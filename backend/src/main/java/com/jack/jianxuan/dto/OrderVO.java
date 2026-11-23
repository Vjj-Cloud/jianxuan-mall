package com.jack.jianxuan.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
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
    /**
     * 只有管理端才输出这个字段：管理员需要知道「这是谁下的单」，
     * 而用户端查自己的订单时，userId 恒等于自己，返回出去既没用又多余。
     * NON_NULL 让它自然消失——用户端不 set，JSON 里就不出现这个 key。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long userId;
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
        // 第三个参数是「忽略列表」：items 要手动转换，userId 默认不带
        BeanUtils.copyProperties(order, vo, "items", "userId");
        vo.setItems(items.stream().map(OrderItemVO::from).toList());
        return vo;
    }

    /** 管理端专用：在用户端 VO 的基础上补上 userId */
    public static OrderVO fromAdmin(Order order, List<OrderItem> items) {
        OrderVO vo = from(order, items);
        vo.setUserId(order.getUserId());
        return vo;
    }
}
