package com.jack.jianxuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String name;
    private String subtitle;
    /** 金额必须用 BigDecimal，不能用 double：double 是二进制浮点，0.1 + 0.2 != 0.3 */
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private Integer sales;
    /** 1 上架 / 0 下架 */
    private Integer status;
    private String cover;
    /** 详情文案；数据库列名 description，避免用 MySQL 保留字 desc 当列名 */
    private String description;

    /**
     * 逻辑删除标记。加了 @TableLogic 之后：
     * - delete 不会真的 DELETE，而是 UPDATE product SET deleted = 1
     * - 所有查询会自动追加 WHERE deleted = 0
     */
    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
