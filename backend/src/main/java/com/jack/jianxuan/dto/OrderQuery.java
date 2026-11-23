package com.jack.jianxuan.dto;

import lombok.Data;

@Data
public class OrderQuery {

    /** PENDING / SHIPPED / COMPLETED / CANCELLED，不传查全部 */
    private String status;
    private Integer page = 1;
    private Integer size = 10;
}
