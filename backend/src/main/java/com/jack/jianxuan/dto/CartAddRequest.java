package com.jack.jianxuan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartAddRequest {

    @NotNull(message = "商品不能为空")
    private Long productId;

    /**
     * 不传时按 1 件处理（由 Service 兜底）。
     * 注意 @Min 对 null 是放行的，所以这里不能用 @NotNull，
     * 否则前端省略 quantity 会被判 400。
     */
    @Min(value = 1, message = "购买数量不能小于 1")
    private Integer quantity;
}
