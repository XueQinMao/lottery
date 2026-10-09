package com.my.project.application.predict.impl;

import com.my.project.python.TrainModelProcess;
import com.my.project.application.config.LotteryModelProperties;
import com.my.project.application.predict.TrainModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * TrainModelServiceImpl
 *
 * @author 刘强
 * @version 2025/10/23 16:04
 **/
@Service
@Primary
public class TrainModelServiceImpl implements TrainModelService {

    private static final Logger logger = LoggerFactory.getLogger(TrainModelServiceImpl.class);

    private final LotteryModelProperties modelProperties;

    private final TrainModelProcess trainModelProcess;

    public TrainModelServiceImpl(LotteryModelProperties modelProperties, TrainModelProcess trainModelProcess) {
        this.modelProperties = modelProperties;
        this.trainModelProcess = trainModelProcess;
    }

    @Override
    public void train() throws IOException, InterruptedException{
        logger.info("========== 开始训练模型 ==========");
        // 构建文件路径
        Map<String, String> csvConfigMap = modelProperties.getCsv();
        String mlFeaturesPath = modelProperties.getPath().concat(csvConfigMap.getOrDefault("ml", "ml_features.csv"));
        String historyPath = modelProperties.getPath().concat(csvConfigMap.getOrDefault("history", "history.csv"));
        String sequenceFeaturesPath = modelProperties.getPath().concat(modelProperties.getCsv().getOrDefault("sequence", "sequence_features.csv"));
        String markovFeaturesPath = modelProperties.getPath().concat(modelProperties.getCsv().getOrDefault("markov", "markov_features.csv"));
        String modelPath = modelProperties.getPath()+ "/model";

        Map<String, String> params = new HashMap<>();
        params.put("ml-features", mlFeaturesPath);
        params.put("history", historyPath);
        params.put("sequence-features", sequenceFeaturesPath);
        params.put("markov-features", markovFeaturesPath);
        params.put("model-dir", modelPath);
        trainModelProcess.process("train_models.py", params);

        logger.info("========== 模型训练完成 ==========");
    }
}
