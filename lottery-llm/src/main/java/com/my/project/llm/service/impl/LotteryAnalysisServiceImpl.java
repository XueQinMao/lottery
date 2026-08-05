package com.my.project.llm.service.impl;

import com.alibaba.fastjson.JSON;
import com.my.project.llm.bo.FeatureForecastBo.FeatureForecastItem;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.llm.prompt.LotteryAnalysisPrompt;
import com.my.project.llm.service.ILotteryAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * LotteryAnalysisServiceImpl
 *
 * <p>只负责单形态推算的大模型调用；候选压缩与硬校验在 application 层完成。
 *
 * @author 刘强
 * @version 2026/08/19
 **/
@Slf4j
@Service
public class LotteryAnalysisServiceImpl implements ILotteryAnalysisService {

    private final ChatClient lotteryChatClient;

    public LotteryAnalysisServiceImpl(@Qualifier("lotteryChatClient") ChatClient lotteryChatClient) {
        this.lotteryChatClient = lotteryChatClient;
    }

    @Override
    public LotteryAnalysisRespBo analyze(LotteryAnalysisReqBo reqBo) {
        throw new UnsupportedOperationException("特征报告直方图已移除；形态请用 forecastOne");
    }

    @Override
    public FeatureForecastItem forecastOne(String featureLabel, String valueHint, String snapshotJson) {
        if (StringUtils.isAnyBlank(featureLabel, snapshotJson)) {
            throw new IllegalArgumentException("形态快照不能为空");
        }
        log.info("开始调用大模型推算形态 [{}]", featureLabel);
        FeatureForecastItem result = lotteryChatClient.prompt()
            .user(u -> u.text(LotteryAnalysisPrompt.FORECAST_ONE_PROMPT)
                .param("label", featureLabel)
                .param("hint", StringUtils.defaultString(valueHint))
                .param("snapshot", snapshotJson)
                .param("format", LotteryAnalysisPrompt.FORECAST_ONE_FORMAT))
            .call()
            .entity(FeatureForecastItem.class);
        log.info("形态 [{}] 大模型推算完成: {}", featureLabel, JSON.toJSONString(result));
        return result;
    }
}
