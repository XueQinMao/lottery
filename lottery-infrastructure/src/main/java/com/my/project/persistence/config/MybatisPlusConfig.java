package com.my.project.persistence.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MybatisPlusConfig
 *
 * @author 刘强
 * @version 2025/08/01 16:46
 **/
@Configuration
@EnableTransactionManagement
public class MybatisPlusConfig {


    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        //分表
        DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor =
                new DynamicTableNameInnerInterceptor();

        dynamicTableNameInnerInterceptor.setTableNameHandler((sql, tableName) -> {
            // 只处理 t_order 表，其他表保持不变
            if ("t_predict_result".equals(tableName)) {
                String suffix = TableNameContext.getSuffix();
                if (suffix != null && !suffix.isEmpty()) {
                    return tableName + "_" + suffix;
                }
            }
            return tableName;
        });

        interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);
        //分页

        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
        paginationInnerInterceptor.setDbType(DbType.MARIADB);
        paginationInnerInterceptor.setOverflow(false);
        paginationInnerInterceptor.setMaxLimit(1000000L);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);

        return interceptor;
    }


}
