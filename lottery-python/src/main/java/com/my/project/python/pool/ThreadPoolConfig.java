package com.my.project.python.pool;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * ThreadPoolConfig
 *
 * @author 刘强
 * @version 2025/10/29 17:18
 **/
@Configuration
public class ThreadPoolConfig {

    @Bean("predictConsumerPool")
    public ThreadPoolExecutor predictConsumerPool() {

        ThreadFactory threadFactory = new BasicThreadFactory.Builder()
                .namingPattern("predict-pool-%d")
                .build();
        return new ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(3), threadFactory, new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
