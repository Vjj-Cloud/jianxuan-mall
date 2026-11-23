package com.jack.jianxuan.controller;

import com.jack.jianxuan.common.Result;
import com.jack.jianxuan.common.UserContext;
import com.jack.jianxuan.dto.CartAddRequest;
import com.jack.jianxuan.dto.CartItemVO;
import com.jack.jianxuan.dto.CartQuantityRequest;
import com.jack.jianxuan.service.CartService;
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

import java.util.List;

/**
 * 购物车接口，全部需要登录（/api/** 已被 JwtInterceptor 拦截，这里不用重复判断）。
 *
 * 所有写操作都返回「操作后的完整购物车」，前端拿到就能直接覆盖本地状态，
 * 不用再补一次 GET /api/cart。
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public Result<List<CartItemVO>> list() {
        return Result.ok(cartService.list(UserContext.getUserId()));
    }

    @PostMapping
    public Result<List<CartItemVO>> add(@RequestBody @Valid CartAddRequest request) {
        return Result.ok(cartService.add(UserContext.getUserId(), request));
    }

    @PutMapping("/{itemId}")
    public Result<List<CartItemVO>> update(@PathVariable Long itemId,
                                          @RequestBody @Valid CartQuantityRequest request) {
        return Result.ok(cartService.updateQuantity(UserContext.getUserId(), itemId, request.getQuantity()));
    }

    @DeleteMapping("/{itemId}")
    public Result<List<CartItemVO>> remove(@PathVariable Long itemId) {
        return Result.ok(cartService.remove(UserContext.getUserId(), itemId));
    }

    @DeleteMapping
    public Result<List<CartItemVO>> clear() {
        return Result.ok(cartService.clear(UserContext.getUserId()));
    }
}
