package com.jack.jianxuan.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductStatusRequest {

    /** 1 上架 / 0 下架 */
    @NotNull(message = "状态不能为空")
    private Integer status;
}
