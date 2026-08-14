package com.my.project.llm.prompt;

/**
 * LotteryAnalysisPrompt
 *
 * <p>单形态推算 Prompt。快照与形态指数页同源（stats / ratioOptions）。
 *
 * @author 刘强
 * @version 2026/08/14
 **/
public final class LotteryAnalysisPrompt {

    private LotteryAnalysisPrompt() {
    }

    /**
     * 占位符：{label} / {hint} / {snapshot} / {format}
     */
    public static final String FORECAST_ONE_PROMPT = """
            请根据以下【{label}】形态指数快照，推算下一期该形态的具体值或合理区间。
            数据与「形态指数」页完全一致，不要重新统计次数，不要分析其他形态。

            【形态】{label}
            【取值说明】{hint}

            【快照】
            {snapshot}

            【字段说明】
            - lastValue：最近一期实际形态
            - stats：切到 lastValue 时页面顶部的遗漏/指数（currentOmission、index、hitCount 等）
            - ratioOptions：页面下拉中全部取值，字段与页面对应：
              hitCount、theoreticalHits、index（实际次数 − 理论次数）、currentOmission、avgOmission、maxOmission
            - index 正=偏热，负=偏冷；currentOmission=0 表示上期刚出
            - 质合比按走势图口径：01 计为质数
            - 蓝球大小：1-8 为小、9-16 为大；012路按 n%3（0路 5 个、1路 6 个、2路 5 个）

            【推算原则】
            1. 不要机械取历史最高频；结合指数冷热、当前遗漏、上期取值做均衡判断。
            2. 偏热且刚出（currentOmission=0、index 明显为正）可考虑回落至次高频或温和区间。
            3. 偏冷且遗漏偏大（index 为负、currentOmission 较大）可考虑回补。
            4. value 必须符合上方取值说明，且应是 ratioOptions 中的 ratio 或由其合并的闭区间；
               alternatives 给 1-3 个备选。
            5. 禁止给出不可能组合（红球比例左右之和不为 6；蓝球取值必须是 奇/偶、大/小、
               小奇/小偶/大奇/大偶 或 0路/1路/2路 之一）。

            【输出要求】
            - 严格输出 JSON，结构必须符合：
            {format}
            - 不得输出 Markdown、解释文字或推理过程，只输出纯 JSON。
            """;
}
