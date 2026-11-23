package com.jack.jianxuan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 不写 @TableName：类名 CartItem 按驼峰转下划线默认映射到 cart_item，
 * 与真实表名一致，不用手写。只有「类名和表名对不上」时才需要显式声明。
 */
@Data
public class CartItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
