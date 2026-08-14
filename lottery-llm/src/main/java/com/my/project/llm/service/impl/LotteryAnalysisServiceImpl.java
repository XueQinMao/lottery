package com.my.project.llm.service.impl;

import com.alibaba.fastjson.JSON;
import com.my.project.llm.bo.FeatureForecastBo.FeatureForecastItem;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.llm.prompt.LotteryAnalysisPrompt;
import com.my.project.llm.service.ILotteryAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * LotteryAnalysisServiceImpl
 *
 * <p>按单个形态调用 LLM 推算下一期值/区间。
 *
 * @author 刘强
 * @version 2026/08/14
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
        throw new UnsupportedOperationException("直方图由 Java 统计；LLM 请使用 forecastOne");
    }

    @Override
    public FeatureForecastItem forecastOne(String featureLabel, String valueHint, String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new IllegalArgumentException("形态快照不能为空");
        }
        log.info("开始推算形态 [{}]", featureLabel);
        FeatureForecastItem result = lotteryChatClient.prompt()
            .user(u -> u.text(LotteryAnalysisPrompt.FORECAST_ONE_PROMPT)
                .param("label", featureLabel)
                .param("hint", valueHint)
                .param("snapshot", snapshotJson)
                .param("format", ITEM_FORMAT))
            .call()
            .entity(FeatureForecastItem.class);
        log.info("形态 [{}] 推算完成: {}", featureLabel, JSON.toJSONString(result));
        return result;
    }

    private static final String ITEM_FORMAT = """
            {
              "value": "主推值或区间",
              "alternatives": ["备选1", "备选2"],
              "confidence": 0.0,
              "reason": "简要依据"
            }
            """;
}
