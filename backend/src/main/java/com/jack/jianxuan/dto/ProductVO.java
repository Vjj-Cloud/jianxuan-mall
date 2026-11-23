package com.jack.jianxuan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jack.jianxuan.entity.Product;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;

/**
 * 商品对外视图对象。
 *
 * 为什么不直接把 Product 实体扔给前端？
 * 1. 实体里有 status、deleted 这些内部字段，用户端不该看到；
 * 2. 前端列表要显示「手机数码」这样的分类名，实体里只有 categoryId；
 * 3. 前端约定的详情字段叫 desc，数据库列叫 description。
 * 这些差异都由 VO 这一层吸收掉，实体、数据库、前端三者互不干扰。
 */
@Data
public class ProductVO {

    private Long id;
    private String name;
    private String subtitle;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private Integer sales;
    private Long categoryId;
    private String categoryName;
    private String cover;
    /** 对应数据库的 description 列，对前端暴露成 desc */
    private String desc;

    /**
     * 上下架状态。只有管理端才填，用户端为 null。
     * NON_NULL 表示序列化时字段为 null 就直接不输出这个 key，
     * 这样用户端响应里根本看不到 status，管理端才看得到。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer status;

    /**
     * 只做字段搬运，categoryName 和 status 需要额外信息，交给 Service 填。
     */
    public static ProductVO from(Product product) {
        ProductVO vo = new ProductVO();
        // 同名字段批量复制；status 故意不复制，避免用户端泄露上下架状态
        BeanUtils.copyProperties(product, vo, "status");
        vo.setDesc(product.getDescription());
        return vo;
    }
}
