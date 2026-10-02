package com.campus.studyroom.config;

import com.campus.studyroom.security.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：拦截器注册 + 跨域
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login", "/api/auth/register",
                        // SSE 事件流：EventSource 无法携带 Authorization 头，且内容仅含座位状态（无个人信息）
                        "/api/seat-events/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // JWT 存放于 Authorization 请求头，无需携带 Cookie 凭证
        // 开发环境前端(Vite 5173)走代理同源，此处限定来源仅为跨域部署兜底；部署时按需增改
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
