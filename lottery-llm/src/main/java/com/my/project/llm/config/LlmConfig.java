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
                        每次任务只针对用户指定的【一个】形态（奇偶比、大小比、质合比、012路比、
                        跨度、和值区间、和值尾数、三区比、一区个数、二区个数或三区个数）。
                        Java 已完成该形态的直方图、超额指数与遗漏统计；你不要重新计数，
                        只推算下一期该形态的具体值或合理区间。
                        连号、邻狐传、蓝球、胆码以及其他形态不在本次职责范围内。
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
                        以及 LLM 推算的 featureForecast 形态目标）；用户消息会明确本次是调优还是推荐。
                        共同任务（两种模式输出 Schema 完全相同）：
                        1. 产出若干组单式结果（adjustedTickets），并为【每一组】生成对应复式
                           complexTicket（红球 7-10 + 蓝球 2-5，须包含该组单式全部红蓝球再扩展）；
                        2. 综合特征报告与全部组结果，输出【唯一一组】最终可购买复式
                           finalComplexTicket（红球 7-10 + 蓝球 2-5）；
                        3. 再输出恰好 2 组最终可购买单式 finalSingleTickets（每组 6 红 + 1 蓝）；
                        4. 各组与最终复式的 totalBets = C(红球数,6)×蓝球数，须准确。
                        模式差异：
                        - 调优：有候选预测号码，逐组比对微调；保留 original* / replacements。
                        - 推荐：无候选号码，按特征报告从零生成 N 组；original* / replacements 置空。
                        输出必须严格遵循用户给定的 JSON Schema，不得包含任何额外说明文字、
                        Markdown 代码块标记或推理过程，只输出纯 JSON。
                        """)
                .build();
    }
}
