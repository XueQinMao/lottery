package com.my.project.service.predict.impl;

import com.my.project.python.pool.PythonConnection;
import com.my.project.python.pool.PythonConnectionPool;
import com.my.project.service.config.LotteryModelConfig;
import com.my.project.service.predict.IInitPythonProcessService;
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
public class InitPythonProcessServiceImpl implements IInitPythonProcessService {

    private static final Logger logger = LoggerFactory.getLogger(InitPythonProcessServiceImpl.class);

    private static final String SCRIPT_NAME = "predict.py";

    private PythonConnectionPool pool;

    private final LotteryModelConfig lotteryModelConfig;

    public InitPythonProcessServiceImpl(LotteryModelConfig lotteryModelConfig) {
        this.lotteryModelConfig = lotteryModelConfig;
    }

    @Override
    public void init() throws IOException {
        pool = new PythonConnectionPool(2, SCRIPT_NAME, lotteryModelConfig.getPath()+"/model");
    }

    @Override
    public String runInference(Map<String, Object> request) {
        PythonConnection conn = null;
        try {
            conn = pool.borrowConnection();
            return conn.execute(request);
        } catch (Exception e) {
            logger.error("执行Python预测失败: {}", e.getMessage(), e);
        } finally {
            if (Objects.nonNull(conn)) {
                pool.returnConnection(conn);
            }
        }
        return null;
    }

    @Override
    public void shutdown() {
        pool.shutdown();
    }
}
