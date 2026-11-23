package com.jack.jianxuan.service;

import com.jack.jianxuan.dto.OrderCreateRequest;
import com.jack.jianxuan.dto.OrderItemRequest;
import com.jack.jianxuan.dto.OrderVO;
import com.jack.jianxuan.dto.ProductVO;
import com.jack.jianxuan.entity.Product;
import com.jack.jianxuan.mapper.ProductMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 商品详情缓存与订单写操作的最终一致性测试。
 *
 * <p>验证的是 M8 里那条最容易被忽略的规则：<b>库存变了，缓存必须立刻失效</b>。
 * 断言不看「key 是否被删」（那是实现细节，改天换成延迟双删、删两次，断言就废了），
 * 而是看「用户查详情时看到的库存对不对」——这才是真正的契约。
 *
 * <p>不加 {@code @Transactional}：缓存失效被安排在 {@code afterCommit}，
 * 而测试事务永远不提交，afterCommit 也就永远不执行，断言必然假绿（看起来通过、
 * 其实什么也没验证）。这是「用 @Transactional 图省事」最典型的翻车方式。
 *
 * <p>Redis 不可用时用 {@link Assumptions} 跳过，而不是失败：
 * 这个用例验证的是缓存层的正确性，Redis 没起来属于环境问题，不该把一个
 * 与本用例无关的环境缺失报成代码缺陷。
 */
@SpringBootTest
class ProductCacheConsistencyTest {

    private static final Long TEST_USER_ID = 999_997L;
    private static final int INITIAL_STOCK = 5;

    @Autowired
    private ProductService productService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long productId;
    private String requestId;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(redisAvailable(), "Redis 不可用，跳过缓存一致性用例");

        Product product = new Product();
        product.setCategoryId(1L);
        product.setName("【缓存测试】测试商品");
        product.setSubtitle("cache test");
        product.setPrice(new BigDecimal("5.00"));
        product.setOriginalPrice(new BigDecimal("10.00"));
        product.setStock(INITIAL_STOCK);
        product.setSales(0);
        product.setStatus(1);
        product.setCover("/covers/0.svg");
        product.setDescription("缓存一致性测试用，用例结束后清理");
        product.setDeleted(0);
        productMapper.insert(product);
        productId = product.getId();

        requestId = "cache-" + System.nanoTime();
    }

    @AfterEach
    void cleanUp() {
        if (productId == null) {
            return;
        }
        jdbcTemplate.update(
                "DELETE oi FROM order_item oi JOIN orders o ON oi.order_id = o.id WHERE o.user_id = ?",
                TEST_USER_ID);
        jdbcTemplate.update("DELETE FROM orders WHERE user_id = ?", TEST_USER_ID);
        jdbcTemplate.update("DELETE FROM product WHERE id = ?", productId);
        try {
            redisTemplate.delete("cache:product:detail:" + productId);
        } catch (Exception ignored) {
            // Redis 中途挂了也不影响清理结果
        }
    }

    @Test
    @DisplayName("下单扣库存后，详情接口立刻返回新库存（缓存已失效）")
    void createOrder_invalidatesDetailCache() {
        // 第一次查询把「库存 5」写进缓存
        assertEquals(INITIAL_STOCK, detailStock(), "初始库存应为 5");

        orderService.create(TEST_USER_ID, requestOf(2));

        // 如果下单后没删缓存，这里会读到缓存里的 5，而不是数据库里的 3
        assertEquals(INITIAL_STOCK - 2, detailStock(),
                "下单后详情必须反映扣减后的库存，否则用户会看到有货、下单却提示库存不足");
    }

    @Test
    @DisplayName("取消订单回补库存后，详情接口立刻返回回补后的库存（缓存第二次失效）")
    void cancelOrder_invalidatesDetailCacheAgain() {
        OrderVO order = orderService.create(TEST_USER_ID, requestOf(2));
        assertEquals(INITIAL_STOCK - 2, detailStock(), "下单后缓存应已失效，读到库存 3");

        orderService.cancel(TEST_USER_ID, order.getId());

        assertEquals(INITIAL_STOCK, detailStock(),
                "取消后库存回补到 5，缓存必须同步失效；"
                        + "这条路径最容易漏，因为在提交前删缓存时它「看起来」是对的");
    }

    private int detailStock() {
        ProductVO vo = productService.detail(productId);
        return vo.getStock();
    }

    private boolean redisAvailable() {
        try {
            redisTemplate.hasKey("cache:product:detail:__probe__");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private OrderCreateRequest requestOf(int quantity) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);

        OrderCreateRequest request = new OrderCreateRequest();
        request.setRequestId(requestId);
        request.setItems(List.of(item));
        request.setReceiverName("缓存测试");
        request.setReceiverPhone("13800138000");
        request.setReceiverAddress("缓存测试地址");
        return request;
    }
}
