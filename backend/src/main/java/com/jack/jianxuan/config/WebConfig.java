package com.jack.jianxuan.config;

import com.jack.jianxuan.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/ping",
                        // 商品浏览是公开的：游客不登录也要能逛首页、看详情
                        "/api/products",
                        "/api/products/**",
                        "/api/categories",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**"
                );
    }
}
