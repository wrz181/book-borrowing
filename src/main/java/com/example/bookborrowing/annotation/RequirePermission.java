package com.example.bookborrowing.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解：标注在 Controller 方法（或类）上，要求当前登录用户拥有指定权限编码。
 * 由 {@code PermissionInterceptor} 在请求进入 Controller 前校验。
 * <p>
 * 语义：{@link #value()} 中声明的权限需<b>全部</b>满足；{@link #anyOf()} 中声明的权限只需满足<b>其一</b>；
 * 两者可同时声明，此时两组条件都需成立。仅写 {@code @RequirePermission("book:add")} 的既有用法语义不变。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 要求的权限编码，如 book:add。多个时需全部满足（为空表示不限制）。
     *
     * @return 权限编码数组
     */
    String[] value() default {};

    /**
     * 「命中其一即可」的权限编码，如 group:todo 与 todo:own（为空表示不限制）。
     *
     * @return 权限编码数组
     */
    String[] anyOf() default {};
}
