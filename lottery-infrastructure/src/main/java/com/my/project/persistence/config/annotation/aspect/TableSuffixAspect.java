package com.my.project.persistence.config.annotation.aspect;

import com.my.project.persistence.config.TableNameContext;
import com.my.project.persistence.config.annotation.UseTableSuffix;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;


/**
 * TableSuffixAspect
 *
 * @author 刘强
 * @version 2025/11/03 16:22
 **/
@Aspect
@Component
public class TableSuffixAspect {

    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(com.my.project.persistence.config.annotation.UseTableSuffix)")
    public Object setTableSuffix(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        UseTableSuffix annotation = method.getAnnotation(UseTableSuffix.class);

        String suffix = null;

        // 1. 如果有 spel 表达式，优先解析
        if (!annotation.spel().isEmpty()) {
            StandardEvaluationContext context = new StandardEvaluationContext();
            Object[] args = joinPoint.getArgs();
            String[] paramNames = signature.getParameterNames();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }
            Expression expression = parser.parseExpression(annotation.spel());
            Object value = expression.getValue(context);
            if (value != null) {
                suffix = value.toString();
            }
        }

        // 2. 如果没有 spel，则用固定值
        if (suffix == null || suffix.isEmpty()) {
            suffix = annotation.value();
        }

        try {
            if (suffix != null && !suffix.isEmpty()) {
                TableNameContext.setSuffix(suffix);
            }
            return joinPoint.proceed();
        } finally {
            TableNameContext.clear();
        }
    }
}
