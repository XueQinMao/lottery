package com.my.project.llm.prompt;

/**
 * LotteryAdjustPrompt
 *
 * <p>双色球号码 Prompt（两套，输出 Schema 相同）：
 * <ol>
 *   <li>{@link #USER_PROMPT}：调优模式（tickets 非空）— 逐组调整 + 组内复式 + 最终复式/单式</li>
 *   <li>{@link #RECOMMEND_PROMPT}：推荐模式（tickets 为空）— 按特征报告直接生成 N 组 + 组内复式 + 最终复式/单式</li>
 * </ol>
 *
 * <p>核心原则：特征报告约束「形态」，冷热/分区/邻狐传约束「结构」；
 * 热号仅作候选，禁止复式主体由超热胆码堆砌。
 *
 * <p>硬约束：杀号清单禁止出现；冷热温档位取自 coldHotAnalysis；
 * 红球和值 ∈ [90,130]、跨度 ∈ [16,28]（适用所有红球输出）。
 *
 * <p>软约束：三区比优先落在 predictedThreeZoneRatio.candidates 中概率较高者。
 *
 * <p>占位符：{report} / {tickets} / {count} / {format}
 *
 * @author 刘强
 * @version 2026/08/10 10:30
 **/
public final class LotteryAdjustPrompt {

    private LotteryAdjustPrompt() {
    }

