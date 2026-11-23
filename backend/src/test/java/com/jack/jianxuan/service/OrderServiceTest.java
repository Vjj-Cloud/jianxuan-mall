package com.jack.jianxuan.service;

import com.jack.jianxuan.common.BusinessException;
import com.jack.jianxuan.dto.OrderCreateRequest;
import com.jack.jianxuan.dto.OrderItemRequest;
import com.jack.jianxuan.dto.OrderVO;
import com.jack.jianxuan.entity.Product;
import com.jack.jianxuan.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 下单 / 取消订单的集成测试，跑在真实 MySQL 上（不能用 H2：
 * 项目里的 SQL 有 GREATEST 和多表 JOIN UPDATE 这类 MySQL 方言）。
 *
 * <p><b>为什么用 @Transactional：</b>每个测试方法都在一个事务里跑，结束后自动回滚，
 * 不会往库里留脏数据，也不需要写清理逻辑。
 *
 * <p><b>为什么这里不断言「异常之后数据回滚了」：</b>
 * 业务方法上的 @Transactional 会加入当前测试事务（REQUIRED 传播），
 * 抛异常只是把内层标记 rollback-only，数据并没有真的回滚。
 * 所以在测试事务里查库，看到的是「未回滚的中间态」——
 * 那样写出来的断言是假断言。回滚是框架能力，不需要在这里重复验证。
 * 这里验证的是**业务规则有没有被触发**：抛不抛异常、抛什么消息、库存和状态变成什么样。
 *
 * <p><b>并发超卖不在这里测</b>：并发需要真实提交，同一个测试事务里别的线程看不到数据。
 * 那部分由 {@code scripts/benchmark.py --scenario oversell} 覆盖。两者互补，不重复。
 */
@SpringBootTest
@Transactional
class OrderServiceTest {

    /** orders 表对 user 没有外键约束，用一个不会撞上的 id 即可，不必真的建用户 */
    private static final Long TEST_USER_ID = 999_999L;

    private static final int INITIAL_STOCK = 5;

    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long productId;

    @BeforeEach
    void setUp() {
        Product product = new Product();
        product.setCategoryId(1L);
        product.setName("【集成测试】测试商品");
        product.setSubtitle("integration test");
        product.setPrice(new BigDecimal("10.00"));
        // original_price 是 NOT NULL 且没有默认值，insert 时必须给
        product.setOriginalPrice(new BigDecimal("20.00"));
        product.setStock(INITIAL_STOCK);
        product.setSales(0);
        product.setStatus(1);
        product.setCover("/covers/0.svg");
        product.setDescription("集成测试用，测试事务结束后自动回滚");
        product.setDeleted(0);
        productMapper.insert(product);
        productId = product.getId();
    }

    @Test
    @DisplayName("库存充足：下单成功，库存扣减、销量增加、状态为 PENDING")
    void createOrder_deductsStockAndSales() {
        OrderVO order = orderService.create(TEST_USER_ID, requestOf(productId, 2));

        assertNotNull(order.getId());
        assertNotNull(order.getOrderNo());
        assertEquals("PENDING", order.getStatus());
        assertEquals(0, new BigDecimal("20.00").compareTo(order.getTotalAmount()),
                "金额应由服务端按商品单价重算，等于 10.00 × 2");

        Product after = productMapper.selectById(productId);
        assertEquals(INITIAL_STOCK - 2, after.getStock().intValue());
        assertEquals(2, after.getSales().intValue());
    }

