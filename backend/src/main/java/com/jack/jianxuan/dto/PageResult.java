package com.jack.jianxuan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页返回结构，前端分页组件只认这四个字段。
 * 写成泛型是为了购物车、订单、商品都能复用同一个壳子。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> list;
    private long total;
    private long page;
    private long size;

    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        return new PageResult<>(list, total, page, size);
    }
}
