package com.jack.jianxuan.support;

import com.jack.jianxuan.service.OrderDelayQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collection;

/**
 * 测试收尾用的 Redis 清理工具。
 *
 * <p><b>为什么需要它：</b>不加 {@code @Transactional} 的集成测试会真的提交订单，
 * 提交之后 {@code afterCommit} 就把「超时自动取消」任务投进了 Redis 的 ZSet。
 * 测试随后删掉数据库里的订单，但 Redis 里的任务还在——它们永远不会到期执行到有用的东西，
 * 却会一直占着 {@code order:delay}。跑十几次之后 {@code ZCARD} 就有几十条指向不存在订单的脏任务，
 * 排查真实队列问题时噪音很大。
 *
 * <p>这不是业务 bug：真实场景里订单不会凭空消失，队列里有历史任务很正常。
 * 问题出在测试「只清理了自己造的数据的一半」——清了库、没清队列。
 *
 * <p>所以清理必须覆盖任务可能落在的三个地方：
 * <ul>
 *   <li>{@code order:delay}：待执行队列；</li>
 *   <li>{@code order:delay:retry}：重试计数（清理失败时会被 HINCRBY 写进去）；</li>
 *   <li>{@code order:delay:dead}：死信集合（重试超限后会转进来）。</li>
 * </ul>
 * 只清第一个的话，剩下两个迟早也会变成垃圾。
 */
@Slf4j
public final class RedisTestCleanup {

    private RedisTestCleanup() {
    }

    /**
     * 把已知订单 id 的超时任务、重试计数、死信记录一并删掉。
     *
     * <p>任何异常都吞掉：Redis 不可用时这三个 key 本来就不会有内容，
     * 而清理失败不该让一个已经通过的业务用例变成失败。
     */
    public static void removeOrderDelayTasks(StringRedisTemplate redis, Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        String[] members = orderIds.stream().map(String::valueOf).toArray(String[]::new);
        try {
            redis.opsForZSet().remove(OrderDelayQueue.KEY, (Object[]) members);
            redis.opsForZSet().remove(OrderDelayQueue.DEAD_KEY, (Object[]) members);
            redis.opsForHash().delete(OrderDelayQueue.RETRY_KEY, (Object[]) members);
        } catch (Exception e) {
            log.warn("清理测试订单的延迟任务失败，会在 Redis 里留下脏任务 ids={}", orderIds, e);
        }
    }
}
