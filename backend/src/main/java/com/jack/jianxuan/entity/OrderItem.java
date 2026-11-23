package com.jack.jianxuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细。
 *
 * product_name / cover / price 都是下单那一刻的快照，不是从 product 表 join 出来的。
 * 商品日后改名、改价、下架、甚至被删除，历史订单都必须保持原样。
 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private String cover;
    private BigDecimal price;
    private Integer quantity;
}