    public static final String USER_PROMPT = """
            请基于以下双色球号码特征分析报告，对给出的预测号码组逐一调优，
            并为每一组生成对应复式；最后再综合输出【一组】最终可购买的复式。

            【核心原则】
            1. 特征报告用于约束形态（奇偶、大小、质合、012路、跨度、和值、三区、连号类型等），
               不得把报告 Top 胆码/热号直接当作「必出号码表」整池搬入。
            2. 热号仅作候选池；选号主体必须冷热分散、三区分散，并防范热号集体回冷。
            3. 所有替换与扩号须可核验：冷热档位、分区、连号、邻狐传均需满足硬性约束。
            4. 【杀号硬约束】若报告中存在 `killNumbers` 字段：
               - `hardKillRed` / `hardKillBlue` 中的号码为「硬杀清单」，禁止出现在任何输出号码中
                 （含 adjustedRedBalls、adjustedBlueBall、complexTicket.redBalls/blueBalls、
                  finalComplexTicket.redBalls/blueBalls、finalSingleTickets.redBalls/blueBall）；
               - 若 `killNumbers` 为 null 或各清单为空，则忽略本条约束。
            5. 【冷热温硬约束】若报告存在 `coldHotAnalysis` 字段：
               - `redHotBalls` / `redWarmBalls` / `redColdBalls` 为红球热/温/冷号清单，
                 `blueHotBalls` / `blueWarmBalls` / `blueColdBalls` 为蓝球热/温/冷号清单；
               - 须直接使用上述清单判定每个候选号码的冷热档位，**不得**再自行从频次表推断冷热；
               - 冷热配比要求见下方调优规则（热号≤上限、冷号≥下限等）；
               - 若 `coldHotAnalysis` 为 null，则按报告频次表自行估算冷热。
            6. 【红球和值与跨度硬约束】所有红球输出（adjustedRedBalls、complexTicket.redBalls、
               finalComplexTicket.redBalls、finalSingleTickets.redBalls）必须同时满足：
               - 和值（6 个红球之和）∈ [90, 130]；
               - 跨度（最大红球 − 最小红球）∈ [16, 28]。
               任一条件不满足即视为违规，须在输出前自行调整号码至合规。
            7. 【三区比预测软约束】若报告存在 `predictedThreeZoneRatio` 字段：
               - `candidates` 为下一期 Top-K 候选三区比及概率，`lastRatio` 为最近一期实际三区比；
               - 最终单式/复式的三区比应尽量落在 Top 候选之中（概率越高越优先）；
               - 不得强行追求概率最高的单一形态而违反其他硬约束（冷热/分区/和值/跨度/连号等）；
               - 若 `predictedThreeZoneRatio` 为 null，则按报告 `threeZoneRatio` 历史高频形态选号。

            【特征分析报告】
            {report}

            【待调整的预测号码组】
            {tickets}

            【调优规则】
            一、单式号码组调整（对每一组，写入 adjustedTickets）
            1. 红球比对：将 6 个红球与特征报告比对奇偶比、大小比、质合比、012路比、
               跨度、和值区间、三区比、尾数、连号、邻狐传等，并计算冷热结构：
               - 冷热档位直接取自报告 `coldHotAnalysis.redHotBalls/redWarmBalls/redColdBalls`，
                 不得自行按频次表估算；
               - 热（在 redHotBalls 中）、温（在 redWarmBalls 中）、冷（在 redColdBalls 中）。
               - 热号≥4 →「过热」（热号回冷风险高）；
               - 冷号≥3 →「过冷」（可能偏离活跃区间）；
               - 理想单式冷热：热2-3、温2-3、冷1-2。
               另检号段：一区(1-11)/二区(12-22)/三区(23-33) 不得出现「某区 0 个」或「某区≥4」；
               连号：最长连号长度≤2，且至多 1 组 2 连号（禁止 1,2,3 这类 3 连团）。
               【和值/跨度硬校验】6 红球和值须 ∈ [90,130]、跨度（最大−最小）须 ∈ [16,28]；
               超出范围时按下方替换规则调整至合规。
            2. 红球替换：红球 1-33 互异升序共 6 个。在维持合理形态前提下，按优先级处理：
               (a) 过热：将 1-2 个超热号（优先替换报告出现次数最高的号）换成温号或遗漏适中的冷号，
                   使热号降至 ≤3；basis 注明「热号回冷防御」。
               (b) 过冷：将 1-2 个极冷号换成「高频但非 Top3」的温热号，避免扎堆超热；冷号≤2；
                   basis 注明「冷号复苏平衡」。
               (c) 号段失衡：缺区则补该区温/冷号；某区过多则换出该区热号到其他区。
               (d) 连号违规：拆散 3 连及以上，或多余的 2 连组，换成同区非连续号。
               (e) 和值越界：和值<85 → 将较小号换成更大的温/冷号；和值>130 → 将较大号换成更小的温/冷号；
                   basis 注明「和值校准至85-130」。
               (f) 跨度越界：跨度<16 → 将最小号调小或最大号调大（扩大分布）；跨度>28 → 收拢两端，
                   将最小号调大或最大号调小；basis 注明「跨度校准至16-28」。
               (g) 冷热已均衡且形态无明显偏离且和值/跨度均合规时，可不替换。
               替换须给出 from、to、basis；替换后重新升序。
            3. 蓝球比对与替换：蓝球 1-16。冷热档位直接取自报告
               `coldHotAnalysis.blueHotBalls/blueWarmBalls/blueColdBalls`，不得自行按频次表估算。
               - 过热蓝球（在 blueHotBalls 中）→ 优先换为同路或邻区的温蓝球；basis 注明「蓝球冷热均衡」。
               - 极冷蓝球（在 blueColdBalls 中）→ 可换温号，但最终复式阶段仍须保留冷/温分散。
               - 若报告显示蓝球「狐」占比高，单式蓝球优先选相对上期的狐号或温号，
                 避免默认追上期邻号/重号或报告 Top1 热蓝。
            4. 若已整体合理可不做替换，但仍须输出 adjustedRedBalls 与 adjustedBlueBall。
            5. reason 简要说明本组调整（150 字内），须点明冷热、分区或连号是否触达硬约束。

            二、单组复式（每组必填 complexTicket，与单式一一对应）
            1. 每组必须生成 1 个 complexTicket，不可缺失。
            2. 基于「本组 adjustedRedBalls / adjustedBlueBall」扩展，须同时满足：
               【红球 7-10 个】
               - 须包含本组全部 6 个调整后红球，再补 1-4 个号；
               - 冷热（取自 coldHotAnalysis）：热:温:冷 ≈ 3:3:2 或 4:3:2（热号≤4，冷号≥2）；禁止补号全为超热胆码；
               - 分区：一区/二区/三区 每区至少 2 个；
               - 连号：最长连号≤2，2 连号组数≤2；禁止出现 3 连及以上；
               - 和值/跨度：6 红球子集（任取 6 个）的和值 ∈ [90,130]、跨度 ∈ [16,28]；
               - 相对报告最近一期（邻狐传）：重号≤2；至少保留 2 个明确狐号（与上期既不重复也不相邻）。
               【蓝球 2-5 个】
               - 须包含本组 adjustedBlueBall；
               - 冷热（取自 coldHotAnalysis）：热蓝≤2；至少 1 个温号 + 至少 1 个冷号（在 blueColdBalls 中，或四分区中的偏冷区）；
               - 四分区（1-4/5-8/9-12/13-16）至少覆盖 2 个不同区；不得 4 个蓝全落同一热区；
               - 至少 1 个相对上期蓝球的狐号（|差|≥2）。
            3. totalBets = C(红球个数, 6) × 蓝球个数，需准确。
            4. name、basis 说明归属哪一组及选号依据（200 字内）；basis 须写明热温冷个数与分区覆盖。

            三、最终可购买复式（必填 finalComplexTicket，全响应仅此一组）
            1. 综合特征报告 + 各组调整结果/单组复式，凝练成【唯一】一套最终购买复式；
               不要与某一组 complexTicket 简单等同。
               可吸收多组「共性温号/结构共识」，但不得把报告 Top5 红胆或 Top4 热蓝作为复式主体；
               超热号入选总数红球≤3、蓝球≤2。
            2. 红球 7-10、蓝球 2-5，互异升序，且必须同时满足：
               - 形态：奇偶、大小、三区比、和值、跨度落在报告高频或次高频区间附近；
               - 三区比预测：优先落在 `predictedThreeZoneRatio.candidates` 中概率较高的形态；
               - 和值/跨度硬约束：6 红球子集（任取 6 个）的和值 ∈ [90,130]、跨度 ∈ [16,28]；
               - 冷热（取自 coldHotAnalysis）：红球热≤4、温≥2、冷≥2；蓝球热≤2，且含≥1 温、≥1 冷；
               - 分区：红球每区≥2；蓝球覆盖≥2 个四分区，建议含三区(9-12)或四区之一作分散；
               - 连号：最长≤2，2 连组数≤2；
               - 邻狐传：红球相对上期重号≤2、狐号≥2；蓝球至少 1 个狐号。
            3. totalBets 准确；name、basis、conclusion 说明为何选这套（各 200 字内）。
               conclusion 必须说明：如何用温冷号、分区分散、限制连号与重号，
               降低「热号集体回冷 + 号段错位 + 蓝球追热」三类风险。

            【输出要求】
            - 严格输出 JSON，结构必须符合以下 Schema：
            {format}
            - 不得输出 Markdown 代码块、解释文字或推理过程，只输出纯 JSON。
            - adjustedTickets 顺序与输入 tickets 一致，id 回填输入 id（若有）。
            - 每个 AdjustedTicket 必须含 1 个 complexTicket；另有且仅有 1 个 finalComplexTicket。
            - 所有号码为整数；红球 1-33 互异升序；蓝球 1-16 互异升序。
            - 若初稿违反任一硬性约束，须在输出前自行修正至合规，不得输出违规复式。
            """;

