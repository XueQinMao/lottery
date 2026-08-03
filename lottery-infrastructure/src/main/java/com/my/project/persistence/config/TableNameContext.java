package com.my.project.persistence.config;

/**
 * TableNameContext
 *
 * @author 刘强
 * @version 2025/11/03 16:16
 **/
public class TableNameContext {

    private static final ThreadLocal<String> TABLE_SUFFIX = new ThreadLocal<>();

    public static void setSuffix(String suffix) {
        TABLE_SUFFIX.set(suffix);
    }

    public static String getSuffix() {
        return TABLE_SUFFIX.get();
    }

    public static void clear() {
        TABLE_SUFFIX.remove();
    }
}
