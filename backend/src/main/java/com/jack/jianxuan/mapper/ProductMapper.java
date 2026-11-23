package com.jack.jianxuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jack.jianxuan.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
