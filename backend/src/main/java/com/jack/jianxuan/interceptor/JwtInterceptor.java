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

        String role = claims.get("role", String.class);

        // 权限校验必须放在 UserContext.set() 之前。
        //
        // 原因：Spring MVC 只有在 preHandle 返回 true 之后，才会把 afterCompletion
        // 登记到本次请求的回调链上。preHandle 里抛异常 → afterCompletion 不会被调用
        // → 第 47 行 set 进去的 userId/role 会残留在 Tomcat 工作线程上。
        // 而 Tomcat 线程是复用的，下一个请求进来时如果没有任何地方重新 set，
        // UserContext.getRole() 就会读到上一个请求的 ADMIN —— 属于越权隐患。
        //
        // 与其「先污染再清理」，不如让污染根本不发生：校验不过就直接抛，
        // 此时 ThreadLocal 一个字都没写过，也就不需要依赖任何清理动作兜底。
        if (needAdmin(handler) && !"ADMIN".equals(role)) {
            throw new BusinessException(403, "没有权限");
        }

        UserContext.set(Long.valueOf(claims.getSubject()), role);
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
