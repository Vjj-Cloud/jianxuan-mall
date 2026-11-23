package com.jack.jianxuan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jack.jianxuan.common.BusinessException;
import com.jack.jianxuan.common.CacheHealth;
import com.jack.jianxuan.dto.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 商品详情缓存（Cache-Aside 模式）。
 *
 * <p>缓存的取舍：这里只缓存「商品详情」，不缓存商品列表。理由是列表的 key 由
 * 关键字 / 分类 / 排序 / 页码 / 页大小组合而成，组合数远大于实际访问量，命中率低、
 * key 还容易堆满内存；而详情是「一个 id 一个 key」，访问集中，缓存收益最直接。
 * 缓存要的是「访问模式稳定的热点」，不是「所有查询」。
 *
 * <p>为什么手写而不用 {@code @Cacheable}：注解表达不了 TTL 随机抖动（防雪崩）、
 * 空值占位（防穿透）、互斥锁重建（防击穿）这三件事，硬塞进去要靠自定义 CacheManager +
 * 自定义 KeyGenerator，配置比代码还长。手写虽然多几十行，但每个策略都在明处。
 *
 * <p>三个缓存经典问题在这里的对应实现：
 * <ul>
 *   <li><b>穿透</b>：查不存在的 id 也会打到数据库。这里把「不存在」也缓存成
 *       {@link #NULL_PLACEHOLDER}，TTL 只有 60 秒，成本极低。</li>
 *   <li><b>击穿</b>：某个热点 key 刚好过期，大量并发请求同时回源。这里用 SETNX 抢锁，
 *       只有一个线程查库重建，其余线程短暂等待后重读缓存。</li>
 *   <li><b>雪崩</b>：同一批写入的 key TTL 相同，会在同一刻集体过期。这里给 TTL 加了
 *       0~300 秒的随机偏移，把过期时刻打散。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCache {

    /** 详情缓存：cache:product:detail:{id} */
    private static final String KEY_PREFIX = "cache:product:detail:";

    /** 重建互斥锁：cache:lock:product:{id} */
    private static final String LOCK_PREFIX = "cache:lock:product:";

    /**
     * 「商品不存在」的占位值。
     *
     * <p>为什么不用布隆过滤器：布隆过滤器要维护全量 id 集合，新增商品要同步、删除商品
     * 无法真正移除（只能靠误判率兜着），还要额外引入组件。本项目的 id 空间很小（17 个商品），
     * 一个占位字符串就把穿透挡住了——方案要和数据规模匹配，不是越复杂越好。
     */
    private static final String NULL_PLACEHOLDER = "__NULL__";

    /** 详情基础 TTL：30 分钟。 */
    private static final long DETAIL_TTL_SECONDS = 30 * 60;

    /** 空值 TTL：60 秒。短一点，避免「刚上架却一直 404」。 */
    private static final long NULL_TTL_SECONDS = 60;

    /** 重建锁的 TTL：3 秒。必须远大于一次「查库 + 序列化」的耗时，又不能太长导致锁死。 */
    private static final long LOCK_TTL_SECONDS = 3;

    /** 没抢到锁时等待的毫秒数：足够让持锁线程完成一次重建。 */
    private static final long WAIT_MILLIS = 50;

    /** TTL 随机偏移上限（秒），用于打散过期时刻。 */
    private static final int TTL_JITTER_SECONDS = 300;

    /**
     * 释放锁：先比对 value 再删。
     *
     * <p>不能直接 DEL。假设线程 A 持锁期间卡顿超过了 3 秒，锁已自动过期、线程 B 拿到新锁，
     * 此时 A 恢复执行并 DEL，删掉的就是 B 的锁——B 的重建还没完成，锁被人偷偷拿走了，
     * 互斥就失效了。把 token 比对和删除放进同一个 Lua，判断和删除之间插不进其他命令。
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /** 用 Spring 容器里的 ObjectMapper，而不是 new 一个新的：它已经配好了 JavaTimeModule 等模块。 */
    private final ObjectMapper objectMapper;

    /** 熔断器：Redis 挂掉时跳过缓存，避免每个请求都串行等三次超时。 */
    private final CacheHealth cacheHealth;

    /**
     * 读缓存，未命中时调用 {@code dbLoader} 回源并回填。
     *
     * @param dbLoader 查库并组装 VO；商品不存在时应抛出 code = 404 的 {@link BusinessException}
     */
    public ProductVO get(Long id, Supplier<ProductVO> dbLoader) {
        // 熔断中直接回源。实测「Redis 停机 + 无熔断」时，一次详情请求会因为
        // 读缓存、抢锁、回写三次超时串行等待而慢到 10 秒——接口没坏，但已经不可用了。
        if (cacheHealth.isOpen()) {
            return dbLoader.get();
        }

        String key = KEY_PREFIX + id;

        String raw = getQuietly(key);
        if (NULL_PLACEHOLDER.equals(raw)) {
            // 空值命中：直接返回 404，不碰数据库
            throw new BusinessException(404, "商品不存在或已下架");
        }
        if (raw != null) {
            ProductVO cached = deserialize(raw);
            if (cached != null) {
                return cached;
            }
            // 反序列化失败（比如缓存里是旧版本的字段结构）当作未命中，重建覆盖掉
            log.warn("商品缓存反序列化失败，按未命中处理 id={}", id);
        }

        return rebuild(id, key, dbLoader);
    }

    /**
     * 删除缓存。商品的任何写操作（编辑、上下架、删除、下单扣库存、取消回补库存）之后都要调。
     *
     * <p>为什么是「删缓存」而不是「更新缓存」：
     * 两个并发写操作如果各自去更新缓存，最终留在缓存里的是「谁最后写完」而不是「谁最后改库」，
     * 一旦顺序颠倒，缓存就永久是脏的（之后没有别的动作会纠正它）。删除是幂等的，
     * 而且删完之后下一次读一定回源拿最新值，不存在写反的可能。
     *
     * <p>为什么是「先更库、再删缓存」而不是反过来：如果先删缓存再更库，在「删完」到「更库生效」
     * 这段窗口里进来的读请求会回源读到旧值、并把它写回缓存，之后再没有删除动作，缓存就脏到底了。
     *
     * <p>删除失败怎么办：这里只记日志，靠 TTL（30 分钟）自然过期兜底。彻底的做法是
     * 删缓存失败进重试队列，或者干脆订阅 binlog（Canal）异步删——本项目量级不值得上，
     * 但要知道这是个真实的可靠性缺口。
     */
    public void evict(Long id) {
        evictAll(List.of(id));
    }

    /**
     * 批量删除缓存，一次网络往返。
     *
     * <p><b>为什么必须批量：</b>一笔订单最多 50 个商品，逐个 DEL 就是 50 次往返。
     * Redis 正常时每次约 0.2 ms，看不出问题；但 Redis 网络抖动、每次都要等满 500 ms 超时时，
     * 50 次串行就是 25 秒——而这段代码跑在「订单已提交」之后，客户端早就超时放弃了。
     * 订单成功、用户却看到失败提示，是最糟糕的一类体验。DEL 支持多 key，一次就能删完。
     *
     * <p><b>为什么熔断中直接返回：</b>{@link CacheHealth#isOpen()} 表示「刚刚确认过 Redis 不可用」。
     * 这时候还去删，等于明知道会超时还要再等一次——批量之后最多等一次 500 ms，
     * 但一次也是白等。跳过删除的后果只是缓存脏到 TTL 过期，而 TTL 本来就是兜底手段。
     *
     * <p>等待 Redis 恢复的说法不成立：本方法没有重试入口，删除失败就是失败，
     * 所以「跳过」和「尝试后失败」在结果上完全等价，区别只在响应时间。
     */
    public void evictAll(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        if (cacheHealth.isOpen()) {
            log.debug("缓存熔断中，跳过删除 ids={}", ids);
            return;
        }
        List<String> keys = ids.stream().map(id -> KEY_PREFIX + id).toList();
        try {
            // DEL 支持多 key，Redis 内部逐个删除但只有一次往返。
            redisTemplate.delete(keys);
        } catch (Exception e) {
            cacheHealth.recordFailure();
            log.warn("删除商品缓存失败，等 TTL 自然过期 ids={}", ids, e);
        }
    }

    /* ==================== 内部实现 ==================== */

    private ProductVO rebuild(Long id, String key, Supplier<ProductVO> dbLoader) {
        String lockKey = LOCK_PREFIX + id;
        String token = UUID.randomUUID().toString();

        if (!tryLock(lockKey, token)) {
            // 没抢到锁：说明已经有人在重建。等一下再读缓存，大概率已经有值了。
            sleepQuietly();
            String raw = getQuietly(key);
            if (NULL_PLACEHOLDER.equals(raw)) {
                throw new BusinessException(404, "商品不存在或已下架");
            }
            ProductVO cached = raw == null ? null : deserialize(raw);
            if (cached != null) {
                return cached;
            }
            // 实在等不到就直接查库。宁可多打一次数据库，也不能让请求卡着或失败——
            // 缓存的目的是降低负载，不是把可用性押在它身上。
            return loadAndCache(key, dbLoader);
        }

        try {
            // 双重检查：等待抢锁的这段时间里，可能别人已经重建完并释放了锁
            String raw = getQuietly(key);
            if (raw != null) {
                if (NULL_PLACEHOLDER.equals(raw)) {
                    throw new BusinessException(404, "商品不存在或已下架");
                }
                ProductVO cached = deserialize(raw);
                if (cached != null) {
                    return cached;
                }
            }
            return loadAndCache(key, dbLoader);
        } finally {
            unlock(lockKey, token);
        }
    }

    private ProductVO loadAndCache(String key, Supplier<ProductVO> dbLoader) {
        ProductVO vo;
        try {
            vo = dbLoader.get();
        } catch (BusinessException e) {
            // 「商品不存在」也是一种查询结果，同样缓存起来。否则每次请求这个 id 都要查一遍库，
            // 被人用随机 id 刷接口时数据库就白扛流量了。
            if (e.getCode() == 404) {
                setQuietly(key, NULL_PLACEHOLDER, NULL_TTL_SECONDS);
            }
            throw e;
        }

        String json = serialize(vo);
        if (json != null) {
            // TTL 加随机偏移，避免同一批缓存同时过期把数据库打穿
            long ttl = DETAIL_TTL_SECONDS + ThreadLocalRandom.current().nextInt(TTL_JITTER_SECONDS);
            setQuietly(key, json, ttl);
        }
        return vo;
    }

    private boolean tryLock(String lockKey, String token) {
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, token, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            cacheHealth.recordFailure();
            // Redis 异常时「假装抢到了锁」，退化成所有线程都直查库：
            // 有短暂的并发回源，但不会因为拿不到锁而卡住请求。
            log.warn("获取商品重建锁失败，按持锁处理 lock={}", lockKey, e);
            return true;
        }
    }

    private void unlock(String lockKey, String token) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), token);
        } catch (Exception e) {
            cacheHealth.recordFailure();
            log.warn("释放商品重建锁失败，等 TTL 自动过期 lock={}", lockKey, e);
        }
    }

    /**
     * 读缓存，任何异常都当作「未命中」。
     *
     * <p>Redis 挂掉时降级为直查数据库——缓存是性能组件，不能变成单点故障。
     */
    private String getQuietly(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            cacheHealth.recordFailure();
            log.warn("读商品缓存失败，降级直查数据库 key={}", key, e);
            return null;
        }
    }

    private void setQuietly(String key, String value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            cacheHealth.recordFailure();
            log.warn("写商品缓存失败 key={}", key, e);
        }
    }

    private String serialize(ProductVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (Exception e) {
            log.warn("商品缓存序列化失败 id={}", vo.getId(), e);
            return null;
        }
    }

    private ProductVO deserialize(String json) {
        try {
            return objectMapper.readValue(json, ProductVO.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(WAIT_MILLIS);
        } catch (InterruptedException e) {
            // 恢复中断标志，交给上层决定要不要停
            Thread.currentThread().interrupt();
        }
    }
}
