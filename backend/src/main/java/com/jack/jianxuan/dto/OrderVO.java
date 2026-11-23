package com.jack.jianxuan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jack.jianxuan.entity.Order;
import com.jack.jianxuan.entity.OrderItem;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVO {

    private Long id;
    private String orderNo;
    private List<OrderItemVO> items;
    private BigDecimal totalAmount;
    /** PENDING / SHIPPED / COMPLETED / CANCELLED，前端直接拿来做按钮显隐 */
    private String status;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    /**
     * 统一成秒级 ISO 格式。
     * 不写 @JsonFormat 的话，刚下完单返回的对象里是 LocalDateTime.now() 的
     * 纳秒精度（2026-11-23T12:48:39.5840771），而重新查库拿到的是 DATETIME 的
     * 秒级（2026-11-23T12:48:40）——同一个字段两种格式，前端解析容易踩坑。
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime shippedAt;

    public static OrderVO from(Order order, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        // userId 不在 VO 里：用户只能看自己的订单，把 userId 暴露出去没有任何意义
        BeanUtils.copyProperties(order, vo, "items");
        vo.setItems(items.stream().map(OrderItemVO::from).toList());
        return vo;
    }
}
