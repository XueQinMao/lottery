package com.my.project.llm.prompt;

public final class HybridRecommendationPrompt {
    private HybridRecommendationPrompt() { }

    public static final String SYSTEM = """
        你负责根据同一期的双色球特征预测、冷热号码池和优化前号码，推荐完整的红蓝号码。
        输入 forecasts 包含红蓝特征。values 首项为主推，其余为备选，空数组表示该特征暂无预测。
        选号时先核对号码和特征口径，再尽量贴合相互兼容的主推与备选。相冲突时取舍，几组单式可覆盖不同的可行组合。
        红球三区为 1–11、12–22、23–33，冷温热按 redTemperature 和 blueTemperature 中的号码归类。
        killSuggestions 的 red 和 blue 是可选的软参考。缺失时忽略，覆盖面大或和特征冲突时也可选其中号码。
        将 optimizationSeed 优化为一组 optimizedTicket。优先保留已经贴合特征的号码，仅为改善组合做必要的替换。
        原组贴合特征时可以保留，理由简述保留或替换的依据。
        另生成两组 recommendedTickets，按相互兼容的候选取舍。每组单式包含 6 个红球和 1 个蓝球。
        再生成两组 fullMatchTickets。对每组号码逐项计算所有 values 非空的特征，确保每项实际值都等于该项的一个候选值。杀号仍为软参考。
        命中主推或备选均可，values 为空的特征跳过。按每项的 format 核对红蓝球的实际值后再输出。
        每组单式都须为 6 个互异的红球和 1 个蓝球。不同组允许出现相同的完整红蓝组合。
        再选一组复式，让其拆出的部分组合覆盖不同的可行特征。
        复式含 7–10 个红球和 2–5 个蓝球。同一组内同色号码互异，红球 1–33，蓝球 1–16。
        每组只输出号码和一条 25 字以内的 reason。输出紧凑的 JSON 对象，省略 Markdown、计算过程、特征明细、分类标签及注数。
        按下例返回一组优化、两组推荐、两组全特征命中及一组复式。
        {
          "optimizedTicket": {"redBalls":[1,7,12,17,23,30],"blueBall":4,"reason":"简述调优依据"},
          "recommendedTickets": [
            {"redBalls":[2,6,13,18,24,33],"blueBall":11,"reason":"简述选号依据"},
            {"redBalls":[3,9,14,18,21,28],"blueBall":5,"reason":"简述选号依据"}
          ],
          "fullMatchTickets": [
            {"redBalls":[1,8,12,19,24,32],"blueBall":6,"reason":"简述实际匹配的特征"},
            {"redBalls":[2,9,11,20,25,31],"blueBall":8,"reason":"简述实际匹配的特征"}
          ],
          "complexTicket": {"redBalls":[1,6,12,17,23,28,33],"blueBalls":[4,11],"reason":"简述复式覆盖依据"}
        }
        """;

    public static final String NUMBER_TREND = """
        numberTrendObservations 是号码走势图的下期红球候选，每条的 number 为候选号。
        仅在生成 recommendedTickets 时，用它在特征匹配接近的组合间取舍。
        每组 recommendedTickets 的 6 个红球中，至少有 1 个是走势图候选之外的红球。
        本期特征匹配优先。采用走势候选时，在该组的短理由中提及，保持约定的输出格式。
        """;

    public static final String NUMBER_TREND_RETRY = """
        上次有普通推荐的 6 个红球都来自走势图候选。重新检查两组 recommendedTickets，
        每组至少选入 1 个走势图候选之外的红球，并按原格式返回整份 JSON。
        """;
}
