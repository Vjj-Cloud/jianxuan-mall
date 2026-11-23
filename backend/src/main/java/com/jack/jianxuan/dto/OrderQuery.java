package com.jack.jianxuan.dto;

import lombok.Data;

@Data
public class OrderQuery {

    /** PENDING / SHIPPED / COMPLETED / CANCELLED，不传查全部 */
    private String status;
    /** 订单号模糊匹配，仅管理端使用；用户端传了也不起作用 */
    private String keyword;
    private Integer page = 1;
    private Integer size = 10;
}
