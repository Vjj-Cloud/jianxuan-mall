package com.jack.jianxuan.controller;

import com.jack.jianxuan.common.BusinessException;
import com.jack.jianxuan.common.Result;
import com.jack.jianxuan.dto.LoginRequest;
import com.jack.jianxuan.dto.LoginResponse;
import com.jack.jianxuan.dto.RegisterRequest;
import com.jack.jianxuan.dto.UserVO;
import com.jack.jianxuan.entity.User;
import com.jack.jianxuan.service.UserService;
import com.jack.jianxuan.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.jack.jianxuan.common.UserContext;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(buildResponse(userService.register(req)));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(buildResponse(userService.login(req)));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        User user = userService.getById(UserContext.getUserId());
        if (user == null) {
            throw new BusinessException(401, "用户不存在，请重新登录");
        }
        return Result.ok(UserVO.from(user));
    }

    private LoginResponse buildResponse(User user) {
        String token = jwtUtil.generate(user.getId(), user.getRole());
        return new LoginResponse(token, UserVO.from(user));
    }
}
