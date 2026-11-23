package com.jack.jianxuan.dto;

import lombok.Data;

/**
 * 商品列表的查询参数。
 *
 * 前端传的是 query string（?keyword=&categoryId=&sort=&page=&size=），
 * Controller 方法参数写成这个 POJO，Spring MVC 会自动按字段名逐个绑定，
 * 不需要一长串 @RequestParam。
 */
@Data
public class ProductQuery {

    /** 模糊匹配商品名或副标题 */
    private String keyword;
    private Long categoryId;
    /** 1 上架 / 0 下架 / null 全部。仅管理端使用，用户端强制只查上架 */
    private Integer status;
    /** priceAsc / priceDesc / salesDesc，其他值按默认排序 */
    private String sort;
    private long page = 1;
    private long size = 12;
}
