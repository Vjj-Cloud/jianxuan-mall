package com.jack.jianxuan.interceptor;

import com.jack.jianxuan.annotation.AdminOnly;
import com.jack.jianxuan.common.BusinessException;
import com.jack.jianxuan.common.UserContext;
import com.jack.jianxuan.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 浏览器跨域预检请求不带 token，直接放过
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX)) {
            throw new BusinessException(401, "请先登录");
        }

        String token = header.substring(PREFIX.length());
        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(401, "登录凭证无效，请重新登录");
        }

        UserContext.set(Long.valueOf(claims.getSubject()), claims.get("role", String.class));

        // 权限校验放在最后：先确认「你是谁」，再判断「你能不能进」
        if (needAdmin(handler) && !"ADMIN".equals(UserContext.getRole())) {
            throw new BusinessException(403, "没有权限");
        }
        return true;
    }

    /**
     * 判断本次请求是否要求 ADMIN 权限。
     * 方法上贴了 @AdminOnly 算，类上贴了也算（类级注解对这个 Controller 的所有方法生效）。
     */
    private boolean needAdmin(Object handler) {
        // 静态资源、404 等没有对应 Controller 方法，handler 不是 HandlerMethod
        if (!(handler instanceof HandlerMethod)) {
            return false;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        if (handlerMethod.getMethodAnnotation(AdminOnly.class) != null) {
            return true;
        }
        return handlerMethod.getBeanType().getAnnotation(AdminOnly.class) != null;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
