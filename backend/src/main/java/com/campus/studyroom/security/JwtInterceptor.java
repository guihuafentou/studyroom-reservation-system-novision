package com.campus.studyroom.security;

import com.campus.studyroom.common.BusinessException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器：校验令牌、填充用户上下文、按注解校验角色。
 * 角色注解兼容类级与方法级（AnnotatedElementUtils 先查方法，再回退类）。
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行非 Controller 请求（静态资源、错误页等）
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // 放行 CORS 预检请求（不带 Authorization 头）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw BusinessException.unauthorized("未登录或登录已过期");
        }
        Claims claims = jwtUtil.parse(auth.substring(7));
        if (claims == null) {
            throw BusinessException.unauthorized("令牌无效或已过期");
        }

        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        Integer role = claims.get("role", Integer.class);
        UserContext.set(userId, username, role);

        try {
            // 方法级优先，类级兜底：AdminController 类上 @RequireRole(1) 同样生效
            RequireRole requireRole = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getMethod(), RequireRole.class);
            if (requireRole == null) {
                requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
            }
            if (requireRole != null && requireRole.value() == 1 && !UserContext.isAdmin()) {
                throw BusinessException.forbidden("无管理员权限");
            }
            return true;
        } catch (Exception e) {
            UserContext.clear();
            throw e;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
