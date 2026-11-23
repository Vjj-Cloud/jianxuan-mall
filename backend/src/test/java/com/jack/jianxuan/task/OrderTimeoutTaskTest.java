package com.jack.jianxuan.task;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jack.jianxuan.entity.Order;
import com.jack.jianxuan.mapper.OrderMapper;
import com.jack.jianxuan.service.OrderDelayQueue;
import com.jack.jianxuan.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单超时任务的容错测试。
 *
 * <p>这个类全部是纯单元测试（Mockito），不连 MySQL / Redis——
 * 它要验证的恰恰是「Redis 挂了会怎样」，连真的 Redis 反而没法构造这个场景。
 *
 * <p>三条底线，任何一条破了都会变成可用性事故：
 * <ol>
 *   <li>Redis 挂掉时，{@link OrderTimeoutTask#run} 不能让应用启动失败；</li>
 *   <li>Redis 挂掉时，每秒执行的 {@link OrderTimeoutTask#scan} 不能抛异常刷日志；</li>
 *   <li>取消订单失败时，任务必须重新入队（否则订单永远等不到自动取消）。</li>
 * </ol>
 */
class OrderTimeoutTaskTest {

    private OrderDelayQueue delayQueue;
    private OrderService orderService;
    private OrderMapper orderMapper;
    private OrderTimeoutTask task;

    @BeforeEach
    void setUp() {
        delayQueue = mock(OrderDelayQueue.class);
        orderService = mock(OrderService.class);
        orderMapper = mock(OrderMapper.class);
        task = new OrderTimeoutTask(delayQueue, orderService, orderMapper);
    }

    @Test
    @DisplayName("Redis 不可用时 scan 不抛异常，也不会去取消订单")
    void scan_whenRedisDown_doesNotThrow() {
        when(delayQueue.pollDue(anyInt())).thenThrow(new RuntimeException("Redis 连接失败"));

        // 连跑两轮：第二轮走的是「已经处于故障状态」的分支（日志降噪路径）
        assertDoesNotThrow(task::scan);
        assertDoesNotThrow(task::scan);

        verify(orderService, never()).cancelBySystem(anyLong());
    }

    @Test
    @DisplayName("取消失败时任务重新入队，不会被静默丢弃")
    void scan_whenCancelFails_requeues() {
        when(delayQueue.pollDue(anyInt())).thenReturn(List.of(1L));
        when(orderService.cancelBySystem(1L)).thenThrow(new RuntimeException("数据库连接池耗尽"));
        when(delayQueue.pushForRetry(eq(1L), anyLong(), anyInt())).thenReturn(true);

        assertDoesNotThrow(task::scan);

        verify(delayQueue).pushForRetry(eq(1L), anyLong(), eq(3));
        verify(delayQueue, never()).push(anyLong(), anyLong());
    }

    @Test
    @DisplayName("重试次数耗尽转死信，调度任务本身不报错")
    void scan_whenRetryExhausted_doesNotThrow() {
        when(delayQueue.pollDue(anyInt())).thenReturn(List.of(2L));
        when(orderService.cancelBySystem(2L)).thenThrow(new RuntimeException("脏数据"));
        when(delayQueue.pushForRetry(eq(2L), anyLong(), anyInt())).thenReturn(false);

        assertDoesNotThrow(task::scan);
    }

    @Test
    @DisplayName("重新入队时 Redis 也挂了，只记日志，不影响同批其他订单")
    void scan_whenRequeueAlsoFails_continuesOtherOrders() {
        when(delayQueue.pollDue(anyInt())).thenReturn(List.of(3L, 4L));
        when(orderService.cancelBySystem(3L)).thenThrow(new RuntimeException("boom"));
        when(orderService.cancelBySystem(4L)).thenReturn(true);
        when(delayQueue.pushForRetry(eq(3L), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("Redis 也挂了"));

        assertDoesNotThrow(task::scan);

        // 3 号失败不该拖累 4 号
        verify(orderService).cancelBySystem(4L);
        // 处理成功要把「重试计数 + 死信记录」一起清掉，否则死信集合会积压已解决的订单
        verify(delayQueue).clearHandled(4L);
    }

    @Test
    @DisplayName("启动补灌时 Redis 不可用：应用照常启动（不能让一个后台任务拖死全站）")
    void run_whenRedisDown_doesNotFailStartup() {
        Order order = new Order();
        order.setId(10L);
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(order));
        when(delayQueue.timeoutMillis()).thenReturn(30 * 60 * 1000L);
        doThrow(new RuntimeException("Redis 拒绝连接"))
                .when(delayQueue).push(anyLong(), anyLong());

        assertDoesNotThrow(() -> task.run(null));
    }

    @Test
    @DisplayName("启动补灌时脏数据（createdAt 为空）跳过，不中断整批")
    void run_skipsOrderWithoutCreatedAt() {
        Order dirty = new Order();
        dirty.setId(11L);
        dirty.setStatus("PENDING");
        dirty.setCreatedAt(null);

        Order normal = new Order();
        normal.setId(12L);
        normal.setStatus("PENDING");
        normal.setCreatedAt(LocalDateTime.now().minusMinutes(1));

        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dirty, normal));
        when(delayQueue.timeoutMillis()).thenReturn(30 * 60 * 1000L);

        assertDoesNotThrow(() -> task.run(null));

        // 脏数据不入队，正常订单照常入队
        verify(delayQueue, never()).push(eq(11L), anyLong());
        verify(delayQueue).push(eq(12L), anyLong());
    }

    @Test
    @DisplayName("启动补灌时数据库不可用：同样不阻断启动")
    void run_whenDatabaseDown_doesNotFailStartup() {
        when(orderMapper.selectList(any(Wrapper.class)))
                .thenThrow(new RuntimeException("Communications link failure"));

        assertDoesNotThrow(() -> task.run(null));
    }
}
