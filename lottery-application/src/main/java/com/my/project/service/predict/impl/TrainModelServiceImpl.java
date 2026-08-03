package com.my.project.service.predict.impl;

import com.my.project.python.TrainModelProcess;
import com.my.project.service.config.LotteryModelConfig;
import com.my.project.service.predict.ITrainModelService;
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
public class TrainModelServiceImpl implements ITrainModelService {

    private static final Logger logger = LoggerFactory.getLogger(TrainModelServiceImpl.class);

    private final LotteryModelConfig lotteryModelConfig;

    private final TrainModelProcess trainModelProcess;

    public TrainModelServiceImpl(LotteryModelConfig lotteryModelConfig, TrainModelProcess trainModelProcess) {
        this.lotteryModelConfig = lotteryModelConfig;
        this.trainModelProcess = trainModelProcess;
    }

    @Override
    public void train() throws IOException, InterruptedException{
        logger.info("========== 开始训练模型 ==========");
        // 构建文件路径
        Map<String, String> csvConfigMap = lotteryModelConfig.getCsv();
        String mlFeaturesPath = lotteryModelConfig.getPath().concat(csvConfigMap.getOrDefault("ml", "ml_features.csv"));
        String historyPath = lotteryModelConfig.getPath().concat(csvConfigMap.getOrDefault("history", "history.csv"));
        String sequenceFeaturesPath = lotteryModelConfig.getPath().concat(lotteryModelConfig.getCsv().getOrDefault("sequence", "sequence_features.csv"));
        String markovFeaturesPath = lotteryModelConfig.getPath().concat(lotteryModelConfig.getCsv().getOrDefault("markov", "markov_features.csv"));
        String modelPath = lotteryModelConfig.getPath()+ "/model";

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