    /**
     * 推荐模式 Prompt（tickets 为空时使用）。
     * <p>占位符：{report} / {count} / {format}
     * <p>输出 Schema 与调优模式完全一致；无原始号码，故 original* / replacements 置空。
     */
    public static final String RECOMMEND_PROMPT = """
            请基于以下双色球号码特征分析报告，直接推荐 {count} 组可购买的号码方案。
            本组任务是「从零生成」，不是对已有号码调优；请严格输出与调优模式相同的 JSON Schema。

            【核心原则】
            1. 特征报告用于约束形态（奇偶、大小、质合、012路、跨度、和值、三区、连号类型等），
               不得把报告 Top 胆码/热号直接当作「必出号码表」整池搬入。
            2. 热号仅作候选池；选号主体必须冷热分散、三区分散，并防范热号集体回冷。
            3. 所有选号须可核验：冷热档位、分区、连号、邻狐传均需满足硬性约束。
            4. 【杀号硬约束】若报告中存在 `killNumbers` 字段：
               - `hardKillRed` / `hardKillBlue` 中的号码为「硬杀清单」，禁止出现在任何输出号码中
                 （含 adjustedRedBalls、adjustedBlueBall、complexTicket.redBalls/blueBalls、
                  finalComplexTicket.redBalls/blueBalls、finalSingleTickets.redBalls/blueBall）；
               - 若 `killNumbers` 为 null 或各清单为空，则忽略本条约束。
            5. 【冷热温硬约束】若报告存在 `coldHotAnalysis` 字段：
               - `redHotBalls` / `redWarmBalls` / `redColdBalls` 为红球热/温/冷号清单，
                 `blueHotBalls` / `blueWarmBalls` / `blueColdBalls` 为蓝球热/温/冷号清单；
               - 须直接使用上述清单判定每个候选号码的冷热档位，**不得**再自行从频次表推断冷热；
               - 若 `coldHotAnalysis` 为 null，则按报告频次表自行估算冷热。
            6. 【红球和值与跨度硬约束】所有红球输出（adjustedRedBalls、complexTicket.redBalls、
               finalComplexTicket.redBalls、finalSingleTickets.redBalls）必须同时满足：
               - 和值（6 个红球之和）∈ [90, 130]；
               - 跨度（最大红球 − 最小红球）∈ [16, 28]。
               任一条件不满足即视为违规，须在输出前自行调整号码至合规。
            7. 【三区比预测软约束】若报告存在 `predictedThreeZoneRatio` 字段：
               - `candidates` 为下一期 Top-K 候选三区比及概率，`lastRatio` 为最近一期实际三区比；
               - 各组单式/复式的三区比应尽量落在 Top 候选之中（概率越高越优先）；
               - 不得强行追求概率最高的单一形态而违反其他硬约束；
               - 若 `predictedThreeZoneRatio` 为 null，则按报告 `threeZoneRatio` 历史高频形态选号。
            8. 【组间差异】{count} 组方案须互有差异（至少红球或蓝球不同），可分别侧重不同形态假设
               （如热温延续 / 温冷回补 / 分区均衡等），禁止 {count} 组完全相同。

            【特征分析报告】
            {report}

            【推荐规则】
            一、生成 {count} 组单式（写入 adjustedTickets，恰好 {count} 组）
            1. 每组输出 6 红 + 1 蓝；红球 1-33 互异升序；蓝球 1-16。
            2. 因无原始号码：originalRedBalls / originalBlueBall 置 null；
               redReplacements 置空数组 []；blueReplacement 置 null。
            3. adjustedRedBalls / adjustedBlueBall 即为本组推荐单式。
            4. 单式硬性结构：
               - 冷热（取自 coldHotAnalysis）：理想热2-3、温2-3、冷1-2；热号≤3、冷号≤2；
               - 分区：一区(1-11)/二区(12-22)/三区(23-33) 不得出现「某区 0 个」或「某区≥4」；
               - 连号：最长连号长度≤2，且至多 1 组 2 连号；
               - 和值 ∈ [90,130]、跨度 ∈ [16,28]；
               - 三区比尽量落在 predictedThreeZoneRatio.candidates 中。
            5. id 可用 "R1"/"R2"/...；reason 说明本组形态假设与选号依据（150 字内）。

            二、单组复式（每组必填 complexTicket，与单式一一对应）
            1. 每组必须生成 1 个 complexTicket，不可缺失。
            2. 基于「本组 adjustedRedBalls / adjustedBlueBall」扩展，须同时满足：
               【红球 7-10 个】
               - 须包含本组全部 6 个推荐红球，再补 1-4 个号；
               - 冷热（取自 coldHotAnalysis）：热:温:冷 ≈ 3:3:2 或 4:3:2（热号≤4，冷号≥2）；禁止补号全为超热胆码；
               - 分区：一区/二区/三区 每区至少 2 个；
               - 连号：最长连号≤2，2 连号组数≤2；禁止出现 3 连及以上；
               - 和值/跨度：6 红球子集（任取 6 个）的和值 ∈ [90,130]、跨度 ∈ [16,28]；
               - 相对报告最近一期（邻狐传）：重号≤2；至少保留 2 个明确狐号（与上期既不重复也不相邻）。
               【蓝球 2-5 个】
               - 须包含本组 adjustedBlueBall；
               - 冷热（取自 coldHotAnalysis）：热蓝≤2；至少 1 个温号 + 至少 1 个冷号；
               - 四分区（1-4/5-8/9-12/13-16）至少覆盖 2 个不同区；
               - 至少 1 个相对上期蓝球的狐号（|差|≥2）。
            3. totalBets = C(红球个数, 6) × 蓝球个数，需准确。
            4. name、basis 说明归属哪一组及选号依据（200 字内）；basis 须写明热温冷个数与分区覆盖。

            三、最终可购买复式（必填 finalComplexTicket，全响应仅此一组）
            1. 综合特征报告 + 各组推荐结果/单组复式，凝练成【唯一】一套最终购买复式；
               不要与某一组 complexTicket 简单等同；超热号入选总数红球≤3、蓝球≤2。
            2. 红球 7-10、蓝球 2-5，互异升序，且必须同时满足：
               - 形态：奇偶、大小、三区比、和值、跨度落在报告高频或次高频区间附近；
               - 三区比预测：优先落在 `predictedThreeZoneRatio.candidates` 中概率较高的形态；
               - 和值/跨度硬约束：6 红球子集（任取 6 个）的和值 ∈ [90,130]、跨度 ∈ [16,28]；
               - 冷热（取自 coldHotAnalysis）：红球热≤4、温≥2、冷≥2；蓝球热≤2，且含≥1 温、≥1 冷；
               - 分区：红球每区≥2；蓝球覆盖≥2 个四分区；
               - 连号：最长≤2，2 连组数≤2；
               - 邻狐传：红球相对上期重号≤2、狐号≥2；蓝球至少 1 个狐号。
            3. totalBets 准确；name、basis、conclusion 说明为何选这套（各 200 字内）。
               conclusion 必须说明：如何用温冷号、分区分散、限制连号与重号，
               降低「热号集体回冷 + 号段错位 + 蓝球追热」三类风险。

            四、最终可购买单式（必填 finalSingleTickets，恰好 2 组）
            1. 两组分别针对不同形态假设（如热温延续 / 温冷回冷），每组 6 红 + 1 蓝、totalBets=1。
            2. 须满足全部硬约束（杀号/冷热/和值/跨度/分区/连号）。

            【输出要求】
            - 严格输出 JSON，结构必须符合以下 Schema：
            {format}
            - 不得输出 Markdown 代码块、解释文字或推理过程，只输出纯 JSON。
            - adjustedTickets 必须恰好 {count} 组；每个 AdjustedTicket 必须含 1 个 complexTicket；
              另有且仅有 1 个 finalComplexTicket；finalSingleTickets 恰好 2 组。
            - 所有号码为整数；红球 1-33 互异升序；蓝球 1-16 互异升序。
            - 若初稿违反任一硬性约束，须在输出前自行修正至合规，不得输出违规复式。
            """;
}
