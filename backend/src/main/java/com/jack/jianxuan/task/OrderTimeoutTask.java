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
import java.util.concurrent.atomic.AtomicBoolean;

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
 *
 * <p><b>这个类里所有 Redis 调用都必须自己兜住异常</b>，理由是 Redis 在架构上是「加速器」，
 * 不是「数据库」：
 * <ul>
 *   <li>{@link #run} 在应用启动阶段执行，异常会一路冒到 Spring，导致整个应用启动失败——
 *       一个缓存/队列组件把主站拖死，是典型的可用性事故。</li>
 *   <li>{@link #scan} 是每秒执行的任务，异常不被吞掉的话，Spring 会每秒打一条完整堆栈，
 *       日志被刷爆，真正的故障信息反而看不见。</li>
 * </ul>
 * 降级后的行为是「这轮不处理」，等 Redis 恢复或下次重启补灌——丢的是时效性，不是订单。
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
    /**
     * 同一个订单最多重试几次，超过就转死信。
     *
     * <p>3 次 ≈ 3 分钟。正常的抖动（数据库连接池瞬时耗尽、行锁等待超时）在这个时间内都能恢复；
     * 连续 3 次失败基本可以断定是数据或代码问题，再重试只是刷日志。
     */
    private static final int MAX_RETRY = 3;

    private final OrderDelayQueue delayQueue;
    private final OrderService orderService;
    private final OrderMapper orderMapper;

    /**
     * Redis 是否处于故障状态。仅用于给日志降噪——故障期间每秒一条 WARN 同样会刷爆日志。
     */
    private final AtomicBoolean redisDown = new AtomicBoolean(false);

    /**
     * 每秒取一批到期任务。
     *
     * <p>用 fixedDelay 而不是 fixedRate：fixedDelay 是「上一次执行结束后再等 1 秒」，
     * 任务慢的时候不会堆积；fixedRate 是「每 1 秒触发一次」，任务超过 1 秒就会并发堆叠。
     */
    @Scheduled(fixedDelay = 1000)
    public void scan() {
        List<Long> orderIds;
        try {
            orderIds = delayQueue.pollDue(BATCH_SIZE);
            if (redisDown.compareAndSet(true, false)) {
                log.info("Redis 已恢复，订单超时扫描继续工作");
            }
        } catch (Exception e) {
            // Redis 不可用：这轮什么都不做。订单还在库里挂着 PENDING，恢复后会被补灌或下一轮取到。
            // 不能抛出去——@Scheduled 方法抛异常只会在日志里留下每秒一条的堆栈，且不会自愈。
            if (redisDown.compareAndSet(false, true)) {
                log.warn("订单超时扫描暂时不可用（Redis 异常），本轮跳过；恢复前订单不会自动取消: {}",
                        e.toString());
            } else {
                log.debug("订单超时扫描仍然不可用: {}", e.toString());
            }
            return;
        }

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
                // 无论「真取消了」还是「状态已变、无需取消」，这条任务都算处理完了，
                // 清掉重试计数和可能残留的死信记录。清理失败不影响业务，最多让这个订单多消耗一次重试配额。
                clearHandledQuietly(orderId);
            } catch (Exception e) {
                requeue(orderId, e);
            }
        }
    }

    /**
     * 处理失败后把任务放回队列。
     *
     * <p>任务在 {@code pollDue} 时已经从 ZSet 里被 ZREM 掉了，不重新入队就等于永久丢失——
     * 这笔订单会一直是 PENDING，永远不会被自动取消（直到下次重启补灌）。
     *
     * <p>重新入队本身也可能失败（Redis 正好在这时挂了）。这时没有更好的办法：
     * 不能重试到成功（会阻塞整批），也不能抛出去（会影响后面的订单）。
     * 靠「启动补灌」兜底——这也是为什么补灌必须存在，它不是可选的优化。
     */
    private void requeue(Long orderId, Exception cause) {
        log.error("自动取消订单 {} 失败", orderId, cause);
        try {
            boolean requeued = delayQueue.pushForRetry(orderId, RETRY_DELAY_MILLIS, MAX_RETRY);
            if (requeued) {
                log.warn("订单 {} 将在 {} 秒后重试", orderId, RETRY_DELAY_MILLIS / 1000);
            } else {
                log.error("订单 {} 已重试 {} 次仍失败，转入死信集合 {}，需要人工处理",
                        orderId, MAX_RETRY, OrderDelayQueue.DEAD_KEY);
            }
        } catch (Exception e) {
            log.error("订单 {} 重新入队失败，只能等下次启动补灌", orderId, e);
        }
    }

    private void clearHandledQuietly(Long orderId) {
        try {
            delayQueue.clearHandled(orderId);
        } catch (Exception e) {
            log.debug("清理订单 {} 的队列痕迹失败", orderId, e);
        }
    }

    /**
     * 启动时把库里所有 PENDING 订单补灌进队列。
     *
     * <p>解决三个问题：一是历史订单从没进过队列；二是 Redis 重启或数据丢失后队列是空的，
     * 光靠队列这些订单永远不会被取消；三是任务取出后重新入队失败时的那次丢失。
     *
     * <p>补灌是幂等的：ZADD 对同一 member 是覆盖语义，重复执行只会刷新到期时间。
     * 已经过了到期时间的订单，delay 会被 {@code push} 收敛成 0，下一轮扫描立刻处理。
     *
     * <p><b>整个方法不允许抛异常。</b>它实现的是 {@link ApplicationRunner}，
     * 抛出即意味着应用启动失败。而「Redis 没起来」是本项目明确支持的降级场景
     * （见 README：Redis 没启动后端也能起来），一个可选组件不能把启动流程绑架。
     * 失败时只记日志，等 Redis 恢复后由运维重启、或后续请求自然触发补灌。
     */
    @Override
    public void run(ApplicationArguments args) {
        List<Order> pendingOrders;
        try {
            pendingOrders = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>()
                            .select(Order::getId, Order::getCreatedAt)
                            .eq(Order::getStatus, PENDING));
        } catch (Exception e) {
            // 数据库都查不了，说明是更基础的问题，其他健康检查会暴露它。
            // 这里同样不能抛：让应用先起来，比因为一个后台任务起不来而全站不可用要好。
            log.error("启动补灌：查询待支付订单失败，本次跳过补灌", e);
            return;
        }

        if (pendingOrders.isEmpty()) {
            log.info("启动补灌：没有待支付订单");
            return;
        }

        long now = System.currentTimeMillis();
        long timeoutMillis = delayQueue.timeoutMillis();
        int pushed = 0;
        for (Order order : pendingOrders) {
            if (order.getCreatedAt() == null) {
                // createdAt 为 null 会 NPE。历史数据或手工插入的行可能出现，
                // 一条脏数据不该让整批补灌中断。
                log.warn("启动补灌：订单 {} 缺少 createdAt，跳过", order.getId());
                continue;
            }
            long expireAt = order.getCreatedAt()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli() + timeoutMillis;
            try {
                delayQueue.push(order.getId(), expireAt - now);
                pushed++;
            } catch (Exception e) {
                // 一条失败没必要继续试：几乎一定是 Redis 整体不可用。
                log.error("启动补灌：Redis 不可用，{} 个待支付订单未能入队。"
                        + "这些订单在 Redis 恢复并由应用重新补灌前不会被自动取消", pendingOrders.size(), e);
                break;
            }
        }

        if (pushed == pendingOrders.size()) {
            log.info("启动补灌：{} 个待支付订单已重新入队（当前队列长度 {}）",
                    pushed, safeQueueSize());
        } else {
            log.warn("启动补灌：仅 {} / {} 个待支付订单入队成功", pushed, pendingOrders.size());
        }
    }

    /** 队列长度只用于打日志，取不到（Redis 挂了）就当 0，绝不能让日志把启动搞崩。 */
    private long safeQueueSize() {
        try {
            return delayQueue.size();
        } catch (Exception e) {
            return 0;
        }
    }
}
