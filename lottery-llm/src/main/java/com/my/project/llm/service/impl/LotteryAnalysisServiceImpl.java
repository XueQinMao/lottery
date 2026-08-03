package com.my.project.llm.service.impl;

import com.alibaba.fastjson.JSON;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.llm.prompt.LotteryAnalysisPrompt;
import com.my.project.llm.service.ILotteryAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

/**
 * LotteryAnalysisServiceImpl
 *
 * <p>调用 Spring AI Alibaba ChatClient（DashScope 后端，模型为 deepseek-v3 / deepseek-r1）
 * 对最近 100 组一等奖号码进行 14 维度特征分析。
 *
 * <p>采用结构化输出（Structured Output）方式，将大模型返回的 JSON 直接反序列化为
 * {@link LotteryAnalysisRespBo}，避免手工解析。
 *
 * @author 刘强
 * @version 2026/07/21 20:30
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
        if (reqBo == null || reqBo.getRecords() == null || reqBo.getRecords().isEmpty()) {
            throw new IllegalArgumentException("分析样本不能为空");
        }
        log.info("开始调用 DeepSeek 进行号码特征分析，样本数: {}",reqBo.getRecords().size());

        String recordsJson = JSON.toJSONString(reqBo.getRecords());

        LotteryAnalysisRespBo result = lotteryChatClient.prompt()
                .user(u -> u.text(LotteryAnalysisPrompt.USER_PROMPT)
                        .param("records", recordsJson)
                        .param("format", FORMAT_HINT))
                .call()
                .entity(LotteryAnalysisRespBo.class);

        log.info("DeepSeek 号码特征分析完成:{}", JSON.toJSONString(result));
        return result;
    }

    /**
     * 给大模型的结构提示。Spring AI 的 entity() 已会注入完整 JSON Schema，
     * 这里补充语义说明，便于模型理解字段含义。
     */
    private static final String FORMAT_HINT = """
            {
              "sampleOverview": { "totalCount": int, "avgSum": double, "avgSpan": double, "avgOddEven": "x:y", "avgBigSmall": "x:y" },
              "oddEvenRatio": { "比例": 次数 },
              "bigSmallRatio": { "比例": 次数 },
              "primeCompositeRatio": { "比例": 次数 },
              "ratio012": { "比例": 次数 },
              "span": { "跨度值": 次数 },
              "sumRange": { "区间": 次数 },
              "sumDigit": { "位数": 次数 },
              "threeZoneRatio": { "比例": 次数 },
              "zone1Count": { "个数": 次数 },
              "zone2Count": { "个数": 次数 },
              "zone3Count": { "个数": 次数 },
              "banker": {
                "oneBanker": [ { "balls": "号码", "count": int, "frequency": double } ],
                "twoBanker": [ { "balls": "号码1,号码2", "count": int, "frequency": double } ],
                "threeBanker": [ { "balls": "号码1,号码2,号码3", "count": int, "frequency": double } ]
              },
              "tail": {
                "tailValue": { "尾数0-9": 次数 },
                "sameTailGroupCount": { "组数0-3": 次数 },
                "threeD": {
                  "onesDigit": { "个位尾数": 次数 },
                  "tensDigit": { "十位尾数": 次数 },
                  "hundredsDigit": { "百位尾数": 次数 }
                }
              },
              "consecutive": {
                "consecutiveType": { "类型": 次数 },
                "hotConsecutive": { "连号组合": 次数 }
              },
              "neighborFoxTransmit": {
                "neighborFoxTransmitRatio": { "邻:狐:传比": 次数 },
                "neighborCount": { "邻号个数0-6": 次数 },
                "foxCount": { "狐号个数0-6": 次数 },
                "repeatCount": { "重号个数0-6": 次数 }
              },
              "blue": {
                "oddEvenRatio": { "奇/偶": 次数 },
                "bigSmallRatio": { "大/小": 次数 },
                "primeCompositeRatio": { "质/合": 次数 },
                "ratio012": { "0/1/2路": 次数 },
                "tailValue": { "尾数0-6": 次数 },
                "tailBigSmall": { "尾数大/小": 次数 },
                "tailOddEven": { "尾数奇/偶": 次数 },
                "tailRatio012": { "尾数0/1/2路": 次数 },
                "fourZone": { "一区/二区/三区/四区": 次数 },
                "neighborFoxTransmit": {
                  "neighborFoxTransmitRatio": { "邻/狐/传": 次数 },
                  "neighborCount": int,
                  "foxCount": int,
                  "repeatCount": int
                },
                "anyN": {
                  "any1": [ { "balls": "号码", "count": int, "frequency": double } ],
                  "any2": [ { "balls": "号码1,号码2", "count": int, "frequency": double } ],
                  "any3": [ { "balls": "号码1,号码2,号码3", "count": int, "frequency": double } ],
                  "any4": [ { "balls": "号码1,号码2,号码3,号码4", "count": int, "frequency": double } ],
                  "any5": [ { "balls": "号码1,号码2,号码3,号码4,号码5", "count": int, "frequency": double } ]
                }
              },
              "conclusion": "综合结论与选号建议(300字内)"
            }
            """;
}
