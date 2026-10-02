package com.campus.studyroom.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色要求注解：标注在 Controller 方法上，由 JwtInterceptor 校验
 * 1 = 管理员
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    int value() default 0;  // 0=登录即可；1=仅管理员
}
