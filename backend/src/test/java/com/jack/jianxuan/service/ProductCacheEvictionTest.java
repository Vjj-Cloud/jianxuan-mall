package com.jack.jianxuan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jack.jianxuan.common.CacheHealth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 缓存删除路径的单元测试（Mockito，不连 Redis）。
 *
 * <p>要验证的是「删缓存这件事不能反过来拖垮下单」的三个具体约束：
 * <ol>
 *   <li>一笔订单的多个商品只发一次 DEL——逐个删在 Redis 超时时会串行等 N 次；</li>
 *   <li>熔断期间根本不发命令——已知 Redis 挂了还去等超时，等于明知故犯；</li>
 *   <li>删除失败必须吞异常并上报熔断，绝不能让「订单已提交、接口却 500」发生。</li>
 * </ol>
 *
 * <p>这里不连真 Redis，是因为要构造的场景本身就是「Redis 不可用 / 超时」，
 * 用真实例反而没法稳定复现，只能靠 sleep 碰运气。
 */
class ProductCacheEvictionTest {

    private StringRedisTemplate redisTemplate;
    private CacheHealth cacheHealth;
    private ProductCache productCache;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        cacheHealth = new CacheHealth();
        productCache = new ProductCache(redisTemplate, mock(ObjectMapper.class), cacheHealth);
    }

    @Test
    @DisplayName("多个商品一次删完：只发一条 DEL 命令")
    void evictAll_sendsSingleDeleteCommand() {
        productCache.evictAll(List.of(1L, 2L, 3L));

        verify(redisTemplate, times(1)).delete(List.of(
                "cache:product:detail:1",
                "cache:product:detail:2",
                "cache:product:detail:3"));
        // 逐个删的老写法（ids.forEach(this::deleteSingleKey)）会让这里变成 3 次
        verify(redisTemplate, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("熔断中不访问 Redis：跳过删除，靠 TTL 兜底")
    void evictAll_skipsRedisWhileCircuitOpen() {
        cacheHealth.recordFailure();

        productCache.evictAll(List.of(1L, 2L));

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Redis 删除失败：不抛异常，但必须上报熔断")
    void evictAll_swallowsErrorAndTripsCircuit() {
        when(redisTemplate.delete(anyCollection()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertDoesNotThrow(() -> productCache.evictAll(List.of(1L)),
                "删缓存失败不能冒到调用方——那时订单已经提交，抛异常等于「订单成功但接口 500」");
        assertTrue(cacheHealth.isOpen(), "失败必须记账，否则后续请求还会一个个去等超时");
    }

    @Test
    @DisplayName("失效器把一笔订单的所有商品一次性交给缓存层")
    void evictNow_batchesIdsIntoOneCall() {
        ProductService productService = mock(ProductService.class);
        ProductCacheEvictor evictor = new ProductCacheEvictor(productService);
        List<Long> ids = LongStream.rangeClosed(1, 50).boxed().toList();

        evictor.evictNow(ids);

        verify(productService, times(1)).evictDetails(ids);
        verify(productService, never()).evictDetail(anyLong());
        evictor.shutdown();
    }
}
