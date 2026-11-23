package com.jack.jianxuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表。
 *
 * 表名是 orders 而不是 order：ORDER 是 SQL 保留字，直接用会逼着每条 SQL 都加反引号。
 * 类名 Order 不受影响（Java 的类名和 SQL 关键字是两回事）。
 */
@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 对用户展示的业务单号，与自增主键无关 */
    private String orderNo;
    private Long userId;
    /** 服务端按当时的商品价格重新计算，绝不信任前端传来的金额 */
    private BigDecimal totalAmount;
    /** PENDING / SHIPPED / COMPLETED / CANCELLED */
    private String status;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private LocalDateTime shippedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
