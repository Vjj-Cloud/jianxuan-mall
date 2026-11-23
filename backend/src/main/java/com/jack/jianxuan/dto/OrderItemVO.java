package com.jack.jianxuan.dto;

import com.jack.jianxuan.entity.OrderItem;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细的对外视图。
 *
 * 字段名用 name 而不是 productName，是为了跟前端约定保持一致（前端渲染 item.name）。
 * 值来自 order_item 的快照列，不是实时商品数据。
 */
@Data
public class OrderItemVO {

    private Long productId;
    private String name;
    private String cover;
    private BigDecimal price;
    private Integer quantity;

    public static OrderItemVO from(OrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setProductId(item.getProductId());
        vo.setName(item.getProductName());
        vo.setCover(item.getCover());
        vo.setPrice(item.getPrice());
        vo.setQuantity(item.getQuantity());
        return vo;
    }
}
