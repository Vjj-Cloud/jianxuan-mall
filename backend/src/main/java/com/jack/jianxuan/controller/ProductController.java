package com.jack.jianxuan.controller;

import com.jack.jianxuan.common.Result;
import com.jack.jianxuan.dto.PageResult;
import com.jack.jianxuan.dto.ProductQuery;
import com.jack.jianxuan.dto.ProductVO;
import com.jack.jianxuan.entity.Category;
import com.jack.jianxuan.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端商品接口，全部公开，游客也能浏览。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.ok(productService.listCategories());
    }

    /**
     * 查询参数直接绑到 ProductQuery 上：
     * ?keyword=耳机&categoryId=1&sort=priceAsc&page=1&size=12
     */
    @GetMapping("/products")
    public Result<PageResult<ProductVO>> list(ProductQuery query) {
        return Result.ok(productService.page(query));
    }

    @GetMapping("/products/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id));
    }
}
