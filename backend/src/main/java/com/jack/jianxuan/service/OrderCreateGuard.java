package com.jack.jianxuan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jack.jianxuan.common.BusinessException;
import com.jack.jianxuan.dto.OrderCreateRequest;
import com.jack.jianxuan.dto.OrderVO;
import com.jack.jianxuan.entity.Order;
import com.jack.jianxuan.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 下单幂等守卫。
 *
 * <p>为什么需要它：{@code OrderService.create} 里的防超卖（{@code WHERE stock >= ?}）
 * 只保证「不超卖」，保证不了「一次购买意图只产生一笔订单」。用户在结算页手抖点两次、
 * 或者前端请求超时后自动重试，就会生成两笔订单、扣两次库存——这是真实的生产事故，
 * 而后者（超时重试）恰恰是用户无感知的。
 *
 * <p>为什么用两层而不是一层：
 * <ul>
 *   <li><b>Redis 层</b>挡掉绝大多数重复请求。第二次请求连事务都不会开、一行 SQL 都不执行，
 *       直接返回第一次的订单——这才是幂等的完整语义（相同请求返回相同结果），
 *       而不是「报个错让你知道重复了」。</li>
 *   <li><b>数据库唯一索引</b>（{@code uk_user_request}）是数据层的强保证。
 *       Redis 宕机、key 过期、并发刚好穿过 SETNX 的空隙，这些情况应用层都拦不住，
 *       但只要两笔请求带着同一个 requestId，最多只能落一行。</li>
 * </ul>
 *
 * <p>只有 Redis、没有唯一索引，Redis 一挂就等于没有幂等；只有唯一索引、没有 Redis，
 * 每次重复请求都要白跑一遍扣库存再回滚，而且得靠捕获 DuplicateKeyException
 * 才能给用户返回正确结果。两者叠加才是既快又稳。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateGuard {

    /** 幂等键前缀。带上 userId，避免不同用户的 requestId 互相干扰。 */
    private static final String KEY_PREFIX = "order:idem:";

    /** 占位值：表示「有请求正在处理，结果还没出来」。 */
    private static final String PROCESSING = "PROCESSING";

    /**
     * 占位锁的存活时间。
     *
     * <p>必须比下单事务的最长耗时大，否则第一个请求还没处理完锁就过期了，
     * 第二个请求会当成首次请求放进去，两层防护退化成一层。
     * 又不能太大：如果进程在占位后崩溃，这个 key 会一直拦着用户，
     * 直到 TTL 到期。30 秒是「正常下单 100ms 级」和「崩了不至于让用户干等太久」的折中。
     */
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    /**
     * 结果缓存时间。
     *
     * <p>只需要覆盖「用户可能重复提交的时间窗口」。24 小时足够保险，
     * 也不用担心内存——一个订单一个短字符串，量级可以忽略。
     */
    private static final Duration RESULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final OrderService orderService;
    private final OrderMapper orderMapper;

    /**
     * 下单入口。Controller 调这里，而不是直接调 {@code orderService.create}。
     */
    public OrderVO create(Long userId, OrderCreateRequest request) {
        String requestId = request.getRequestId();
        String key = KEY_PREFIX + userId + ":" + requestId;

        Boolean acquired = tryAcquire(key);

        // acquired == FALSE：key 已存在，这是一次重复请求，直接回放上次的结果。
        if (Boolean.FALSE.equals(acquired)) {
            return replay(userId, key, requestId);
        }
        // acquired == null：Redis 不可用。不阻断下单（缓存和锁都是增强，
        // 不能变成下单的单点故障），降级为只靠数据库唯一索引兜底。

        try {
            // 业务事务在这里面。异常冒出时事务已经回滚干净，下面的 catch 才能安全查库。
            OrderVO vo = orderService.create(userId, request);
            remember(key, vo.getId());
            return vo;
        } catch (DuplicateKeyException e) {
            // 走到这里说明并发刚好穿过了 Redis 那层，被唯一索引挡住。
            // 关键：此时异常已经从 orderService 的事务代理里冒出来，事务已回滚，
            // 这里是一次全新的查询，不会撞上「事务已标记 rollback-only」的坑。
            log.info("并发重复下单被唯一索引拦住，返回已有订单 user={} requestId={}", userId, requestId);
            return loadByRequestId(userId, requestId);
        } catch (RuntimeException e) {
            // 业务失败（库存不足、商品下架……）必须释放幂等键，
            // 否则用户补了库存、改了地址再提交，会被当成重复请求挡回去，且 30 秒内无法恢复。
            release(key);
            throw e;
        }
    }

    /**
     * 抢占幂等键。
     *
     * @return TRUE 抢到（首次请求）；FALSE 已被占（重复请求）；null Redis 不可用（降级）
     */
    private Boolean tryAcquire(String key) {
        try {
            // SET key value NX EX 30 是一条原子命令：判断「不存在」和「写入」之间插不进另一个客户端。
            // 换成「先 get 再 set」就会有两个线程同时读到 null、同时写入、同时认为自己抢到的经典竞态。
            return redisTemplate.opsForValue().setIfAbsent(key, PROCESSING, LOCK_TTL);
        } catch (Exception e) {
            log.warn("抢占幂等键失败，降级为仅靠唯一索引保护 key={}", key, e);
            return null;
        }
    }

    /**
     * 重复请求：把第一次的结果原样返回。
     */
    private OrderVO replay(Long userId, String key, String requestId) {
        String value = readQuietly(key);

        if (value == null || PROCESSING.equals(value)) {
            // 两种情况：第一次还在处理中（正常并发），或 key 刚好在这一瞬间过期（极罕见）。
            // 都返回 409 让客户端稍后重试——不能放进去重跑一遍，那就失去拦截的意义了。
            throw new BusinessException(409, "订单正在处理中，请勿重复提交");
        }

        // 存的是订单 ID。走 detail 而不是手拼 VO，顺带复用了「只能看自己的订单」这段校验。
        return orderService.detail(userId, Long.valueOf(value));
    }

    /**
     * 回填结果。失败不要紧：key 里还是 PROCESSING，30 秒后自动过期，
     * 期间重复请求返回 409；真穿了也有唯一索引兜底。
     */
    private void remember(String key, Long orderId) {
        try {
            redisTemplate.opsForValue().set(key, orderId.toString(), RESULT_TTL);
        } catch (Exception e) {
            log.warn("写入幂等结果失败 key={}", key, e);
        }
    }

    private String readQuietly(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("读取幂等结果失败 key={}", key, e);
            return null;
        }
    }

    private void release(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("释放幂等键失败，将在 {} 秒后自动过期 key={}", LOCK_TTL.toSeconds(), key, e);
        }
    }

    /**
     * 按 (user_id, request_id) 反查订单。唯一索引保证最多一行。
     */
    private OrderVO loadByRequestId(Long userId, String requestId) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getRequestId, requestId));
        if (order == null) {
            // 唯一索引冲突了却查不到：说明对方事务最后回滚了（比如它自己也失败了）。
            // 这种情况让客户端重试一次即可，不猜。
            throw new BusinessException(409, "订单正在处理中，请稍后重试");
        }
        return orderService.detail(userId, order.getId());
    }
}
