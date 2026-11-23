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

    /**
     * 幂等键：由客户端为「一次购买意图」生成，重试时必须保持不变。
     *
     * <p>前端在进入结算页时生成一个 UUID，提交时带上；提交失败后重试沿用同一个值，
     * 这样服务端能把「用户点了两次」和「用户真的想下两单」区分开。
     *
     * <p>要求必填而不是服务端兜底生成：服务端生成的键每次都不一样，等于没有幂等。
     *
     * <p>格式限制是为了防止把任意字符拼进 Redis key 和唯一索引——
     * 比如传一个 `a:b` 就能和其他 key 前缀串味，或者塞超长字符串撑爆索引。
     */
    @NotBlank(message = "缺少幂等键 requestId")
    @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "幂等键格式不合法")
    private String requestId;

    /**
     * 结算的商品行。
     *
     * <p>必须有条数上限：没有上限时，一个请求可以塞进来几万行，
     * 后面的 {@code selectBatchIds} 会拼出一条几万项的 IN 子句（MySQL 直接报 SQL 过长），
     * 就算没报错，一次事务里扣几万次库存也会长时间持锁、拖垮其他下单请求。
     * 正常人一次结算几十种商品就顶天了，50 是宽松但有意义的边界。
     */
    @NotEmpty(message = "请选择要购买的商品")
    @Size(max = 50, message = "一次最多结算 50 种商品")
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
