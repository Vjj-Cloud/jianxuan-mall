package com.jack.jianxuan.service;

import com.jack.jianxuan.common.BusinessException;
import com.jack.jianxuan.dto.OrderCreateRequest;
import com.jack.jianxuan.dto.OrderItemRequest;
import com.jack.jianxuan.dto.OrderVO;
import com.jack.jianxuan.entity.Product;
import com.jack.jianxuan.mapper.ProductMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 幂等下单的集成测试。
 *
 * <p><b>为什么必须有这个测试类：</b>{@link OrderServiceTest} 直接调
 * {@code orderService.create}，绕过了 {@link OrderCreateGuard}——而幂等这件事完全发生在
 * Guard 里。所以哪怕那边 6 个用例全绿，也只能证明「库存扣减正确」，证明不了
 * 「同一个 requestId 只会产生一笔订单」。测试覆盖错了层，比没测试更危险，
 * 因为它会让人误以为这条防线已经被验证过。
 *
 * <p><b>为什么这里不加 @Transactional：</b>幂等的核心是并发，而并发的前提是「一个线程
 * 能看到另一个线程提交的数据」。测试事务里的数据对其他线程不可见，所有线程都会
 * 走一遍「查不到 → 插入」，测出来的结论和线上完全相反。
 * 所以这里用真实提交 + {@link #cleanUp()} 手工清理。
 *
 * <p>两个用例分别覆盖两层防护：
 * <ul>
 *   <li>{@code sequentialRepeat}：Redis 那层（第二个请求连事务都不开，直接回放）；</li>
 *   <li>{@code concurrentRepeat}：并发穿透 Redis 时，数据库唯一索引兜底。</li>
 * </ul>
 * 其中并发用例在 Redis 停机时也能通过——那时所有请求都走唯一索引，结论一致。
 * 这正是「两层防护」的好处：去掉任何一层，另一个用例仍能说明问题。
 */
@SpringBootTest
class OrderIdempotencyTest {

    private static final Long TEST_USER_ID = 999_998L;
    private static final int INITIAL_STOCK = 100;

    @Autowired
    private OrderCreateGuard orderCreateGuard;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long productId;
    private String requestId;

    @BeforeEach
    void setUp() {
        Product product = new Product();
        product.setCategoryId(1L);
        product.setName("【幂等测试】测试商品");
        product.setSubtitle("idempotency test");
        product.setPrice(new BigDecimal("9.90"));
        product.setOriginalPrice(new BigDecimal("19.90"));
        product.setStock(INITIAL_STOCK);
        product.setSales(0);
        product.setStatus(1);
        product.setCover("/covers/0.svg");
        product.setDescription("幂等测试用，用例结束后清理");
        product.setDeleted(0);
        productMapper.insert(product);
        productId = product.getId();

        // requestId 每个用例都不同，避免用例之间通过 Redis key 互相影响
        requestId = "idem-" + System.nanoTime();
    }

    @AfterEach
    void cleanUp() {
        // 先删明细再删订单（没有外键，但顺序仍然保持合理）
        jdbcTemplate.update(
                "DELETE oi FROM order_item oi JOIN orders o ON oi.order_id = o.id WHERE o.user_id = ?",
                TEST_USER_ID);
        jdbcTemplate.update("DELETE FROM orders WHERE user_id = ?", TEST_USER_ID);
        // 物理删除测试商品：productMapper.deleteById 是逻辑删除，留着会让表越跑越大
        jdbcTemplate.update("DELETE FROM product WHERE id = ?", productId);
        // 幂等键：不删也只是 24 小时后自动过期，但会让下一次跑同一个 requestId 时走回放路径
        try {
            redisTemplate.delete("order:idem:" + TEST_USER_ID + ":" + requestId);
        } catch (Exception ignored) {
            // Redis 不可用时没有 key 可删，忽略
        }
    }

    @Test
    @DisplayName("同一 requestId 顺序提交两次：返回同一笔订单，库存只扣一次")
    void sequentialRepeat_returnsSameOrder() {
        OrderVO first = orderCreateGuard.create(TEST_USER_ID, requestOf(2));
        OrderVO second = orderCreateGuard.create(TEST_USER_ID, requestOf(2));

        assertEquals(first.getId(), second.getId(),
                "第二次提交必须回放第一次的订单，而不是新建一笔");
        assertEquals(1, countOrders(), "库里只能有一笔订单");
        assertEquals(INITIAL_STOCK - 2, stock(), "库存只能扣一次");
    }

    @Test
    @DisplayName("10 个线程带同一 requestId 并发提交：只落一笔订单，库存只扣一次")
    void concurrentRepeat_createsOnlyOneOrder() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);
        List<Long> returnedOrderIds = new ArrayList<>();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    // 同时放行，最大化「都读到 key 不存在」的窗口
                    startGate.await();
                    OrderVO vo = orderCreateGuard.create(TEST_USER_ID, requestOf(1));
                    synchronized (returnedOrderIds) {
                        returnedOrderIds.add(vo.getId());
                    }
                } catch (BusinessException e) {
                    // 409「订单正在处理中」是预期分支：第一个请求还没提交完，
                    // 后来者被 PROCESSING 占位挡住。这不叫失败，叫「没让你重复下单」。
                    assertEquals(409, e.getCode(), "非 409 的业务异常说明拦截逻辑写错了: " + e.getMessage());
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneGate.await(30, TimeUnit.SECONDS), "并发用例超时，可能有线程卡死");

        pool.shutdownNow();

        assertEquals(1, countOrders(),
                "同一个 requestId 只能落一笔订单，实际落了 " + countOrders() + " 笔");
        assertEquals(INITIAL_STOCK - 1, stock(), "10 个线程也只该扣 1 件库存");
        assertTrue(returnedOrderIds.stream().distinct().count() <= 1,
                "所有成功返回的订单号必须是同一个，实际: " + returnedOrderIds);
        // 每个线程的结局只有两种：拿到回放结果，或被 409 挡回。
        // 加起来必须等于线程数——少了就说明有线程抛了预期之外的异常（被吞在上面了）。
        assertEquals(threads, returnedOrderIds.size() + rejected.get(),
                "有线程既没拿到订单也没被 409 挡回，说明存在预期外的失败路径");
    }

    @Test
    @DisplayName("Redis 那层失效时，数据库唯一索引兜底：第二笔插入失败且库存不多扣")
    void uniqueIndex_blocksDuplicateInsertAndRollsBackStock() {
        // 第一单正常走 Guard，落库并扣库存
        orderCreateGuard.create(TEST_USER_ID, requestOf(1));
        assertEquals(INITIAL_STOCK - 1, stock());

        // 直接调 Service = 模拟「Guard 没拦住」（Redis 宕机时的真实路径：
        // tryAcquire 返回 null → 降级放行 → 两个请求都进事务）
        assertThrows(DuplicateKeyException.class,
                () -> orderService.create(TEST_USER_ID, requestOf(1)),
                "同一个 (user_id, request_id) 必须撞唯一索引 uk_user_request，"
                        + "否则 Redis 一挂就等于没有幂等");

        assertEquals(1, countOrders(), "撞索引的插入不该留下第二笔订单");
        assertEquals(INITIAL_STOCK - 1, stock(),
                "撞唯一索引后整个事务回滚，刚才扣掉的库存必须还原");
    }

    private int countOrders() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE user_id = ?", Integer.class, TEST_USER_ID);
        return count == null ? 0 : count;
    }

    private int stock() {
        Integer stock = jdbcTemplate.queryForObject(
                "SELECT stock FROM product WHERE id = ?", Integer.class, productId);
        return stock == null ? -1 : stock;
    }

    private OrderCreateRequest requestOf(int quantity) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);

        OrderCreateRequest request = new OrderCreateRequest();
        request.setRequestId(requestId);
        request.setItems(List.of(item));
        request.setReceiverName("幂等测试");
        request.setReceiverPhone("13800138000");
        request.setReceiverAddress("幂等测试地址");
        return request;
    }
}
