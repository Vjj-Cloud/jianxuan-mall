package com.jack.jianxuan.controller;

import com.jack.jianxuan.annotation.AdminOnly;
import com.jack.jianxuan.common.Result;
import com.jack.jianxuan.entity.Category;
import com.jack.jianxuan.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@AdminOnly
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class CategoryAdminController {

    private final ProductService productService;

    @GetMapping("/categories")
    public Result<List<Category>> list() {
        return Result.ok(productService.listCategories());
    }
}
