package com.my.project.persistence.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;

/**
 * UseTableSuffix
 *
 * @author 刘强
 * @version 2025/11/03 16:20
 **/
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UseTableSuffix {

    /**
     * 固定表后缀（优先级低于 spel）
     */
    String value() default "";

    /**
     * 支持 Spring EL 表达式，从方法入参动态取值
     * 例: "#userId % 2 == 0 ? 'even' : 'odd'"
     */
    String spel() default "";
}
