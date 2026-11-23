package com.jack.jianxuan.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j 文档配置。
 *
 * 除了填标题版本，真正有用的是注册 Bearer 鉴权：
 * 打开 doc.html 点右上角「Authorize」，粘贴登录拿到的 token，
 * 之后所有需要登录的接口都能直接在文档里调通，不用再去 curl 拼 header。
 *
 * 不加这段配置，文档里的接口一旦有 @AdminOnly 或需要登录，点「发送」
 * 永远返回 401，文档就只剩「看」的作用。
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI jianxuanOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("简选商城 API")
                        .version("1.0.0")
                        .description("""
                                Vue 3 + Spring Boot 3 电商项目后端接口。

                                **试用步骤**
                                1. 调 `POST /api/auth/login`，账号 `admin / admin123` 或 `user / user123`
                                2. 复制返回的 `data.token`
                                3. 点右上角 Authorize，粘贴 token（不需要自己加 "Bearer " 前缀）
                                4. 之后可直接调用 `/api/cart`、`/api/orders`、`/api/admin/**`

                                **状态码约定**
                                - 200 成功；400 业务校验失败；401 未登录或 token 失效；403 已登录但非管理员；404 资源不存在
                                """))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 token")))
                // 全局声明：所有接口默认可能需要鉴权。公开接口不带 token 也能调，不受影响。
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
