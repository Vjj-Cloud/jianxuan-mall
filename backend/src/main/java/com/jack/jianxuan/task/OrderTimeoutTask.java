package com.jack.jianxuan.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jack.jianxuan.entity.Order;
import com.jack.jianxuan.mapper.OrderMapper;
import com.jack.jianxuan.service.OrderDelayQueue;
import com.jack.jianxuan.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;

/**
 * 订单超时扫描任务：把延迟队列里到期的订单取消掉。
 *
 * <p>为什么是「Redis 队列 + 本任务」而不是「定时扫订单表」：
 * 扫表要全表扫 PENDING 订单，订单量一大就扫不动，而且精度受扫描间隔限制。
 * 队列只处理「刚好到期的那几个」，代价是 O(log N + M)。
 *
 * <p>但队列不能作为唯一依据——Redis 挂了、数据丢了，任务就永远不执行。
 * 所以启动时会把库里所有 PENDING 订单重新灌一遍队列（见 {@link #run}），
 * 相当于用数据库兜底，队列只是把「多久之后被发现」从小时级缩到秒级。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask implements ApplicationRunner {

    private static final String PENDING = "PENDING";
    /** 单批取多少条。太大单次事务时间长，太小空转频繁。 */
    private static final int BATCH_SIZE = 100;
    /** 处理失败后隔多久重试。 */
    private static final long RETRY_DELAY_MILLIS = 60_000L;

    private final OrderDelayQueue delayQueue;
    private final OrderService orderService;
    private final OrderMapper orderMapper;

    /**
     * 每秒取一批到期任务。
     *
     * <p>用 fixedDelay 而不是 fixedRate：fixedDelay 是「上一次执行结束后再等 1 秒」，
     * 任务慢的时候不会堆积；fixedRate 是「每 1 秒触发一次」，任务超过 1 秒就会并发堆叠。
     */
    @Scheduled(fixedDelay = 1000)
    public void scan() {
        List<Long> orderIds = delayQueue.pollDue(BATCH_SIZE);
        if (orderIds.isEmpty()) {
            return;
        }
        log.info("订单超时扫描：取到 {} 个到期任务", orderIds.size());

        for (Long orderId : orderIds) {
            // 单个订单失败不能影响整批。这里吞异常不是偷懒——
            // 循环里任何一个异常抛出去，后面 99 个订单这次就不处理了。
            try {
                boolean cancelled = orderService.cancelBySystem(orderId);
                if (cancelled) {
                    log.info("订单 {} 超时未支付，已自动取消", orderId);
                }
            } catch (Exception e) {
                log.error("自动取消订单 {} 失败，{} 秒后重试", orderId, RETRY_DELAY_MILLIS / 1000, e);
                // 任务已经从队列里删掉了，失败要重新塞回去，否则这笔订单永远不会被取消。
                // 注意：这里没做重试次数上限，异常数据会一直重试——
                // 生产上应该再加一个 order:delay:retry 的 Hash 记次数，超过 N 次转死信集合。
                delayQueue.push(orderId, RETRY_DELAY_MILLIS);
            }
        }
    }

    /**
     * 启动时把库里所有 PENDING 订单补灌进队列。
     *
     * <p>解决两个问题：一是历史订单从没进过队列；二是 Redis 重启或数据丢失后队列是空的，
     * 光靠队列这些订单永远不会被取消。
     *
     * <p>补灌是幂等的：ZADD 对同一 member 是覆盖语义，重复执行只会刷新到期时间。
     * 已经过了到期时间的订单，delay 会被 {@code push} 收敛成 0，下一轮扫描立刻处理。
     */
    @Override
    public void run(ApplicationArguments args) {
        List<Order> pendingOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .select(Order::getId, Order::getCreatedAt)
                        .eq(Order::getStatus, PENDING));
        if (pendingOrders.isEmpty()) {
            log.info("启动补灌：没有待支付订单");
            return;
        }

        long now = System.currentTimeMillis();
        for (Order order : pendingOrders) {
            long expireAt = order.getCreatedAt()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli() + delayQueue.timeoutMillis();
            delayQueue.push(order.getId(), expireAt - now);
        }
        log.info("启动补灌：{} 个待支付订单已重新入队（当前队列长度 {}）",
                pendingOrders.size(), delayQueue.size());
    }
}
