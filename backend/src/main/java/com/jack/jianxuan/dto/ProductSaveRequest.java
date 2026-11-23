package com.jack.jianxuan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理端新增 / 编辑商品的请求体。
 *
 * 为什么不直接用 Product 实体接收？
 * 1. 实体里有 id、sales、deleted、createdAt 这些「服务端说了算」的字段，
 *    让前端传等于把主键和删除标记交给外部控制；
 * 2. 前端字段叫 desc，实体叫 description，需要一层翻译；
 * 3. 校验注解必须贴在「请求」上，而不是「数据表映射」上。
 */
@Data
public class ProductSaveRequest {

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 100, message = "商品名称最多 100 个字符")
    private String name;

    @Size(max = 200, message = "副标题最多 200 个字符")
    private String subtitle;

    @NotNull(message = "售价不能为空")
    @DecimalMin(value = "0.01", message = "售价必须大于 0")
    private BigDecimal price;

    @DecimalMin(value = "0.00", message = "划线价不能为负数")
    private BigDecimal originalPrice;

    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    @NotNull(message = "请选择商品分类")
    private Long categoryId;

    @Size(max = 500, message = "封面地址最多 500 个字符")
    private String cover;

    /** 前端字段叫 desc，数据库列叫 description */
    private String desc;

    /** 1 上架 / 0 下架，不传默认上架 */
    private Integer status;
}
