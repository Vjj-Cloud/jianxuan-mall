package com.jack.jianxuan.controller;

import com.jack.jianxuan.annotation.AdminOnly;
import com.jack.jianxuan.common.Result;
import com.jack.jianxuan.dto.OrderQuery;
import com.jack.jianxuan.dto.OrderVO;
import com.jack.jianxuan.dto.PageResult;
import com.jack.jianxuan.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端订单接口。
 *
 * @AdminOnly 贴在类上，整个类的所有方法都要求 ADMIN，
 * 以后再加接口也不用记得补注解——默认安全，而不是默认开放。
 *
 * 注意这里没有「改金额」「改收货信息」「删订单」接口：
 * 订单一旦生成就是交易凭证，管理员只能推动状态，不能篡改内容。
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@AdminOnly
public class OrderAdminController {

    private final OrderService orderService;

    @GetMapping
    public Result<PageResult<OrderVO>> list(OrderQuery query) {
        return Result.ok(orderService.adminPage(query));
    }

    @PutMapping("/{id}/ship")
    public Result<OrderVO> ship(@PathVariable Long id) {
        return Result.ok(orderService.ship(id));
    }
}
