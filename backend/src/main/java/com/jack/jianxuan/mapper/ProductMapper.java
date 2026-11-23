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
     * 取消订单时回补库存、回退销量。与 deductStock 完全对称。
     *
     * GREATEST(..., 0) 是防御：如果历史数据里 sales 已经小于要回退的数量
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
