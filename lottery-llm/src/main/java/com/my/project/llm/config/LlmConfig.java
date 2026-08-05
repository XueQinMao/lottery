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
 *     <li>{@code lotteryChatClient}：单形态推算（engine=llm 时由 application 压缩候选后调用）</li>
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
            @Value("${lottery.llm.analysis.temperature:0.2}") Double analysisTemperature) {
        return ChatClient.builder(chatModel)
                .defaultOptions(DeepSeekChatOptions.builder()
                        .model(analysisModel)
                        .temperature(analysisTemperature)
                        .build())
                .defaultSystem("""
                        你是一名双色球形态推算分析师，每次只处理一个形态。
                        主推优先理论先验最高的桶；指数差值只做轻量加减分，禁止用 eta 把低频桶抬成主推。
                        你只准从 eligibleValue=true 且 forbiddenAsValue=false 的候选里选 value。
                        禁止用出现次数或 index 绝对值；热度断档、低频刚出不得主推。
                        理论众数刚出或黏性连出（clusterContinue=true）允许再主推。
                        reboundMustInclude 中的长冷回补必须进入 value 或 alternatives。
                        predictedGap/eta/dueWindow/recentGaps/gapTrend/score 必须抄候选表，禁止自造。
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
                        用户消息含特征报告（killNumbers / coldHotAnalysis / featureForecast /
                        predictedThreeZoneRatio / trendAnalysis）及分层规则：真硬必须满足，
                        结构底线尽量满足，forecast 只是先验不是答案。
                        dueWindow/confidence 不是命中率；覆盖已满的维不作过滤；
                        cooling+刚出或 eta≤-3 勿追 value；比例维一组偏均衡、一组走另一侧备选；
                        禁止 15 维硬锁、禁止所有组追逐同一套 value。
                        共同任务（两种模式输出 Schema 完全相同）：
                        1. 产出若干组单式（adjustedTickets）；每组只含单式，禁止输出 complexTicket；
                        2. 输出唯一一份 finalRecommendation：singleTickets 恰好 2 组（6红+1蓝），
                           complexTicket 恰好 1 组（红 7-10 + 蓝 2-5）；
                        3. 复式 totalBets = C(红球数,6)×蓝球数，须准确；单式 totalBets=1。
                           conclusion 写在 JSON 根上，不要写进 complexTicket。
                        模式差异：
                        - 调优：有候选号码，逐组微调；保留 original* / replacements。
                        - 推荐：无候选，按报告从零生成 N 组；original* / replacements 置空。
                        只输出纯 JSON，不得含 Markdown、解释或推理过程。
                        """)
                .build();
    }
}
