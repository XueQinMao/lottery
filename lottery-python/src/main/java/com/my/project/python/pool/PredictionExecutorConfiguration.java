package com.my.project.python.pool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * PredictionExecutorConfiguration
 *
 * @author 刘强
 * @version 2025/10/29 17:18
 **/
@Configuration
public class PredictionExecutorConfiguration {

    @Bean("predictConsumerPool")
    public ThreadPoolExecutor predictConsumerPool() {

        ThreadFactory threadFactory = Thread.ofPlatform().name("predict-pool-", 1).factory();
        return new ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(3), threadFactory, new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
