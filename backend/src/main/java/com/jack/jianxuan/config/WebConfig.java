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
                // 只拦 /api/**。接口文档（/v3/api-docs、/swagger-ui/**）和静态资源
                // 根本不在这个范围内，写进 exclude 是死配置，只会让人误以为它们需要放行。
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/ping",
                        // 商品浏览是公开的：游客不登录也要能逛首页、看详情
                        "/api/products",
                        "/api/products/**",
                        "/api/categories"
                );
    }
}
