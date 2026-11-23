package com.jack.jianxuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jack.jianxuan.entity.CartItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /**
     * 数量累加，同时把「不超过库存」的判断塞进 WHERE。
     *
     * 为什么不用「先 select 出 quantity，再算好余量 update」？
     * 那是两条 SQL，中间隔着一次网络往返，并发请求会在缝隙里同时通过判断。
     * 这里判断和写入是同一条 UPDATE，InnoDB 对目标行加行锁，
     * 读到的 quantity 和写回的 quantity 之间不存在时间窗口。
     */
    @Update("""
            UPDATE cart_item
               SET quantity   = quantity + #{delta},
                   updated_at = NOW()
             WHERE user_id    = #{userId}
               AND product_id = #{productId}
               AND quantity + #{delta} <= #{stock}
            """)
    int increaseQuantity(@Param("userId") Long userId,
                         @Param("productId") Long productId,
                         @Param("delta") Integer delta,
                         @Param("stock") Integer stock);

    /**
     * 直接改成指定数量。
     *
     * 两个关键点：
     * 1. WHERE 里带 user_id，拿别人的 itemId 来改会命中 0 行，杜绝 IDOR 越权；
     * 2. JOIN product 在库内校验「没超库存 且 商品仍上架」，同样不给应用层留判断窗口。
     *
     * p.deleted = 0 必须手写：JOIN 出来的表不会自动套 @TableLogic 的逻辑删除条件。
     */
    @Update("""
            UPDATE cart_item c
              JOIN product p ON p.id = c.product_id
               SET c.quantity   = #{quantity},
                   c.updated_at = NOW()
             WHERE c.id      = #{itemId}
               AND c.user_id = #{userId}
               AND p.status  = 1
               AND p.deleted = 0
               AND #{quantity} <= p.stock
            """)
    int updateQuantity(@Param("userId") Long userId,
                       @Param("itemId") Long itemId,
                       @Param("quantity") Integer quantity);

    @Delete("DELETE FROM cart_item WHERE id = #{itemId} AND user_id = #{userId}")
    int deleteByIdAndUser(@Param("itemId") Long itemId, @Param("userId") Long userId);
}
