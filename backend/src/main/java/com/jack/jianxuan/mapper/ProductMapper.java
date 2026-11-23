package com.jack.jianxuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jack.jianxuan.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 扣库存 + 加销量。本项目最核心的一条 SQL。
     *
     * 为什么不能「先 select 查库存，Java 里 if (stock >= qty) 再 update」？
     * 因为那是两条 SQL。两个请求可能同时查到 stock = 1，都判断「够」，
     * 然后各自把库存改成 0 —— 同一件商品卖给了两个人，这就是超卖。
     *
     * 把判断写进 WHERE 之后，判断和写入是同一条语句，InnoDB 会对这一行加行锁，
     * 后到的请求要么排队等锁、要么在 stock 已经变小之后不满足条件而返回 0 行。
     * 返回 0 就说明「库存不够 / 已下架 / 已删除」，直接抛异常让整个订单事务回滚。
     *
     * sales 跟着一起加，是为了销量排序（sort=salesDesc）能反映真实成交。
     */
    @Update("""
            UPDATE product
               SET stock = stock - #{quantity},
                   sales = sales + #{quantity}
             WHERE id      = #{productId}
               AND status  = 1
               AND deleted = 0
               AND stock  >= #{quantity}
            """)
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 取消订单时回补库存、回退销量。
     *
     * 注意它和 deductStock 并**不对称**：这里故意不带 status / deleted 条件。
     *
     * 这不是漏写，是刻意的。回补是「补偿操作」，补偿的对象是当初被扣掉的那一份，
     * 而不是「这件商品现在还卖不卖」。扣减发生的那一刻商品是有效的，所以撤销时
     * 也不该用当前状态重新判断一遍。
     *
     * 如果给 WHERE 加上 deleted = 0，会出现这样的数据损坏：
     *   商品被软删 → 用户取消订单 → 回补被静默跳过 → 库存永久少 1，且不报错；
     *   商品日后恢复上架 → 库存是错的，而且没有任何痕迹能查出错在哪。
     *
     * 这条原则可以推广：**补偿逻辑不要复用业务判断条件**。
     * 对账、退款、事务回滚都是同一类问题 —— 补偿只负责把状态退回原处，
     * 不负责重新评估「现在该不该退」。
     *
     * 唯一会「什么都没发生」的情况是商品被物理删除，此时影响 0 行。这是可接受的：
     * 被回补的对象已经不存在，没有其他地方需要这份库存。
     *
     * GREATEST(..., 0) 是另一层防御：如果历史数据里 sales 已经小于要回退的数量
     * （比如人工改过库），不要让 sales 变成负数。
     */
    @Update("""
            UPDATE product
               SET stock = stock + #{quantity},
                   sales = GREATEST(sales - #{quantity}, 0)
             WHERE id = #{productId}
            """)
    int restoreStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
