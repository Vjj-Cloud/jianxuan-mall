package com.jack.jianxuan.common;

import org.springframework.stereotype.Component;

/**
 * 缓存可用性开关（极简熔断器）。
 *
 * <p>为什么需要它：Redis 挂掉时，每一次缓存调用都要等连接超时才返回。一次商品详情请求
 * 要碰三次缓存（读缓存、抢重建锁、回写缓存），串起来就是好几秒——接口不报错，但慢到不可用。
 * 熔断要解决的就是这个：一旦确认 Redis 不可用，接下来一段时间里直接跳过缓存，不再逐次等超时。
 *
 * <p>为什么不用 Resilience4j：需要的功能只是「短期记住 Redis 挂了」，一个 volatile 时间戳
 * 加一次大小比较就够了。上框架带来的配置和依赖成本，远大于这里能得到的收益。
 *
 * <p>5 秒后自动放行，相当于熔断器的「半开」状态——让下一个请求去试探 Redis 是否恢复。
 *
 * <p>关键点：熔断只做「跳过缓存」，绝不让请求失败。缓存是性能组件，
 * 它挂掉时业务应该退化成直查数据库，而不是被它一起带走。
 */
@Component
public class CacheHealth {

    /** 熔断时长：5 秒。太短会频繁试探超时，太长则 Redis 恢复后迟迟用不上缓存。 */
    private static final long OPEN_MILLIS = 5000;

    /** 熔断截止时间戳；当前时间小于它就是熔断中。0 表示从未失败过。 */
    private volatile long openUntil = 0;

    /** 是否处于熔断中。调用方据此决定要不要跳过缓存。 */
    public boolean isOpen() {
        return System.currentTimeMillis() < openUntil;
    }

    /** 记录一次 Redis 操作失败，开启或续期熔断。 */
    public void recordFailure() {
        openUntil = System.currentTimeMillis() + OPEN_MILLIS;
    }
}
