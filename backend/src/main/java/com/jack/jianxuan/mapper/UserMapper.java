package com.jack.jianxuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jack.jianxuan.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
