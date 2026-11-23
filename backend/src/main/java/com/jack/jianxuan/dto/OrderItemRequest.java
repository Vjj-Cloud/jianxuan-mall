package com.jack.jianxuan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderItemRequest {

    @NotNull(message = "商品不能为空")
    private Long productId;

    /**
     * 购买数量。
     *
     * <p>上限不是「防君子不防小人」的装饰，它挡的是一个真实的金额/库存漏洞：
     * {@code OrderService.create} 会把同一商品的多行数量用 {@code Integer::sum} 合并，
     * 而 Integer 加法是静默溢出的——两条各 20 亿的 quantity 合并后变成负数，
     * 于是：
     * <ul>
     *   <li>「库存是否足够」的判断 {@code stock >= quantity} 对负数恒为真，校验形同虚设；</li>
     *   <li>扣库存的 SQL {@code stock = stock - quantity} 反而把库存加上去了；</li>
     *   <li>订单总额 = 单价 × 负数，变成一笔「买东西还倒赚」的订单。</li>
     * </ul>
     * 加上 @Max 之后，合并结果最大是 999 × 50（items 上限）= 49950，远离溢出边界。
     * 校验要放在「进入业务逻辑之前」，因为溢出发生在合并的那一刻，业务代码已经来不及判断了。
     */
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量不能小于 1")
    @Max(value = 999, message = "单个商品一次最多购买 999 件")
    private Integer quantity;
}
