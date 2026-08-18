package com.my.project.llm.prompt;

/**
 * LotteryAdjustPrompt
 *
 * <p>双色球号码 Prompt（两套，输出 Schema 相同）：
 * <ol>
 *   <li>{@link #USER_PROMPT}：调优模式（tickets 非空）— 逐组单式调整 + 最终推荐包</li>
 *   <li>{@link #RECOMMEND_PROMPT}：推荐模式（tickets 为空）— 按特征报告直接生成 N 组单式 + 最终推荐包</li>
 * </ol>
 *
 * <p>核心原则：`featureForecast` 约束「下一期形态目标」（Java 间隔快照 + LLM 逐维推算），
 * 冷热/分区/邻狐传约束「结构」；热号仅作候选，禁止复式主体由超热号堆砌。
 * 最终推荐包含：3 胆码 + 2 组单式 + 1 组复式（红 7-10 / 蓝 2-5）。
 * 报告不含历史直方图，形态一律以 featureForecast 为准。
 *
 * <p>硬约束：杀号清单禁止出现；冷热温档位取自 coldHotAnalysis；
 * 形态：dueWindow=true 且 confidence≥0.5 时优先落入 value；低置信度仅软参考；
 * 缺失则红球和值 ∈ [90,130]、跨度 ∈ [16,28]；
 * 红球奇数个数 ≤ 5、质数个数 ≤ 4
 * （适用所有红球输出；选号质数取 2,3,5,7,11,13,17,19,23,29,31，1 视为合数）。
 *
 * <p>软约束：三区比优先落在 predictedThreeZoneRatio.candidates 中概率较高者。
 *
 * <p>占位符：{report} / {tickets} / {count} / {format}
 *
 * @author 刘强
 * @version 2026/08/17
 **/
public final class LotteryAdjustPrompt {

    private LotteryAdjustPrompt() {
    }

