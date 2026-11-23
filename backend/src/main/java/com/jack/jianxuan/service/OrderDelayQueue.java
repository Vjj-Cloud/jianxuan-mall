package com.jack.jianxuan.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 订单延迟队列（Redis ZSet 实现）。
 *
 * <p>为什么是 ZSet：score 存「到期时间戳」，取任务就是一次 {@code ZRANGEBYSCORE 0 now}，
 * 天然按时间排序、天然支持批量取。用 List 只能先进先出，没法表达「延迟到某一刻」；
 * 用 Redis 键过期通知则不可靠——过期通知是「最多一次」投递，订阅端断线就永久丢。
 *
 * <p>这里用的是自动配置的 {@link StringRedisTemplate}，没有自定义序列化器：
 * key 和 member 全是字符串，好处是能直接在 redis-cli 里 {@code ZRANGE order:delay 0 -1 WITHSCORES}
 * 用肉眼检查队列内容，排障成本低很多。
 */
@Component
@RequiredArgsConstructor
public class OrderDelayQueue {

    /** 延迟队列的 key。member = orderId，score = 到期时间戳（毫秒）。 */
    public static final String KEY = "order:delay";

    /**
     * 原子地「取出并删除」已到期的任务。
     *
     * <p>查询和删除必须在同一个脚本里。如果分成两次调用（先 ZRANGEBYSCORE 再 ZREM），
     * 两个调度线程会读到同一批 orderId，同一个订单被取消两次、库存回补两遍。
     * 下游的条件 UPDATE 虽然能兜住（第二次影响 0 行），但那是「靠下游擦屁股」，不是设计正确。
     *
     * <p>Redis 执行 Lua 期间是单线程原子的，不同客户端的命令插不进来——这是不重复消费的全部秘密。
     *
     * <p>最小值用 {@code -inf} 而不是 {@code 0}：补灌历史订单时，早就过了到期时间的订单
     * score 会是负数，用 0 做下界会永远取不到它们。
     */
    private static final DefaultRedisScript<List> POLL_DUE_SCRIPT = new DefaultRedisScript<>("""
            local jobs = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
            if #jobs == 0 then
              return {}
            end
            redis.call('ZREM', KEYS[1], unpack(jobs))
            return jobs
            """, List.class);

    private final StringRedisTemplate redisTemplate;

    /** 未支付订单的超时时间（分钟）。用 double 是为了测试时能配成 0.2（12 秒）。 */
    @Value("${order.timeout-minutes:30}")
    private double timeoutMinutes;

    public long timeoutMillis() {
        return (long) (timeoutMinutes * 60 * 1000);
    }

    /** 按配置的超时时间投递（下单时调用）。 */
    public void pushAfterTimeout(Long orderId) {
        push(orderId, timeoutMillis());
    }

    /**
     * 投递一个延迟任务，{@code delayMillis} 毫秒后到期。
     *
     * <p>ZADD 对同一个 member 是覆盖语义，所以重复投递同一个 orderId 不会产生重复任务，
     * 只是把到期时间刷新——补灌历史订单时可以放心重跑。
     */
    public void push(Long orderId, long delayMillis) {
        long score = System.currentTimeMillis() + Math.max(0, delayMillis);
        redisTemplate.opsForZSet().add(KEY, String.valueOf(orderId), score);
    }

    /** 取出一批已到期的订单 id，取完即从队列移除。没有到期任务时返回空集合，不返回 null。 */
    public List<Long> pollDue(int batchSize) {
        @SuppressWarnings("unchecked")
        List<String> ids = redisTemplate.execute(
                POLL_DUE_SCRIPT,
                Collections.singletonList(KEY),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(batchSize));
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream().map(Long::valueOf).toList();
    }

    /** 队列里还剩多少待执行任务，仅用于排查和验证。 */
    public long size() {
        Long size = redisTemplate.opsForZSet().zCard(KEY);
        return size == null ? 0 : size;
    }
}
