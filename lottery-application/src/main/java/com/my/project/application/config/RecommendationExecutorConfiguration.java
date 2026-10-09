package com.my.project.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * LLM 推荐异步任务专用线程池：单线程，避免与预测/启动异步抢公共池。
 **/
@Configuration
public class RecommendationExecutorConfiguration {

    @Bean(name = "llmAdjustExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor llmAdjustExecutor() {
        ThreadFactory factory = Thread.ofPlatform().daemon(true).name("llm-adjust-", 1).factory();
        return new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), factory, new ThreadPoolExecutor.AbortPolicy());
    }
}