    public static final String USER_PROMPT = """
            请基于以下双色球号码特征分析报告，对给出的预测号码组逐一调优（仅单式）；
            最后综合输出【一组】最终可购买方案：3 胆码 + 2 组单式 + 1 组复式。
            注意：各组 AdjustedTicket 不要输出 complexTicket。

            【核心原则】
            1. 下一期形态以报告 `featureForecast` 为准（间隔走冷/走热 + LLM；value 为主推）。
               dueWindow=true 且 confidence≥0.5 时优先落入；低置信度仅软参考。
               报告无历史直方图，不得臆造「高频号表」整池搬入。
            2. 热号仅作候选池；选号主体必须冷热分散、三区分散，并防范热号集体回冷。
            3. 所有替换与扩号须可核验：冷热档位、分区、连号、邻狐传均需满足硬性约束。
            4. 【杀号硬约束】若报告中存在 `killNumbers` 字段：
               - `hardKillRed` / `hardKillBlue` 中的号码为「硬杀清单」，禁止出现在任何输出号码中
                 （含 adjustedRedBalls、adjustedBlueBall、
                  finalRecommendation.danBalls、
                  finalRecommendation.singleTickets.redBalls/blueBall、
                  finalRecommendation.complexTicket.redBalls/blueBalls）；
               - 若 `killNumbers` 为 null 或各清单为空，则忽略本条约束。
            5. 【冷热温硬约束】若报告存在 `coldHotAnalysis` 字段：
               - `redHotBalls` / `redWarmBalls` / `redColdBalls` 为红球热/温/冷号清单，
                 `blueHotBalls` / `blueWarmBalls` / `blueColdBalls` 为蓝球热/温/冷号清单；
               - 须直接使用上述清单判定每个候选号码的冷热档位，**不得**再自行推断冷热；
               - 冷热配比要求见下方调优规则（热号≤上限、冷号≥下限等）；
               - 若 `coldHotAnalysis` 为 null，则冷热约束降为软参考，仍须满足杀号与形态硬约束。
            6. 【红球和值与跨度约束】所有红球输出：
               - 若 `featureForecast.span` / `sumRange` 的 dueWindow=true 且 confidence≥0.5：
                 跨度、和值须落入其 value（或 alternatives）；
               - 否则仅软参考；缺失时回退：和值 ∈ [90, 130]、跨度 ∈ [16, 28]。
               回退区间不满足时须在输出前自行调整号码至合规。
            7. 【红球奇偶与质合上限硬约束】所有红球输出（adjustedRedBalls、
               finalRecommendation.singleTickets.redBalls、
               finalRecommendation.complexTicket.redBalls）必须同时满足：
               - 奇数个数 ≤ 5（禁止 6 个红球全为奇数，即禁止 6:0 奇偶比）；
               - 质数个数 ≤ 4（质数取 2,3,5,7,11,13,17,19,23,29,31，1 视为合数；禁止 ≥5 个质数）。
               任一条件不满足即视为违规，须在输出前自行调整号码至合规。
            8. 【形态推算约束】报告必有 `featureForecast`（间隔快照 + LLM 推算，含 gapTrend/eta）：
               - 各维 `value` 为主推；`alternatives` 为备选；
               - `dueWindow=true` 且 `confidence≥0.5`：单式须落入 value 或 alternatives（硬优先）；
               - `dueWindow=false` 或 `confidence<0.5`：仅软参考，允许偏离，不得为迎合该维而整组重调；
               - `gapTrend=cooling` 且 eta 很大：勿强行追该形态；`gapTrend=heating` 且 dueWindow：可提高权重；
               - 三区比必须与 zone1Count / zone2Count / zone3Count 尽量自洽；
               - 蓝球：`blueBigSmallOddEven` 优先，`blueOddEven`/`blueBigSmall` 应与其对齐；
                 口径：1-8 小、9-16 大；奇偶按 n%2；大小奇偶为 小奇/小偶/大奇/大偶；
                 012路按 n%3（0路=3,6,9,12,15；1路=1,4,7,10,13,16；2路=2,5,8,11,14）。
            9. 【三区比预测软约束】若报告存在 `predictedThreeZoneRatio` 字段：
               - `candidates` 为下一期 Top-K 候选三区比及概率，`lastRatio` 为最近一期实际三区比；
               - 最终单式/复式的三区比应尽量落在 Top 候选之中（概率越高越优先）；
               - 不得强行追求概率最高的单一形态而违反其他硬约束（冷热/分区/和值/跨度/连号等）；
               - 若与 `featureForecast.threeZone` 冲突，以 featureForecast 为准；
               - 若 `predictedThreeZoneRatio` 为 null，则以 `featureForecast.threeZone` 为准。
            10. 【趋势均线补充约束】若报告存在 `trendAnalysis` 字段（堆叠 + MA5 斜率相位）：
               - `reboundingRedBalls` / `reboundingBlueBalls`：空头或交叉但斜率向上（回暖），
                 补号时**最优先**，禁止因「空头」回避；
               - `risingRedBalls` / `risingBlueBalls`：多头且斜率未下行，次优先；
               - `coolingRedBalls` / `coolingBlueBalls`：多头但斜率下行，谨慎；
               - `fallingRedBalls` / `fallingBlueBalls`：空头且斜率未上行（真趋冷），可降权但勿整池硬删；
               - 补号优先级：回暖 > 上升 > 平稳/未入榜 > 转弱 > 真趋冷；
               - 不得违反杀号/冷热/和值/跨度等硬约束，趋势仅作补号倾向；
               - 若 `trendAnalysis` 为 null，则忽略本条约束。
            11. 【极端整组重调】默认以局部替换为主；但若本组原号码陷入极端不合规，
               **允许将红球 6 个与蓝球全部替换**，等价于按报告从零重选一组单式。
               极端情形包括但不限于：
               - 原红球中 ≥3 个落入 `hardKillRed`，或蓝球落入 `hardKillBlue` 且难以单点替换；
               - 过热+过冷+号段失衡+连号违规+和值/跨度越界+奇偶/质合越界等多条硬约束同时严重冲突，
                 局部换 1-2 个号仍无法同时满足全部硬约束；
               - 原组与报告主导形态严重背离，局部微调无法拉回合规区间。
               整组重调时：redReplacements 须覆盖所有被换掉的原红球（from→to），
               blueReplacement 须写出原蓝→新蓝；reason / basis 须明确注明「极端整组重调」及触发原因。
               重调后的号码仍须满足全部硬约束（杀号/冷热/和值/跨度/奇偶/质合/分区/连号等）。

            【特征分析报告】
            {report}

            【待调整的预测号码组】
            {tickets}

            【调优规则】
            一、单式号码组调整（对每一组，写入 adjustedTickets；禁止输出 complexTicket）
            1. 红球比对：先按 `featureForecast` 校验奇偶/大小/质合/012路/跨度/和值/和尾/三区/区个数，
               再比对连号、邻狐传，并计算冷热结构：
               - 冷热档位直接取自报告 `coldHotAnalysis.redHotBalls/redWarmBalls/redColdBalls`；
               - 热（在 redHotBalls 中）、温（在 redWarmBalls 中）、冷（在 redColdBalls 中）。
               - 热号≥4 →「过热」（热号回冷风险高）；
               - 冷号≥3 →「过冷」（可能偏离活跃区间）；
               - 理想单式冷热：热2-3、温2-3、冷1-2。
               另检号段：一区(1-11)/二区(12-22)/三区(23-33) 不得出现「某区 0 个」或「某区≥4」；
               连号：最长连号长度≤2，且至多 1 组 2 连号（禁止 1,2,3 这类 3 连团）。
               【形态校验】dueWindow 且高置信维须落入 value/alternatives；低置信仅软参考（含蓝球）；
               【和值/跨度校验】优先 featureForecast.sumRange / span（同上置信规则），缺失时和值 ∈ [90,130]、跨度 ∈ [16,28]；
               【奇偶/质合硬校验】6 红球奇数个数须 ≤ 5、质数个数须 ≤ 4
               （质数取 2,3,5,7,11,13,17,19,23,29,31，1 视为合数）；
               超出范围时按下方替换规则调整至合规。
            2. 红球替换：红球 1-33 互异升序共 6 个。在维持合理形态前提下，按优先级处理：
               (a) 过热：将 1-2 个超热号换成温号或遗漏适中的冷号，使热号降至 ≤3；
                   basis 注明「热号回冷防御」。
               (b) 过冷：将 1-2 个极冷号换成温热号，避免扎堆超热；冷号≤2；
                   basis 注明「冷号复苏平衡」。
               (c) 号段失衡：缺区则补该区温/冷号；某区过多则换出该区热号到其他区。
               (d) 连号违规：拆散 3 连及以上，或多余的 2 连组，换成同区非连续号。
               (e) 和值越界：和值<85 → 将较小号换成更大的温/冷号；和值>130 → 将较大号换成更小的温/冷号；
                   basis 注明「和值校准至85-130」。
               (f) 跨度越界：跨度<16 → 将最小号调小或最大号调大（扩大分布）；跨度>28 → 收拢两端，
                   将最小号调大或最大号调小；basis 注明「跨度校准至16-28」。
               (g) 奇偶越界：奇数个数=6 → 将其中 1 个奇号换成同区/邻区的偶号（优先换最热或连号中的奇号），
                   使奇数个数降至 ≤5；basis 注明「奇偶校准至≤5奇」。
               (h) 质合越界：质数个数≥5 → 将其中 1-2 个质数换成同区/邻区的合数
                   （合数即非质数，含 1）；basis 注明「质合校准至≤4质」。
               (i) 冷热已均衡且形态无明显偏离且和值/跨度/奇偶/质合均合规时，可不替换。
               (j) 【极端整组重调】若局部替换（通常 1-2 个）仍无法同时满足杀号/冷热/分区/连号/
                   和值/跨度/奇偶/质合等硬约束，允许一次性替换全部 6 个红球（可同时替换蓝球），
                   按报告约束重新生成合规单式；优先从 `reboundingRedBalls` / `risingRedBalls` 与温号池选取，
                   严禁选用 hardKill 清单号码。basis 必须写明「极端整组重调」及具体触发原因。
               替换须给出 from、to、basis；替换后重新升序。
               极端整组重调时 redReplacements 应包含全部 6 组 from→to（原号→新号）。
            3. 蓝球比对与替换：蓝球 1-16。冷热档位直接取自报告
               `coldHotAnalysis.blueHotBalls/blueWarmBalls/blueColdBalls`。
               - 【蓝球形态校验】优先 `blueBigSmallOddEven`，再对齐奇偶/大小；
                 dueWindow 且高置信时落入 value/alternatives，否则软参考；
                 与杀号冲突时优先杀号，其次形态，再次冷热。
               - 过热蓝球（在 blueHotBalls 中）→ 优先换为同路或邻区的温蓝球；basis 注明「蓝球冷热均衡」。
               - 极冷蓝球（在 blueColdBalls 中）→ 可换温号，但最终复式阶段仍须保留冷/温分散。
               - 单式蓝球优先选相对上期的狐号或温号，避免默认追上期邻号/重号或超热蓝。
               - 若触发红球极端整组重调，或蓝球本身落入 hardKillBlue / 与红球硬约束冲突无法单点修复，
                 允许同时更换蓝球；优先选回暖/上升或温号池中的非杀号蓝球。
            4. 若已整体合理可不做替换，但仍须输出 adjustedRedBalls 与 adjustedBlueBall。
               仅在上述极端情形下才整组全换；非极端时仍优先局部替换，避免无必要大改。
            5. reason 简要说明本组调整（150 字内），须点明冷热、分区或连号是否触达硬约束；
               若发生极端整组重调，须在 reason 首句写明「极端整组重调」。

            二、最终推荐包（必填 finalRecommendation，全响应仅此一份）
            综合特征报告 + 各组调整后的单式，凝练成最终可购买方案，包含三部分：

            （1）三个胆码 danBalls（恰好 3 个红球，升序互异）
            - 从各组调整结果的共性温号/结构共识中提炼，禁止全选超热号；
            - 超热号在胆码中至多 1 个；优先温号 + 回暖/上升号；
            - 三胆应尽量覆盖不同分区（一/二/三区），避免同区扎堆；
            - danBasis 说明为何选这三胆（150 字内）。

            （2）两组单式 singleTickets（恰好 2 组，每组 6 红 + 1 蓝、totalBets=1）
            - 两组分别针对不同形态假设（如热温延续 / 温冷回冷）；
            - 【胆码硬约束】每组红球必须包含全部 3 个 danBalls，再补 3 个拖码；
            - 须满足全部硬约束（杀号/冷热/和值/跨度/奇偶/质合/分区/连号）；
            - name、basis 说明形态假设与选号依据（200 字内）。

            （3）一组复式 complexTicket（红球 7-10、蓝球 2-5，全响应仅此一组）
            - 【胆码硬约束】复式红球必须包含全部 3 个 danBalls；
            - 不要与某一组 adjustedTickets 简单等同；可吸收多组共性结构；
              超热号入选总数红球≤3、蓝球≤2。
            - 红球 7-10、蓝球 2-5，互异升序，且必须同时满足：
              - 形态：须落入 `featureForecast`（红球奇偶/大小/质合/012路/跨度/和值/和尾/三区/区个数，
                以及蓝球奇偶/大小/大小奇偶/012路）；
              - 三区比预测：与 featureForecast.threeZone 一致时优先；否则参考 `predictedThreeZoneRatio.candidates`；
              - 和值/跨度硬约束：优先 featureForecast；缺失时 6 红球子集和值 ∈ [90,130]、跨度 ∈ [16,28]；
              - 奇偶/质合硬约束：6 红球子集（任取 6 个）的奇数个数 ≤ 5、质数个数 ≤ 4
                （质数取 2,3,5,7,11,13,17,19,23,29,31，1 视为合数）；
              - 冷热（取自 coldHotAnalysis）：红球热≤4、温≥2、冷≥2；蓝球热≤2，且含≥1 温、≥1 冷；
              - 分区：红球每区≥2；蓝球覆盖≥2 个四分区，建议含三区(9-12)或四区之一作分散；
              - 连号：最长≤2，2 连组数≤2；
              - 邻狐传：红球相对上期重号≤2、狐号≥2；蓝球至少 1 个狐号。
            - totalBets = C(红球个数, 6) × 蓝球个数，需准确；
              name、basis、conclusion 说明为何选这套（各 200 字内）。
              conclusion 必须说明：如何用胆码锚定、温冷号分散、限制连号与重号，
              降低「热号集体回冷 + 号段错位 + 蓝球追热」三类风险。

            【输出要求】
            - 严格输出 JSON，结构必须符合以下 Schema：
            {format}
            - 不得输出 Markdown 代码块、解释文字或推理过程，只输出纯 JSON。
            - adjustedTickets 顺序与输入 tickets 一致，id 回填输入 id（若有）；
              每个 AdjustedTicket 禁止包含 complexTicket 字段。
            - finalRecommendation 必填：danBalls 恰好 3 个；singleTickets 恰好 2 组；
              complexTicket 恰好 1 组；两组单式与复式红球均须包含全部 3 胆。
            - 所有号码为整数；红球 1-33 互异升序；蓝球 1-16 互异升序。
            - 若初稿违反任一硬性约束，须在输出前自行修正至合规，不得输出违规方案。
            """;

