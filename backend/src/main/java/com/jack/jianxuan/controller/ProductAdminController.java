package com.jack.jianxuan.controller;

import com.jack.jianxuan.annotation.AdminOnly;
import com.jack.jianxuan.common.Result;
import com.jack.jianxuan.dto.PageResult;
import com.jack.jianxuan.dto.ProductQuery;
import com.jack.jianxuan.dto.ProductSaveRequest;
import com.jack.jianxuan.dto.ProductStatusRequest;
import com.jack.jianxuan.dto.ProductVO;
import com.jack.jianxuan.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端商品接口，全部要求 ADMIN。
 *
 * @AdminOnly 贴在类上，等于这个 Controller 的每个方法都要管理员权限，
 * 不用在方法上一个个重复标。拦截器里 needAdmin() 会同时检查方法级和类级注解。
 */
@AdminOnly
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductService productService;

    /**
     * 商品列表（含下架）。status 不传就是全部。
     */
    @GetMapping
    public Result<PageResult<ProductVO>> list(ProductQuery query) {
        return Result.ok(productService.adminPage(query));
    }

    @PostMapping
    public Result<ProductVO> create(@Valid @RequestBody ProductSaveRequest req) {
        return Result.ok(productService.create(req));
    }

    /**
     * 编辑。注意这里没有 @Valid：接口约定是「字段均可选，只更新传入项」，
     * 如果加了 @Valid，那些 @NotNull 会强制前端每次都全量提交。
     */
    @PutMapping("/{id}")
    public Result<ProductVO> update(@PathVariable Long id, @RequestBody ProductSaveRequest req) {
        return Result.ok(productService.update(id, req));
    }

    /**
     * 逻辑删除，返回 data 为 null。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<ProductVO> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody ProductStatusRequest req) {
        return Result.ok(productService.updateStatus(id, req.getStatus()));
    }
}
