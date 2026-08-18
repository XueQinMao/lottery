package com.my.project.llm.service.impl;

import com.alibaba.fastjson.JSON;
import com.my.project.llm.bo.FeatureForecastBo.FeatureForecastItem;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.llm.prompt.LotteryAnalysisPrompt;
import com.my.project.llm.service.ILotteryAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.DefaultChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * LotteryAnalysisServiceImpl
 *
 * <p>按单个形态调用 LLM，基于 Java 间隔节奏快照推算下一期值/区间。
 *
 * @author 刘强
 * @version 2026/08/17
 **/
@Slf4j
@Service
public class LotteryAnalysisServiceImpl implements ILotteryAnalysisService {

    private final ChatClient lotteryChatClient;

    public LotteryAnalysisServiceImpl(
            @Qualifier("lotteryChatClient") ChatClient lotteryChatClient) {
        this.lotteryChatClient = lotteryChatClient;
    }

    @Override
    public LotteryAnalysisRespBo analyze(LotteryAnalysisReqBo reqBo) {
        throw new UnsupportedOperationException("特征报告直方图已移除；形态请用 forecastOne（间隔快照）");
    }

    @Override
    public FeatureForecastItem forecastOne(String featureLabel, String valueHint, String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new IllegalArgumentException("形态快照不能为空");
        }
        log.info("开始推算形态 [{}]", featureLabel);
        System.out.println("prompt:"+LotteryAnalysisPrompt.FORECAST_ONE_PROMPT);
        System.out.println("label:"+featureLabel);
        System.out.println("hint:"+valueHint);
        System.out.println("snapshot:"+snapshotJson);
        System.out.println("format:"+ITEM_FORMAT);
        FeatureForecastItem result = lotteryChatClient.prompt()
            .user(u -> u.text(LotteryAnalysisPrompt.FORECAST_ONE_PROMPT)
                .param("label", featureLabel)
                .param("hint", valueHint)
                .param("snapshot", snapshotJson)
                .param("format", ITEM_FORMAT))
            .call()
            .entity(FeatureForecastItem.class);
        log.info("形态 [{}] 推算完成: {}", featureLabel, JSON.toJSONString(null));
        return null;
    }

    private static final String ITEM_FORMAT = """
            {
              "value": "主推值或区间",
              "alternatives": ["备选1", "备选2"],
              "confidence": 0.0,
              "reason": "点明间隔趋势与接入时机",
              "gapTrend": "heating|cooling|stable|unknown",
              "predictedGap": 0.0,
              "currentOmission": 0,
              "eta": 0,
              "dueWindow": false,
              "score": 0.0,
              "recentGaps": [3, 4, 5]
            }
            """;
}