    /**
     * 推荐模式 Prompt（tickets 为空时使用）。
     * <p>占位符：{report} / {count} / {format}
     * <p>输出 Schema 与调优模式完全一致；无原始号码，故 original* / replacements 置空。
     */
    public static final String RECOMMEND_PROMPT = """
            请基于以下双色球号码特征分析报告，直接推荐 {count} 组可购买的单式号码方案；
            最后综合输出【一组】最终可购买方案：3 胆码 + 2 组单式 + 1 组复式。
            本组任务是「从零生成」，不是对已有号码调优；请严格输出与调优模式相同的 JSON Schema。
            注意：各组 AdjustedTicket 不要输出 complexTicket。

            【核心原则】
            1. 下一期形态以报告 `featureForecast` 为准（间隔走冷/走热 + LLM；value 为主推）。
               dueWindow=true 且 confidence≥0.5 时优先落入；低置信度仅软参考。
               报告无历史直方图，不得臆造「高频号表」整池搬入。
            2. 热号仅作候选池；选号主体必须冷热分散、三区分散，并防范热号集体回冷。
            3. 所有选号须可核验：冷热档位、分区、连号、邻狐传均需满足硬性约束。
            4. 【杀号硬约束】若报告中存在 `killNumbers` 字段：
               - `hardKillRed` / `hardKillBlue` 中的号码为「硬杀清单」，禁止出现在任何输出号码中
                 （含 adjustedRedBalls、adjustedBlueBall、
                  finalRecommendation.danBalls、
                  finalRecommendation.singleTickets.redBalls/blueBall、
                  finalRecommendation.complexTicket.redBalls/blueBalls）；
               - 若 `killNumbers` 为 null 或各清单为空，则忽略本条约束。
            5. 【冷热温硬约束】若报告存在 `coldHotAnalysis` 字段：
               - `redHotBalls` / `redWarmBalls` / `redColdBalls` 为红球热/温/冷号清单，
                 `blueHotBalls` / `blueWarmBalls` / `blueColdBalls` 为蓝球热/温/冷号清单；
               - 须直接使用上述清单判定每个候选号码的冷热档位，**不得**再自行推断冷热；
               - 若 `coldHotAnalysis` 为 null，则冷热约束降为软参考，仍须满足杀号与形态硬约束。
            6. 【红球和值与跨度约束】所有红球输出：
               - 若 `featureForecast.span` / `sumRange` 的 dueWindow=true 且 confidence≥0.5：
                 跨度、和值须落入其 value（或 alternatives）；
               - 否则仅软参考；缺失时回退：和值 ∈ [90, 130]、跨度 ∈ [16, 28]。
               回退区间不满足时须在输出前自行调整号码至合规。
            7. 【红球奇偶与质合上限硬约束】所有红球输出（adjustedRedBalls、
               finalRecommendation.singleTickets.redBalls、
               finalRecommendation.complexTicket.redBalls）必须同时满足：
               - 奇数个数 ≤ 5（禁止 6 个红球全为奇数，即禁止 6:0 奇偶比）；
               - 质数个数 ≤ 4（质数取 2,3,5,7,11,13,17,19,23,29,31，1 视为合数；禁止 ≥5 个质数）。
               任一条件不满足即视为违规，须在输出前自行调整号码至合规。
            8. 【形态推算约束】报告必有 `featureForecast`（间隔快照 + LLM 推算，含 gapTrend/eta）：
               - 各维 `value` 为主推；`alternatives` 为备选；
               - `dueWindow=true` 且 `confidence≥0.5`：单式须落入 value 或 alternatives（硬优先）；
               - `dueWindow=false` 或 `confidence<0.5`：仅软参考，允许偏离，不得为迎合该维而整组重调；
               - `gapTrend=cooling` 且 eta 很大：勿强行追该形态；`gapTrend=heating` 且 dueWindow：可提高权重；
               - 三区比必须与 zone1Count / zone2Count / zone3Count 尽量自洽；
               - 蓝球：`blueBigSmallOddEven` 优先，`blueOddEven`/`blueBigSmall` 应与其对齐；
                 口径：1-8 小、9-16 大；奇偶按 n%2；大小奇偶为 小奇/小偶/大奇/大偶；
                 012路按 n%3（0路=3,6,9,12,15；1路=1,4,7,10,13,16；2路=2,5,8,11,14）。
            9. 【三区比预测软约束】若报告存在 `predictedThreeZoneRatio` 字段：
               - `candidates` 为下一期 Top-K 候选三区比及概率，`lastRatio` 为最近一期实际三区比；
               - 各组单式/最终单式/复式的三区比应尽量落在 Top 候选之中（概率越高越优先）；
               - 不得强行追求概率最高的单一形态而违反其他硬约束；
               - 若与 `featureForecast.threeZone` 冲突，以 featureForecast 为准；
               - 若 `predictedThreeZoneRatio` 为 null，则以 `featureForecast.threeZone` 为准。
            10. 【趋势均线补充约束】若报告存在 `trendAnalysis` 字段（堆叠 + MA5 斜率相位）：
               - `reboundingRedBalls` / `reboundingBlueBalls`：空头或交叉但斜率向上（回暖），
                 补号时**最优先**，禁止因「空头」回避；
               - `risingRedBalls` / `risingBlueBalls`：多头且斜率未下行，次优先；
               - `coolingRedBalls` / `coolingBlueBalls`：多头但斜率下行，谨慎；
               - `fallingRedBalls` / `fallingBlueBalls`：空头且斜率未上行（真趋冷），可降权但勿整池硬删；
               - 补号优先级：回暖 > 上升 > 平稳/未入榜 > 转弱 > 真趋冷；
               - 不得违反杀号/冷热/和值/跨度等硬约束，趋势仅作补号倾向；
               - 若 `trendAnalysis` 为 null，则忽略本条约束。
            11. 【组间差异】{count} 组方案须互有差异（至少红球或蓝球不同），可分别侧重不同形态假设
               （如热温延续 / 温冷回补 / 分区均衡等），禁止 {count} 组完全相同。

            【特征分析报告】
            {report}

            【推荐规则】
            一、生成 {count} 组单式（写入 adjustedTickets，恰好 {count} 组；禁止输出 complexTicket）
            1. 每组输出 6 红 + 1 蓝；红球 1-33 互异升序；蓝球 1-16。
            2. 因无原始号码：originalRedBalls / originalBlueBall 置 null；
               redReplacements 置空数组 []；blueReplacement 置 null。
            3. adjustedRedBalls / adjustedBlueBall 即为本组推荐单式。
            4. 单式硬性结构：
               - 冷热（取自 coldHotAnalysis）：理想热2-3、温2-3、冷1-2；热号≤3、冷号≤2；
               - 分区：一区(1-11)/二区(12-22)/三区(23-33) 不得出现「某区 0 个」或「某区≥4」；
               - 连号：最长连号长度≤2，且至多 1 组 2 连号；
               - 和值/跨度优先落入 featureForecast；缺失时和值 ∈ [90,130]、跨度 ∈ [16,28]；
               - 奇数个数 ≤ 5、质数个数 ≤ 4（质数取 2,3,5,7,11,13,17,19,23,29,31，1 视为合数）；
               - 形态：dueWindow 且高置信维优先落入 featureForecast；低置信软参考；
                 三区比与 zone1/2/3Count 尽量自洽；
                 蓝球以 blueBigSmallOddEven 为准对齐奇偶/大小，再看 012 路；
            5. id 可用 "R1"/"R2"/...；reason 说明本组形态假设与选号依据（150 字内）。

            二、最终推荐包（必填 finalRecommendation，全响应仅此一份）
            综合特征报告 + 各组推荐单式，凝练成最终可购买方案，包含三部分：

            （1）三个胆码 danBalls（恰好 3 个红球，升序互异）
            - 从各组推荐结果的共性温号/结构共识中提炼，禁止全选超热号；
            - 超热号在胆码中至多 1 个；优先温号 + 回暖/上升号；
            - 三胆应尽量覆盖不同分区（一/二/三区），避免同区扎堆；
            - danBasis 说明为何选这三胆（150 字内）。

            （2）两组单式 singleTickets（恰好 2 组，每组 6 红 + 1 蓝、totalBets=1）
            - 两组分别针对不同形态假设（如热温延续 / 温冷回冷）；
            - 【胆码硬约束】每组红球必须包含全部 3 个 danBalls，再补 3 个拖码；
            - 须满足全部硬约束（杀号/冷热/和值/跨度/奇偶/质合/分区/连号）；
            - name、basis 说明形态假设与选号依据（200 字内）。

            （3）一组复式 complexTicket（红球 7-10、蓝球 2-5，全响应仅此一组）
            - 【胆码硬约束】复式红球必须包含全部 3 个 danBalls；
            - 不要与某一组 adjustedTickets 简单等同；超热号入选总数红球≤3、蓝球≤2。
            - 红球 7-10、蓝球 2-5，互异升序，且必须同时满足：
              - 形态：须落入 `featureForecast`（红球奇偶/大小/质合/012路/跨度/和值/和尾/三区/区个数，
                以及蓝球奇偶/大小/大小奇偶/012路）；
              - 三区比预测：与 featureForecast.threeZone 一致时优先；否则参考 `predictedThreeZoneRatio.candidates`；
              - 和值/跨度硬约束：优先 featureForecast；缺失时 6 红球子集和值 ∈ [90,130]、跨度 ∈ [16,28]；
              - 奇偶/质合硬约束：6 红球子集（任取 6 个）的奇数个数 ≤ 5、质数个数 ≤ 4
                （质数取 2,3,5,7,11,13,17,19,23,29,31，1 视为合数）；
              - 冷热（取自 coldHotAnalysis）：红球热≤4、温≥2、冷≥2；蓝球热≤2，且含≥1 温、≥1 冷；
              - 分区：红球每区≥2；蓝球覆盖≥2 个四分区；
              - 连号：最长≤2，2 连组数≤2；
              - 邻狐传：红球相对上期重号≤2、狐号≥2；蓝球至少 1 个狐号。
            - totalBets = C(红球个数, 6) × 蓝球个数，需准确；
              name、basis、conclusion 说明为何选这套（各 200 字内）。
              conclusion 必须说明：如何用胆码锚定、温冷号分散、限制连号与重号，
              降低「热号集体回冷 + 号段错位 + 蓝球追热」三类风险。

            【输出要求】
            - 严格输出 JSON，结构必须符合以下 Schema：
            {format}
            - 不得输出 Markdown 代码块、解释文字或推理过程，只输出纯 JSON。
            - adjustedTickets 必须恰好 {count} 组；每个 AdjustedTicket 禁止包含 complexTicket 字段。
            - finalRecommendation 必填：danBalls 恰好 3 个；singleTickets 恰好 2 组；
              complexTicket 恰好 1 组；两组单式与复式红球均须包含全部 3 胆。
            - 所有号码为整数；红球 1-33 互异升序；蓝球 1-16 互异升序。
            - 若初稿违反任一硬性约束，须在输出前自行修正至合规，不得输出违规方案。
            """;
}
