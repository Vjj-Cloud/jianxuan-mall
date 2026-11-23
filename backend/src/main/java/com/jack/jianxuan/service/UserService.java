package com.jack.jianxuan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jack.jianxuan.common.BusinessException;
import com.jack.jianxuan.dto.LoginRequest;
import com.jack.jianxuan.dto.RegisterRequest;
import com.jack.jianxuan.entity.User;
import com.jack.jianxuan.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public User register(RegisterRequest req){
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (count !=null && count > 0){
            throw new BusinessException("用户名已被注册");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(StringUtils.hasText(req.getNickname()) ? req.getNickname():req.getUsername());
        user.setRole("USER");

        try {
            userMapper.insert(user);
        }catch (DuplicateKeyException e){
            throw new BusinessException("用户名已被注册");
        }
        return user;
    }

    public User login(LoginRequest req) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));

        // 用户不存在 和 密码错误 返回同一句提示，防止攻击者枚举用户名
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        return user;
    }

    public User getById(Long userId) {
        return userMapper.selectById(userId);
    }

}
