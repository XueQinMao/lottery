package com.my.project.llm.prompt;

/**
 * LotteryAnalysisPrompt
 *
 * <p>单形态大模型推算 Prompt。候选表由 application 层 compactForLlm 压缩，
 * 主信号是 indexValues 前后期差值（收缩=heating，扩张=cooling，平稳按 eta 择时）。
 *
 * @author 刘强
 * @version 2026/08/19
 **/
public final class LotteryAnalysisPrompt {

    private LotteryAnalysisPrompt() {
    }

    public static final String FORECAST_ONE_FORMAT = """
            {
              "value": "主推值或区间",
              "alternatives": ["备选1", "备选2"],
              "confidence": 0.0,
              "reason": "点明指数差值趋势与接入时机",
              "gapTrend": "heating|cooling|stable|unknown",
              "predictedGap": 0.0,
              "currentOmission": 0,
              "eta": 0,
              "dueWindow": false,
              "score": 0.0,
              "recentGaps": [3, 4, 5]
            }
            """;

    /**
     * 占位符：{label} / {hint} / {snapshot} / {format}
     */
    public static final String FORECAST_ONE_PROMPT = """
            请根据以下【{label}】Java 候选表，推算下一期该形态的具体值或合理区间。
            不要重新统计次数，不要分析其他形态，不要发明候选表里没有的数字。

            【形态】{label}
            【取值说明】{hint}

            【候选表】
            {snapshot}

            【硬约束——违反则答案无效】
            1. value 必须是 candidates 里 eligibleValue=true 且 forbiddenAsValue=false 的 ratio。
            2. 禁止把 forbiddenAsValue 列表中的取值当作 value（热度断档 heatBroken；低频刚出）。
               例外：clusterContinue=true 的黏性桶（均漏短、近间隔1-2）允许刚出再主推。
            3. 主信号是 indexValues 前后期差值：收缩(heating)=未来倾向命中；扩张(cooling)=开出概率低；
               差值或命中间隔平稳(stable)时按 predictedGap/eta 确定介入时机。禁止用 hitCount、index 绝对值决定主推。
            4. reboundMustInclude 非空时：这些长冷回补桶必须出现在 value 或 alternatives 中。
            5. 优先 dueWindow=true 或 reboundWindow=true 或 indexDiffTrend=heating；cooling 不得主推（除非 reboundWindow）。
            6. predictedGap、eta、dueWindow、recentGaps、gapTrend、score 必须原样抄自所选候选，禁止自造。
            7. alternatives 1-2 个，必须同样来自 candidates 且不是 value。
            8. 红球三元比三项之和必须为 6；仅跨度/和尾/区个数允许相邻闭区间。

            【输出要求】
            - 严格输出 JSON，结构必须符合：
            {format}
            - 不得输出 Markdown、解释文字或推理过程，只输出纯 JSON。
            """;
}
