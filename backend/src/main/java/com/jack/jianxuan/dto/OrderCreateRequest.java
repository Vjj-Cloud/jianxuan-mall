package com.jack.jianxuan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建订单的入参。
 *
 * 注意这里没有 totalAmount 字段 —— 金额由服务端根据当时的商品价格重新计算。
 * 如果让前端传金额，用户改一个请求体就能用 1 元买下手机。
 */
@Data
public class OrderCreateRequest {

    @NotEmpty(message = "请选择要购买的商品")
    @Valid
    private List<OrderItemRequest> items;

    @NotBlank(message = "请填写收货人姓名")
    @Size(max = 50, message = "收货人姓名过长")
    private String receiverName;

    @NotBlank(message = "请填写收货电话")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String receiverPhone;

    @NotBlank(message = "请填写收货地址")
    @Size(max = 255, message = "收货地址过长")
    private String receiverAddress;
}
