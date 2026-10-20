package com.my.project.application.predict.impl;

import com.my.project.python.pool.PythonConnection;
import com.my.project.python.pool.PythonConnectionPool;
import com.my.project.application.config.LotteryModelProperties;
import com.my.project.application.predict.InitPythonProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import jakarta.annotation.PreDestroy;

/**
 * InitPythonProcessServiceImpl
 *
 * @author 刘强
 * @version 2025/10/28 19:52
 **/
@Service
@Primary
public class InitPythonProcessServiceImpl implements InitPythonProcessService {

    private static final Logger logger = LoggerFactory.getLogger(InitPythonProcessServiceImpl.class);

    private static final String SCRIPT_NAME = "predict.py";

    private volatile PythonConnectionPool connectionPool;

    private final LotteryModelProperties modelProperties;

    public InitPythonProcessServiceImpl(LotteryModelProperties modelProperties) {
        this.modelProperties = modelProperties;
    }

    @Override
    public synchronized void init() throws IOException {
        // 每次初始化仍重新加载最新模型/历史；不能用幂等跳过使跨期预测继续使用旧历史。
        PythonConnectionPool replacement = createPool();
        PythonConnectionPool previous = connectionPool;
        connectionPool = replacement;
        if (previous != null) previous.shutdown();
    }

    PythonConnectionPool createPool() throws IOException {
        return new PythonConnectionPool(2, SCRIPT_NAME, modelProperties.getPath() + "/model");
    }

    @Override
    public String runInference(Map<String, Object> request) {
        PythonConnectionPool pool = connectionPool;
        if (pool == null) throw new IllegalStateException("Python连接池尚未初始化或已关闭");
        IOException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            PythonConnection connection = null;
            try {
                connection = pool.borrowConnection();
                return connection.execute(request);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待Python连接被中断", e);
            } catch (IOException e) {
                last = e;
                if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Python预测被中断", e);
                logger.warn("Python预测第{}次失败: {}", attempt, e.getMessage(), e);
            } finally {
                if (connection != null) pool.returnConnection(connection);
            }
        }
        throw new IllegalStateException("Python预测失败，已用同一输入尝试2次", last);
    }

    @Override
    @PreDestroy
    public synchronized void shutdown() {
        PythonConnectionPool pool = connectionPool;
        connectionPool = null;
        if (pool != null) pool.shutdown();
    }
}
