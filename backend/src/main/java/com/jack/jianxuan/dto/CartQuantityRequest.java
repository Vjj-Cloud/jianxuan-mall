package com.jack.jianxuan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartQuantityRequest {

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "购买数量不能小于 1")
    private Integer quantity;
}