    @Test
    @DisplayName("库存不足：下单被拒绝，消息里点名是哪件商品")
    void createOrder_insufficientStock() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.create(TEST_USER_ID, requestOf(productId, INITIAL_STOCK + 1)));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("库存不足"), "实际消息: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("【集成测试】"),
                "库存不足时要告诉用户是哪件商品，否则多商品订单里根本不知道该删哪个");

        // 被拒绝的订单不应该扣库存
        assertEquals(INITIAL_STOCK, productMapper.selectById(productId).getStock().intValue());
    }

    @Test
    @DisplayName("取消订单：状态变 CANCELLED，库存和销量都回到原值")
    void cancelOrder_restoresStock() {
        OrderVO created = orderService.create(TEST_USER_ID, requestOf(productId, 2));
        assertEquals(INITIAL_STOCK - 2, productMapper.selectById(productId).getStock().intValue());

        OrderVO cancelled = orderService.cancel(TEST_USER_ID, created.getId());

        assertEquals("CANCELLED", cancelled.getStatus());
        Product after = productMapper.selectById(productId);
        assertEquals(INITIAL_STOCK, after.getStock().intValue(), "库存要完整回补");
        assertEquals(0, after.getSales().intValue(), "销量也要回退，否则销量排序会虚高");
    }

    @Test
    @DisplayName("重复取消：第二次被拒绝，库存不会被回补两次")
    void cancelOrder_twiceRejected() {
        OrderVO created = orderService.create(TEST_USER_ID, requestOf(productId, 1));
        orderService.cancel(TEST_USER_ID, created.getId());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.cancel(TEST_USER_ID, created.getId()));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("不可取消"), "实际消息: " + ex.getMessage());
        assertEquals(INITIAL_STOCK, productMapper.selectById(productId).getStock().intValue(),
                "重复取消如果没被挡住，库存会被多加一份");
    }

    @Test
    @DisplayName("商品被软删后取消订单：库存依然回补（补偿不依赖商品的当前状态）")
    void cancelOrder_restoresStockEvenIfProductSoftDeleted() {
        OrderVO created = orderService.create(TEST_USER_ID, requestOf(productId, 2));

        // 逻辑删除：UPDATE product SET deleted = 1
        productMapper.deleteById(productId);
        assertNull(productMapper.selectById(productId), "软删后带 @TableLogic 的查询应该查不到");

        // restoreStock 的 WHERE 故意不带 deleted 条件，所以这里依然能回补
        orderService.cancel(TEST_USER_ID, created.getId());

        // selectById 查不到（自动追加 deleted = 0），只能用裸 SQL 看真实库存
        Integer stock = jdbcTemplate.queryForObject(
                "SELECT stock FROM product WHERE id = ?", Integer.class, productId);
        assertEquals(INITIAL_STOCK, stock,
                "如果 restoreStock 加了 deleted = 0，这里会得到 3 —— 库存永久少 2 且不报错");
    }

    @Test
    @DisplayName("单价上限 × 数量上限：总额能落库（orders.total_amount 已扩到 DECIMAL(14,2)）")
    void createOrder_maxPriceStillFitsTotalAmountColumn() {
        // 常规商品库存只有 5，这里单独造一个「贵且多」的商品
        Product expensive = new Product();
        expensive.setCategoryId(1L);
        expensive.setName("【集成测试】满额商品");
        expensive.setSubtitle("max amount test");
        expensive.setPrice(new BigDecimal("999999.99"));
        expensive.setOriginalPrice(new BigDecimal("999999.99"));
        expensive.setStock(1000);
        expensive.setSales(0);
        expensive.setStatus(1);
        expensive.setCover("/covers/0.svg");
        expensive.setDescription("验证订单总额列容量");
        expensive.setDeleted(0);
        productMapper.insert(expensive);

        OrderVO order = orderService.create(TEST_USER_ID, requestOf(expensive.getId(), 999));

        // 999999.99 × 999 = 998,999,990.01：9 位整数，
        // 旧的 DECIMAL(10,2)（上限 99,999,999.99）会在这里抛 Out of range value。
        BigDecimal expected = new BigDecimal("998999990.01");
        assertEquals(0, expected.compareTo(order.getTotalAmount()),
                "金额由服务端重算，实际: " + order.getTotalAmount());

        BigDecimal stored = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM orders WHERE id = ?", BigDecimal.class, order.getId());
        assertEquals(0, expected.compareTo(stored),
                "总额必须原样落库。这里失败通常意味着没跑 sql/08_money_precision.sql");
    }

    private OrderCreateRequest requestOf(Long productId, int quantity) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);

        OrderCreateRequest request = new OrderCreateRequest();
        // 幂等键必须给：uk_user_request 是 (user_id, request_id) 唯一索引，
        // 不设的话插进去的是 NULL，唯一索引对 NULL 不生效（NULL != NULL）。
        // 那样测试虽然能过，但等于把「真实请求长什么样」这件事测掉了。
        request.setRequestId("svc-" + System.nanoTime());
        request.setItems(List.of(item));
        request.setReceiverName("集成测试");
        request.setReceiverPhone("13800138000");
        request.setReceiverAddress("集成测试地址");
        return request;
    }
}
