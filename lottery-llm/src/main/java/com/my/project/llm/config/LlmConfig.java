package com.my.project.llm.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LlmConfig
 *
 * <p>基于 Spring AI {@code DeepSeekChatModel}（直连 api.deepseek.com）构建 ChatClient。
 * 调优/推荐沿用 {@code spring.ai.deepseek.chat.options.model}（思考模型）；
 * 特征分析单独覆盖为 {@code lottery.llm.analysis.model}（非思考模型），
 * 避免 reasoning 把输出额度吃光导致 JSON 截断。
 *
 * <p>提供两个 ChatClient：
 * <ul>
 *     <li>{@code lotteryChatClient}：根据 Java 形态快照推算下一期值/区间</li>
 *     <li>{@code lotteryAdjustChatClient}：调优（有候选）或推荐（无候选），输出 Schema 相同</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/08/13
 **/
@Configuration
public class LlmConfig {

    @Bean
    public ChatClient lotteryChatClient(
            ChatModel chatModel,
            @Value("${lottery.llm.analysis.model:deepseek-chat}") String analysisModel,
            @Value("${lottery.llm.analysis.temperature:0.9}") Double analysisTemperature) {
        return ChatClient.builder(chatModel)
                .defaultOptions(DeepSeekChatOptions.builder()
                        .model(analysisModel)
                        .temperature(analysisTemperature)
                        .build())
                .defaultSystem("""
                        你是一名资深的双色球形态推算分析师。
                        每次任务只针对用户指定的【一个】形态（红球或蓝球）。
                        Java 已给出该形态全部分桶的遗漏序列 omissions、指数走势 indexValues、
                        以及相邻命中间隔 hitIntervals，与形态指数页同源。
                        你不要重新计数。间隔越来越大视为走冷应降权，越来越小视为走热；
                        并结合当前遗漏估算下次开出时机。刚出的取值不要立刻再主推。
                        输出必须严格遵循用户给定的 JSON Schema，不得包含任何额外说明文字、
                        Markdown 代码块标记或推理过程，只输出纯 JSON。
                        """)
                .build();
    }

    @Bean
    public ChatClient lotteryAdjustChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是一名资深的双色球选号顾问，同时支持「调优」与「推荐」两种模式。
                        你会收到一份历史一等奖特征报告（含 Java 统计的连号/邻狐传/蓝球，
                        以及 featureForecast 形态目标，默认由 Java 间隔评分产出）；用户消息会明确本次是调优还是推荐。
                        共同任务（两种模式输出 Schema 完全相同）：
                        1. 产出若干组单式结果（adjustedTickets）；每组只含单式，禁止输出 complexTicket；
                        2. 综合特征报告与全部组结果，输出【唯一一份】最终推荐包
                           finalRecommendation，必须包含：
                           - danBalls：恰好 3 个胆码（红球）；
                           - singleTickets：恰好 2 组最终可购买单式（每组 6 红 + 1 蓝）；
                           - complexTicket：恰好 1 组最终可购买复式（红球 7-10 + 蓝球 2-5）；
                           两组单式与复式红球均须包含全部 3 个胆码；
                        3. 最终复式的 totalBets = C(红球数,6)×蓝球数，须准确；单式 totalBets=1。
                        模式差异：
                        - 调优：有候选预测号码，逐组比对微调；保留 original* / replacements。
                        - 推荐：无候选号码，按特征报告从零生成 N 组；original* / replacements 置空。
                        输出必须严格遵循用户给定的 JSON Schema，不得包含任何额外说明文字、
                        Markdown 代码块标记或推理过程，只输出纯 JSON。
                        """)
                .build();
    }
}
