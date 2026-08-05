package com.my.project.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM 推荐异步任务专用线程池：单线程，避免与预测/启动异步抢公共池。
 **/
@Configuration
public class LlmAdjustExecutorConfig {

    @Bean(name = "llmAdjustExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor llmAdjustExecutor() {
        AtomicInteger seq = new AtomicInteger(1);
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "llm-adjust-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        return new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), factory, new ThreadPoolExecutor.AbortPolicy());
    }
}
