package com.jack.jianxuan.controller;

import com.jack.jianxuan.common.Result;
import com.jack.jianxuan.common.UserContext;
import com.jack.jianxuan.dto.OrderCreateRequest;
import com.jack.jianxuan.dto.OrderQuery;
import com.jack.jianxuan.dto.OrderVO;
import com.jack.jianxuan.dto.PageResult;
import com.jack.jianxuan.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口，全部需要登录。
 *
 * 每个方法都用 UserContext.getUserId() 取当前用户，
 * 任何接口都不接受前端传来的 userId —— 那是越权的入口。
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Result<OrderVO> create(@RequestBody @Valid OrderCreateRequest request) {
        return Result.ok(orderService.create(UserContext.getUserId(), request));
    }

    @GetMapping
    public Result<PageResult<OrderVO>> list(OrderQuery query) {
        return Result.ok(orderService.page(UserContext.getUserId(), query));
    }

    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(UserContext.getUserId(), id));
    }

    @PostMapping("/{id}/cancel")
    public Result<OrderVO> cancel(@PathVariable Long id) {
        return Result.ok(orderService.cancel(UserContext.getUserId(), id));
    }

    @PostMapping("/{id}/confirm")
    public Result<OrderVO> confirm(@PathVariable Long id) {
        return Result.ok(orderService.confirm(UserContext.getUserId(), id));
    }
}
