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
import java.util.Objects;

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

    private PythonConnectionPool connectionPool;

    private final LotteryModelProperties modelProperties;

    public InitPythonProcessServiceImpl(LotteryModelProperties modelProperties) {
        this.modelProperties = modelProperties;
    }

    @Override
    public void init() throws IOException {
        connectionPool = new PythonConnectionPool(2, SCRIPT_NAME, modelProperties.getPath()+"/model");
    }

    @Override
    public String runInference(Map<String, Object> request) {
        PythonConnection connection = null;
        try {
            connection = connectionPool.borrowConnection();
            return connection.execute(request);
        } catch (Exception e) {
            logger.error("执行Python预测失败: {}", e.getMessage(), e);
        } finally {
            if (Objects.nonNull(connection)) {
                connectionPool.returnConnection(connection);
            }
        }
        return null;
    }

    @Override
    public void shutdown() {
        connectionPool.shutdown();
    }
}
