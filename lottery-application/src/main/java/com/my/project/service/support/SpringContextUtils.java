package com.my.project.service.support;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring上下文工具类
 * 用于获取Spring容器中的Bean
 * 
 * @author liuqiang
 * @since 2025-07-17
 */
@Component
public class SpringContextUtils implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextUtils.applicationContext = applicationContext;
    }

    /**
     * 获取ApplicationContext
     * 
     * @return ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 根据Bean名称获取Bean
     * 
     * @param name Bean名称
     * @return Bean实例
     */
    public static Object getBean(String name) {
        return applicationContext.getBean(name);
    }

    /**
     * 根据Bean类型获取Bean
     * 
     * @param clazz Bean类型
     * @param <T> 泛型类型
     * @return Bean实例
     */
    public static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }

    /**
     * 根据Bean名称和类型获取Bean
     * 
     * @param name Bean名称
     * @param clazz Bean类型
     * @param <T> 泛型类型
     * @return Bean实例
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return applicationContext.getBean(name, clazz);
    }

    /**
     * 获取指定类型的所有Bean
     * 
     * @param clazz Bean类型
     * @param <T> 泛型类型
     * @return Bean映射表
     */
    public static <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        return applicationContext.getBeansOfType(clazz);
    }

    /**
     * 获取指定类型的所有Bean列表
     * 
     * @param clazz Bean类型
     * @param <T> 泛型类型
     * @return Bean列表
     */
    public static <T> List<T> getBeansOfTypeList(Class<T> clazz) {
        Map<String, T> beansOfType = getBeansOfType(clazz);
        return new ArrayList<>(beansOfType.values());
    }

    /**
     * 获取指定类型的所有Bean名称
     * 
     * @param clazz Bean类型
     * @return Bean名称数组
     */
    public static String[] getBeanNamesForType(Class<?> clazz) {
        return applicationContext.getBeanNamesForType(clazz);
    }

    /**
     * 判断是否包含指定名称的Bean
     * 
     * @param name Bean名称
     * @return 是否包含
     */
    public static boolean containsBean(String name) {
        return applicationContext.containsBean(name);
    }

    /**
     * 判断指定名称的Bean是否为单例
     * 
     * @param name Bean名称
     * @return 是否为单例
     */
    public static boolean isSingleton(String name) {
        return applicationContext.isSingleton(name);
    }

    /**
     * 获取指定名称Bean的类型
     * 
     * @param name Bean名称
     * @return Bean类型
     */
    public static Class<?> getType(String name) {
        return applicationContext.getType(name);
    }

    /**
     * 获取当前激活的Profile
     * 
     * @return 激活的Profile数组
     */
    public static String[] getActiveProfiles() {
        return applicationContext.getEnvironment().getActiveProfiles();
    }

    /**
     * 获取默认的Profile
     * 
     * @return 默认Profile数组
     */
    public static String[] getDefaultProfiles() {
        return applicationContext.getEnvironment().getDefaultProfiles();
    }

    /**
     * 判断当前环境是否为指定的Profile
     * 
     * @param profile Profile名称
     * @return 是否为指定Profile
     */
    public static boolean acceptsProfiles(String profile) {
        return applicationContext.getEnvironment().acceptsProfiles(profile);
    }
}