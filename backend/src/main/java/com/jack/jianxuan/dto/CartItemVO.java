package com.jack.jianxuan.dto;

import com.jack.jianxuan.entity.CartItem;
import lombok.Data;

/**
 * 购物车条目视图。
 *
 * itemId 是 cart_item 的主键，前端改数量/删除都用它；
 * product 直接把商品对象嵌进来，前端购物车页面不用再发 N 个请求查商品详情。
 */
@Data
public class CartItemVO {

    private Long itemId;
    private Long productId;
    private Integer quantity;
    private ProductVO product;

    public static CartItemVO from(CartItem item, ProductVO product) {
        CartItemVO vo = new CartItemVO();
        vo.setItemId(item.getId());
        vo.setProductId(item.getProductId());
        vo.setQuantity(item.getQuantity());
        vo.setProduct(product);
        return vo;
    }
}
