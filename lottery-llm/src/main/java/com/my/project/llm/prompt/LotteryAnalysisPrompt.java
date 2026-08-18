package com.my.project.llm.prompt;

/**
 * LotteryAnalysisPrompt
 *
 * <p>单形态推算 Prompt。快照与形态指数页同源：各分桶遗漏序列 / 指数序列 / 命中间隔。
 *
 * @author 刘强
 * @version 2026/08/18
 **/
public final class LotteryAnalysisPrompt {

    private LotteryAnalysisPrompt() {
    }

    /**
     * 占位符：{label} / {hint} / {snapshot} / {format}
     */
    public static final String FORECAST_ONE_PROMPT = """
            请根据以下【{label}】形态指数趋势快照，推算下一期该形态的具体值或合理区间。
            数据与「形态指数」页同源。不要重新统计次数，不要分析其他形态。

            【形态】{label}
            【取值说明】{hint}

            【快照】
            {snapshot}

            【字段说明】
            - periods / actuals：每期期号与该期实际形态（最旧到最新）
            - lastValue：最近一期实际形态
            - ratioOptions：该形态全部取值（如奇偶比含 0:6、1:5、2:4、3:3、4:2、5:1、6:0），每项含：
              hitCount、theoreticalProb、index（实际次数减理论次数）、currentOmission、avgOmission、maxOmission、
              omissions（该取值每期遗漏序列，命中当期为 0）、
              indexValues（该取值超额指数走势，上升=趋热，下降=趋冷）、
              hitIntervals（相邻两次命中的间隔，单位期；由旧到新）
            - isLast=true 表示上期刚开出该取值

            【推算原则】
            1. 看 hitIntervals：间隔越来越大 → 走冷，下一期降低权重、倾向杀掉；
               间隔越来越小 → 走热，下一期提高开出可能。
            2. 结合 omissions 末端（当前遗漏）与 hitIntervals 近期均值，估算下次接入时机：
               当前遗漏接近或刚过近期平均间隔 → 窗口内；远小于平均间隔 → 还早；远超且走冷 → 不要强行回补。
            3. indexValues 近期上行=趋热，下行=趋冷，与间隔节奏互相印证。
            4. 刚出（isLast 或 currentOmission=0）周期刚复位，不宜立刻再主推同值。
            5. value 必须是 ratioOptions 中的 ratio，或相邻取值合并的闭区间（仅跨度/和尾/区个数）。
            6. 禁止不可能组合（红球比例之和不为 6；蓝球须为 奇/偶、大/小、小奇/小偶/大奇/大偶、0路/1路/2路）。
            7. alternatives 给 1-3 个备选；reason 须点明间隔扩张/收缩与预计接入时机。

            【输出要求】
            - 严格输出 JSON，结构必须符合：
            {format}
            - 不得输出 Markdown、解释文字或推理过程，只输出纯 JSON。
            """;
}
