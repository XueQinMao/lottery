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
 * <p>核心原则：`featureForecast` 是下一期形态的<strong>概率先验</strong>，不是开奖答案。
 * 含备选命中约 60-90% 主要来自覆盖面（高频桶被备选盖住），主推单独更低，每期约 2-6 维整维未中。
 * dueWindow 对众数几乎总为 true，confidence 只是 Top1/Top2 分差，都不是命中率。
 * 禁止把 15 维同时当硬锁，禁止所有号码组追逐同一套 value。
 * 冷热/分区/邻狐传约束「结构」；热号仅作候选，禁止复式主体由超热号堆砌。
 * 最终推荐包含：2 组单式 + 1 组复式（红 7-10 / 蓝 2-5）。
 *
 * <p>真硬约束：杀号清单、冷热温档位、红球奇数个数 ≤ 5、质数个数 ≤ 4、
 * 和值/跨度安全网（和值 ∈ [90,130]、跨度 ∈ [16,28]）。
 * 形态：奇偶/大小/质合作分散假设（一组均衡、一组备选侧）；蓝球形态先做覆盖检查；
 * 012路/跨度/和值/和尾/三区比为较弱维（只作方向，禁止硬锁）。
 *
 * <p>占位符：{report} / {tickets} / {count} / {format} / {lastRedBalls} / {lastBlueBall}
 *
 * @author 刘强
 * @version 2026/08/17
 **/
public final class LotteryAdjustPrompt {

    private LotteryAdjustPrompt() {
    }

    public static final String USER_PROMPT = """
            请基于以下双色球号码特征分析报告，对给出的预测号码组逐一调优（仅单式）；
            最后综合输出【一组】最终可购买方案：2 组单式 + 1 组复式。
            注意：各组 AdjustedTicket 不要输出 complexTicket。

            【核心原则】
            1. `featureForecast` 是形态先验，不是标准答案。
               回测：含备选（value ∪ alternatives）约 60-90% 维能中，主要因为备选盖住高频桶，
               不是主推准；主推单独更低；每期通常有 2-6 维整维未中
               （尤其 012路、跨度、和值区间、和值尾数、三区比）。
               生成时已排除「遗漏到期概率>0」的取值，但被排除值仍会开出，也可能漏进 alternatives，
               因此剩余 value/alternatives 只是偏好池，不是必中集合，未列出 ≠ 禁选。
               dueWindow 对众数几乎总为 true，confidence 只是分差，都不是命中率。
               禁止 15 维同时硬锁；禁止所有组都贴同一套 value。
               报告无历史直方图，不得臆造「高频号表」整池搬入。
            2. 热号仅作候选池；选号主体必须冷热分散、三区分散，并防范热号集体回冷。
            3. 所有替换与扩号须可核验：冷热档位、分区、连号、邻狐传均需满足结构约束。
            4. 【杀号分级约束】若报告中存在 `killNumbers` 字段，按红球/蓝球分别处理：
               - 字段结构：`hardKillRed` / `softKillRed` / `hardKillBlue` / `softKillBlue`，
                 每项为 List<KillItemBo>（含 ball/score/source/reason）。
               - 【蓝球硬约束】所选蓝球（adjustedBlueBall、finalRecommendation 中的所有蓝球）
                 严禁出现在 `hardKillBlue` 中；优先选择在 `softKillBlue` 的蓝球；仅当`softKillBlue`蓝球全部无法满足形态/冷热等其他硬约束时，
                 才允许从非 `softKillBlue`和`hardKillBlue` 中降级选取，basis 必须注明「蓝球降级选自软杀池」。
               - 【红球硬杀上限约束】红球硬杀回测命中 1–3 个占 90%（最常见 2–3 个），
                 软杀命中 0–2 个占 88%（最常见 1 个）。因此不强制排除 hardKillRed，
                 而是设上限引导：
                 · 每组 6 红球中，从 `hardKillRed` 选取的号码至多 3 个（建议默认 2 个）；
                 · 从 `softKillRed` 选取的号码至多 2 个（建议默认 1 个）；
                 · 其余从既不在硬杀也不在软杀的号码池中选取（建议默认 3 个）；
                 · 默认配比「硬杀2 + 软杀1 + 非杀3」，可依形态/冷热/分区约束在
                   「硬1–3 + 软0–2 + 非杀2–4」范围内浮动，但硬杀不得超过 3 个；
                 · 若 `hardKillRed` 条数不足 2，可从软杀/非杀池补足；
                   若 `softKillRed` 为空，则从非杀池补足；
               - 若 `killNumbers` 为 null 或各清单为空，则忽略本条约束，
                 退回冷热/形态/和值/跨度等结构约束选号。
            5. 【冷热温硬约束】若报告存在 `coldHotAnalysis` 字段：
               - `redHotBalls` / `redWarmBalls` / `redColdBalls` 为红球热/温/冷号清单，
                 `blueHotBalls` / `blueWarmBalls` / `blueColdBalls` 为蓝球热/温/冷号清单；
               - 须直接使用上述清单判定每个候选号码的冷热档位，**不得**再自行推断冷热；
               - 冷热配比要求见下方调优规则（热号≤上限、冷号≥下限等）；
               - 若 `coldHotAnalysis` 为 null，则冷热约束降为软参考，仍须满足杀号与结构硬约束。
            6. 【红球和值与跨度】分两层，禁止把 forecast 当硬锁：
               - 【安全网·真硬】所有 6 红球：和值 ∈ [90, 130]、跨度 ∈ [16, 28]；
                 越界须在输出前调整。这是结构底线，与 dueWindow/confidence 无关。
               - 【偏好中心·软】`featureForecast.span` / `sumRange` 只指示偏好中心，回测整维未中较多。
                 允许落在安全网内但偏离 forecast。若 value 连多期不变（如和值 85-90、跨度 26-30），
                 视为粘性中心而非规律，整包不追；value 落在安全网外时最多一组在网边缘试探。
               - 不得为贴 span/sumRange 而牺牲杀号/冷热/分区；禁止因为 forecast 在安全网外
                 （如和值 85-90）就把该组强行拉出安全网。
            7. 【红球奇偶与质合上限硬约束】所有红球输出（adjustedRedBalls、
               finalRecommendation.singleTickets.redBalls、
               finalRecommendation.complexTicket.redBalls）必须同时满足：
               - 奇数个数 ≤ 5（禁止 6 个红球全为奇数，即禁止 6:0 奇偶比）；
               - 质数个数 ≤ 4（质数取 2,3,5,7,11,13,17,19,23,29,31，1 视为合数；禁止 ≥5 个质数）。
               任一条件不满足即视为违规，须在输出前自行调整号码至合规。
            8. 【形态分层使用】报告必有 `featureForecast`（含 gapTrend/eta/dueWindow/confidence/reason）：
               各维独立评分，常自相矛盾；dueWindow 对理论众数几乎总为 true（含刚出、eta 已大幅为负），
               confidence 只是 Top1/Top2 分差、不是命中率，score 高也不代表会开出。
               冲突优先级：杀号硬约束 > 冷热/分区/连号/奇偶质合上限/和值跨度安全网 > 比例维分散假设 > 较弱形态维。
               【覆盖检查·先做】若某维 value∪alternatives 几乎盖住该维全部合法取值
               （蓝球大小奇偶 ≥3/4、蓝球012=3/3、奇偶已含 3:3+4:2+2:4），则该维不作选号过滤，
               只可作组间标签；覆盖已满时禁止再要求「一组贴主推、一组贴备选」。
               （1）比例维 oddEven/bigSmall/primeComposite：含备选命中高是因为备选盖住高频桶，主推仍常落空。
                   一组偏均衡（如 3:3 / 2:4），一组走另一侧备选（4:2 或 2:4）；不要全组同一比；
                   1:5/0:6 不要当默认，也不要当绝对禁区。与杀号/冷热/分区冲突时可弃。
               （2）蓝球：硬约束是杀号+冷热。形态先覆盖检查。
                   cooling 且遗漏=0（reason 含「理论众数刚出仍主推」）时不要追 value，
                   二元维（大/小、奇/偶）改走对立备选。blueRatio012 通常覆盖满，忽略。
                   blueBigSmallOddEven 覆盖未满时，多组蓝球应分散大小或奇偶，禁止全部挤同一号。
               （3）较弱维 ratio012/span/sumRange/sumTail/threeZone：只作方向，禁止硬锁/整组重调。
                   排除清单中的值仍可能开出，也可能漏进 alternatives；未列出 ≠ 禁选。
               （4）区个数与 threeZone 经常对不上、甚至 zone1+zone2+zone3≠6：禁止四维联立去硬凑。
                   以分区结构底线为准（避免某区 0 或 ≥5，允许某区=4）；threeZone 只作均衡偏好。
                   禁止为贴 forecast 去造 0:6、6:0、某区 0/5。
               （5）权重只看 gapTrend/遗漏/eta/reason，禁止用 dueWindow×confidence 给 value 加权：
                   cooling+遗漏0 或 reason 含「刚出仍主推」或 eta≤-3 → 降权 value、勿追；
                   heating 且遗漏>0 → 可略偏 value，仍非必须；
                   confidence<0.15（含 0）→ 该维无区分度，忽略。
                   「高频黏性连出」仅对蓝球奇偶这类二元维可略信，红球比例维仍只软参考。
               （6）组间分散要分散真能分开的维：奇偶 4:2 vs 2:4、大小 3:3 vs 4:2、
                   蓝球大 vs 小（覆盖未满时）。覆盖已满的维上拆「主推/备选」是假分散。
                   较弱维允许两组都偏离。
               （7）蓝球口径：1-8 小、9-16 大；奇偶按 n%2；大小奇偶为 小奇/小偶/大奇/大偶；
                   012路按 n%3（0路=3,6,9,12,15；1路=1,4,7,10,13,16；2路=2,5,8,11,14）。
            9. 【三区比预测软约束】若报告存在 `predictedThreeZoneRatio` 字段：
               - `candidates` 为下一期 Top-K 候选三区比及概率，`lastRatio` 为最近一期实际三区比；
               - 与 `featureForecast.threeZone` 都是软参考，都不得压过分区结构底线；
               - 冲突时优先较均衡形态（2:2:2 / 1:2:3 / 2:1:3 / 1:3:2 / 3:2:1），
                 不要为追最高概率去造某区 0 或 ≥5；
               - 不得强行追求概率最高的单一形态而违反杀号/冷热/和值/跨度/连号；
               - 若 `predictedThreeZoneRatio` 为 null，则仅软参考 `featureForecast.threeZone`。
            10. 【趋势均线补充约束】若报告存在 `trendAnalysis` 字段（堆叠 + MA5 斜率相位）：
               - `reboundingRedBalls` / `reboundingBlueBalls`：空头或交叉但斜率向上（回暖），
                 补号时**最优先**，禁止因「空头」回避；
               - `risingRedBalls` / `risingBlueBalls`：多头且斜率未下行，次优先；
               - `coolingRedBalls` / `coolingBlueBalls`：多头但斜率下行，谨慎；
               - `fallingRedBalls` / `fallingBlueBalls`：空头且斜率未上行（真趋冷），可降权但勿整池硬删；
               - 补号优先级：回暖 > 上升 > 平稳/未入榜 > 转弱 > 真趋冷；
               - 不得违反杀号/冷热/和值/跨度安全网，趋势仅作补号倾向；
               - 若 `trendAnalysis` 为 null，则忽略本条约束。
            11. 【极端整组重调】默认以局部替换为主；但若本组原号码陷入极端不合规，
               **允许将红球 6 个与蓝球全部替换**，等价于按报告从零重选一组单式。
               极端情形包括但不限于：
               - 原红球中 ≥3 个落入 `hardKillRed`，或蓝球落入 `hardKillBlue` 且难以单点替换；
               - 过热+过冷+号段失衡+连号违规+和值/跨度越出安全网+奇偶/质合越界等多条硬约束同时严重冲突，
                 局部换 1-2 个号仍无法同时满足全部硬约束。
               **不要**仅因较弱形态维（012路/跨度/和值/和尾/三区比）偏离 forecast 就整组重调。
               整组重调时：redReplacements 须覆盖所有被换掉的原红球（from→to），
               blueReplacement 须写出原蓝→新蓝；reason / basis 须明确注明「极端整组重调」及触发原因。
               重调后的号码仍须满足全部真硬约束（杀号/冷热/和值跨度安全网/奇偶/质合/分区/连号/上期号码约束等）。
            12. 【上期开奖号码硬约束】若入参提供上一期开奖号码（lastRedBalls / lastBlueBall，
               见下方【上期开奖号码】区块），所有红球与蓝球输出（adjustedRedBalls、adjustedBlueBall、
               finalRecommendation.singleTickets.redBalls/blueBall、
               finalRecommendation.complexTicket.redBalls/blueBalls）必须同时满足：
               - 红球重号上限：当期 6 红球中，与上期红球（lastRedBalls）重合的号码至多 1 个（最多 1 个重号）；
               - 蓝球排斥：当期蓝球不得等于上期蓝球（lastBlueBall）；
               - 蓝红互斥：上期蓝球（lastBlueBall）不得出现在当期任何红球中；
               - 红球连号相邻排斥：若上期红球中存在 2 连号（相邻两个连续号，如 6、7），
                 则当期红球不得包含该连号两端的相邻号（即 5 与 8）；
                 多组连号时对每组连号分别套用本规则；连号长度 ≥3 时按相邻两两拆分
                 （如 6、7、8 视作含 6-7 与 7-8 两组连号，禁 5、8 与 6、9）。
               - 若 lastRedBalls 为空或 null，则忽略本条全部约束。
               - 优先级：杀号硬约束 > 上期号码约束 > 冷热/分区/连号/奇偶质合/和值跨度安全网 > 比例维分散假设 > 较弱形态维。
                 若上期连号相邻排斥与连号结构底线（最长≤2、至多 1 组 2 连号）冲突，
                 以连号结构底线为准并尽量满足上期约束。

            【特征分析报告】
            {report}

            【上期开奖号码】
            上期红球：{lastRedBalls}
            上期蓝球：{lastBlueBall}
            （若为空则忽略【上期开奖号码硬约束】全部规则）

            【待调整的预测号码组】
            {tickets}

            【调优规则】
            一、单式号码组调整（对每一组，写入 adjustedTickets；禁止输出 complexTicket）
            1. 红球比对：先核验结构（杀号/冷热/分区/连号/和值跨度安全网/奇偶质合上限），
               再把比例维当分散假设叠加，较弱维只作方向、不作为否决项：
               - 冷热档位直接取自报告 `coldHotAnalysis.redHotBalls/redWarmBalls/redColdBalls`；
               - 热（在 redHotBalls 中）、温（在 redWarmBalls 中）、冷（在 redColdBalls 中）。
               - 热号≥4 →「过热」（热号回冷风险高）；
               - 冷号≥3 →「过冷」（可能偏离活跃区间）；
               - 理想单式冷热：热2-3、温2-3、冷1-2。
               另检号段：一区(1-11)/二区(12-22)/三区(23-33) 尽量避免「某区 0 个」或「某区≥5」；
               某区=4 可接受。连号：最长连号长度≤2，且至多 1 组 2 连号（禁止 1,2,3 这类 3 连团）。
               【形态】比例维一组偏均衡、一组走另一侧备选；覆盖已满的维不作过滤。
               较弱维不否决本组。cooling+刚出或 eta≤-3 时勿追 value。
               【和值/跨度】先满足安全网 [90,130]/[16,28]；粘性中心（如 85-90）整包不追；
               【奇偶/质合硬校验】6 红球奇数个数须 ≤ 5、质数个数须 ≤ 4
               （质数取 2,3,5,7,11,13,17,19,23,29,31，1 视为合数）；
               超出范围时按下方替换规则调整至合规。
            2. 红球替换：红球 1-33 互异升序共 6 个。在维持合理形态前提下，按优先级处理：
               (a) 过热：将 1-2 个超热号换成温号或遗漏适中的冷号，使热号降至 ≤3；
                   basis 注明「热号回冷防御」。
               (b) 过冷：将 1-2 个极冷号换成温热号，避免扎堆超热；冷号≤2；
                   basis 注明「冷号复苏平衡」。
               (c) 号段失衡：缺区则补该区温/冷号；某区过多（≥5）则换出该区热号到其他区。
               (d) 连号违规：拆散 3 连及以上，或多余的 2 连组，换成同区非连续号。
               (e) 和值越出安全网：和值<90 → 将较小号换成更大的温/冷号；和值>130 → 将较大号换成更小的温/冷号；
                   basis 注明「和值校准至90-130」。不要为贴 forecast.sumRange 而突破安全网。
               (f) 跨度越出安全网：跨度<16 → 将最小号调小或最大号调大（扩大分布）；跨度>28 → 收拢两端，
                   将最小号调大或最大号调小；basis 注明「跨度校准至16-28」。
               (g) 奇偶越界：奇数个数=6 → 将其中 1 个奇号换成同区/邻区的偶号（优先换最热或连号中的奇号），
                   使奇数个数降至 ≤5；basis 注明「奇偶校准至≤5奇」。
               (h) 质合越界：质数个数≥5 → 将其中 1-2 个质数换成同区/邻区的合数
                   （合数即非质数，含 1）；basis 注明「质合校准至≤4质」。
               (i) 冷热已均衡且结构合规时，可不替换。
                   较弱维偏离 forecast、或比例维未贴 value，都不是必须替换的理由。
               (j) 【极端整组重调】若局部替换（通常 1-2 个）仍无法同时满足杀号/冷热/分区/连号/
                   和值跨度安全网/奇偶/质合等真硬约束，允许一次性替换全部 6 个红球（可同时替换蓝球），
                   按报告约束重新生成合规单式；优先从 `reboundingRedBalls` / `risingRedBalls` 与温号池选取，
                   仍须遵守红球硬杀上限（≤3）与蓝球硬杀禁选约束。basis 必须写明「极端整组重调」及具体触发原因。
                   禁止仅因较弱形态维未贴 forecast 而整组重调。
               (k) 【杀号配比引导】替换/选号时参考 killNumbers 配比：
                   默认「硬杀2 + 软杀1 + 非杀3」，可在「硬1–3 + 软0–2 + 非杀2–4」浮动，
                   硬杀命中数不得超过 3。不要将 hardKillRed 整池排除，
                   也不要全部从 hardKillRed 中选。basis 可注明「按杀号回测配比选号」。
               (l) 【上期号码约束修复】若本组违反【上期开奖号码硬约束】：
                   - 重号 > 1：将多余的重号换成同区/邻区非上期红球、且不落入上期连号相邻号的温/冷号，
                     basis 注明「上期重号降至≤1」；
                   - 蓝球等于上期蓝球：按蓝球替换规则换为非上期蓝球的温/回暖蓝球，
                     basis 注明「上期蓝球排斥」；
                   - 红球含上期蓝球：将该红球换成同区非上期蓝球、非上期连号相邻号的号，
                     basis 注明「上期蓝红互斥」；
                   - 红球含上期连号相邻号：将该号换成同区非相邻号、非上期红球的温/冷号，
                     basis 注明「上期连号相邻排斥」。
               替换须给出 from、to、basis；替换后重新升序。
               极端整组重调时 redReplacements 应包含全部 6 组 from→to（原号→新号）。
            3. 蓝球比对与替换：蓝球 1-16。冷热档位直接取自报告
               `coldHotAnalysis.blueHotBalls/blueWarmBalls/blueColdBalls`。
               - 【蓝球杀号硬约束】所选蓝球严禁落入 `hardKillBlue`；
                 优先选既不在 `hardKillBlue` 也不在 `softKillBlue` 的蓝球；
                 仅当非硬杀蓝球全部无法满足形态/冷热等硬约束时，
                 才允许从 `softKillBlue` 中降级选取，basis 注明「蓝球降级选自软杀池」。
               - 【蓝球形态】先覆盖检查：value∪alternatives 盖住 ≥3/4 大小奇偶或 3/3 的 012 路则不作过滤。
                 cooling 且遗漏=0（刚出仍主推）时不要追 value，二元维改走对立备选。
                 与杀号冲突时优先杀号。不要把 blueRatio012 当硬锁。
                 覆盖未满时多组蓝球应分散大小或奇偶，禁止全部挤同一号。
               - 过热蓝球（在 blueHotBalls 中）→ 优先换为同路或邻区的温蓝球；basis 注明「蓝球冷热均衡」。
               - 极冷蓝球（在 blueColdBalls 中）→ 可换温号，但最终复式阶段仍须保留冷/温分散。
               - 单式蓝球优先选相对上期的狐号或温号，避免默认追上期邻号/重号或超热蓝。
               - 【上期蓝球排斥】所选蓝球不得等于上期蓝球（lastBlueBall）；若原蓝球违反，
                 换为非上期蓝球的温/回暖蓝球，basis 注明「上期蓝球排斥」。
               - 若触发红球极端整组重调，或蓝球本身落入 hardKillBlue / 与红球硬约束冲突无法单点修复，
                 允许同时更换蓝球；优先选回暖/上升或温号池中的非硬杀蓝球。
            4. 若已整体合理可不做替换，但仍须输出 adjustedRedBalls 与 adjustedBlueBall。
               仅在上述极端情形下才整组全换；非极端时仍优先局部替换，避免无必要大改。
               较弱形态维偏离 forecast 属于「整体合理」的可接受范围。
            5. reason 简要说明本组调整（150 字内），须点明冷热、分区或连号是否触达硬约束，
               以及本组比例维走的是均衡侧还是另一侧备选；
               若发生极端整组重调，须在 reason 首句写明「极端整组重调」。

            二、最终推荐包（必填 finalRecommendation，全响应仅此一份）
            综合特征报告 + 各组调整后的单式，凝练成最终可购买方案，包含两部分：

            （1）两组单式 singleTickets（恰好 2 组，每组 6 红 + 1 蓝、totalBets=1）
            - 两组必须针对不同形态假设：一组比例维偏均衡（如 3:3），另一组走另一侧备选；
              不要两组都追逐同一套 forecast；覆盖已满的维不要再拆主推/备选。
            - 须满足全部真硬约束（杀号/冷热/和值跨度安全网/奇偶/质合/分区/连号/上期号码约束）；
            - 较弱形态维允许两组都偏离 forecast；
            - name、basis 说明形态假设与选号依据（200 字内）。

            （2）一组复式 complexTicket（红球 7-10、蓝球 2-5，全响应仅此一组）
            - 不要与某一组 adjustedTickets 简单等同；可吸收多组共性结构；
              超热号入选总数红球≤3、蓝球≤2。
            - 红球 7-10、蓝球 2-5，互异升序，且必须同时满足：
              - 【形态·覆盖而非锁定】复式目的是让不同 6 红子集分别覆盖比例维的两侧假设；
                禁止要求整组同时落入全部 15 维；较弱维（012路/跨度/和值/和尾/三区比）不作为入选门槛。
                蓝球 2-5 先覆盖检查：已满则分散冷温/分区即可，未满才补对立大小或奇偶。
              - 三区比：软参考 featureForecast.threeZone 与 predictedThreeZoneRatio.candidates，
                复式内不同 6 子集可覆盖 1-2 个较均衡三区比，禁止整包做成某区 0 或 ≥5。
              - 和值/跨度：复式内应存在至少一组 6 红子集满足安全网 [90,130]/[16,28]；
                不要求所有子集都贴 forecast.span/sumRange。
              - 奇偶/质合硬约束：6 红球子集（任取 6 个）的奇数个数 ≤ 5、质数个数 ≤ 4
                （质数取 2,3,5,7,11,13,17,19,23,29,31，1 视为合数）；
              - 冷热（取自 coldHotAnalysis）：红球热≤4、温≥2、冷≥2；蓝球热≤2，且含≥1 温、≥1 冷；
              - 杀号配比：红球 hardKillRed 命中数 ≤3、softKillRed 命中数 ≤2；
                蓝球严禁选 hardKillBlue，优先排除 softKillBlue，仅在无可用非硬杀蓝时降级选 softKillBlue；
              - 分区：红球每区≥2；蓝球覆盖≥2 个四分区，建议含三区(9-12)或四区之一作分散；
              - 连号：最长≤2，2 连组数≤2；
              - 邻狐传：红球相对上期重号≤2、狐号≥2；蓝球至少 1 个狐号。
              - 【上期号码约束】复式红球中与上期红球（lastRedBalls）重合的号码至多 1 个；
                复式蓝球不得包含上期蓝球（lastBlueBall）；复式红球不得包含上期蓝球；
                复式红球不得包含上期红球任意 2 连号两端的相邻号。
                若 lastRedBalls 为空则忽略本条。
            - totalBets = C(红球个数, 6) × 蓝球个数，需准确；
              name、basis、conclusion 说明为何选这套（各 200 字内）。
              conclusion 必须说明：如何用温冷号分散、限制连号与重号，
              以及两组单式如何分别走比例维均衡侧与另一侧备选，
              降低「热号集体回冷 + 号段错位 + 蓝球追热 + 形态全组赌同一套 forecast」四类风险。

            【输出要求】
            - 严格输出 JSON，结构必须符合以下 Schema：
            {format}
            - 不得输出 Markdown 代码块、解释文字或推理过程，只输出纯 JSON。
            - adjustedTickets 顺序与输入 tickets 一致，id 回填输入 id（若有）；
              每个 AdjustedTicket 禁止包含 complexTicket 字段。
            - finalRecommendation 必填：singleTickets 恰好 2 组；
              complexTicket 恰好 1 组。
            - 所有号码为整数；红球 1-33 互异升序；蓝球 1-16 互异升序。
            - 若初稿违反任一真硬约束，须在输出前自行修正至合规，不得输出违规方案。
              形态维未贴 forecast 不属于违规。
            """;

    /**
     * 推荐模式 Prompt（tickets 为空时使用）。
     * <p>占位符：{report} / {count} / {format} / {lastRedBalls} / {lastBlueBall}
     * <p>输出 Schema 与调优模式完全一致；无原始号码，故 original* / replacements 置空。
     */
    public static final String RECOMMEND_PROMPT = """
            请基于以下双色球号码特征分析报告，直接推荐 {count} 组可购买的单式号码方案；
            最后综合输出【一组】最终可购买方案：2 组单式 + 1 组复式。
            本组任务是「从零生成」，不是对已有号码调优；请严格输出与调优模式相同的 JSON Schema。
            注意：各组 AdjustedTicket 不要输出 complexTicket。

            【核心原则】
            1. `featureForecast` 是形态先验，不是标准答案。
               回测：含备选（value ∪ alternatives）约 60-90% 维能中，主要因为备选盖住高频桶，
               不是主推准；主推单独更低；每期通常有 2-6 维整维未中
               （尤其 012路、跨度、和值区间、和值尾数、三区比）。
               生成时已排除「遗漏到期概率>0」的取值，但被排除值仍会开出，也可能漏进 alternatives，
               因此剩余 value/alternatives 只是偏好池，不是必中集合，未列出 ≠ 禁选。
               dueWindow 对众数几乎总为 true，confidence 只是分差，都不是命中率。
               禁止 15 维同时硬锁；禁止所有组都贴同一套 value。
               报告无历史直方图，不得臆造「高频号表」整池搬入。
            2. 热号仅作候选池；选号主体必须冷热分散、三区分散，并防范热号集体回冷。
            3. 所有选号须可核验：冷热档位、分区、连号、邻狐传均需满足结构约束。
            4. 【杀号分级约束】若报告中存在 `killNumbers` 字段，按红球/蓝球分别处理：
               - 字段结构：`hardKillRed` / `softKillRed` / `hardKillBlue` / `softKillBlue`，
                 每项为 List<KillItemBo>（含 ball/score/source/reason）。
               - 【蓝球硬约束】所选蓝球（adjustedBlueBall、finalRecommendation 中的所有蓝球）
                 严禁出现在 `hardKillBlue` 中；优先选择在 `softKillBlue` 的蓝球；仅当`softKillBlue`蓝球全部无法满足形态/冷热等其他硬约束时，
                 才允许从非 `softKillBlue`和`hardKillBlue` 中降级选取，basis 必须注明「蓝球降级选自软杀池」。
               - 【红球硬杀上限约束】红球硬杀回测命中 1–3 个占 90%（最常见 2–3 个），
                 软杀命中 0–2 个占 88%（最常见 1 个）。因此不强制排除 hardKillRed，
                 而是设上限引导：
                 · 每组 6 红球中，从 `hardKillRed` 选取的号码至多 3 个（建议默认 2 个）；
                 · 从 `softKillRed` 选取的号码至多 2 个（建议默认 1 个）；
                 · 其余从既不在硬杀也不在软杀的号码池中选取（建议默认 3 个）；
                 · 默认配比「硬杀2 + 软杀1 + 非杀3」，可依形态/冷热/分区约束在
                   「硬1–3 + 软0–2 + 非杀2–4」范围内浮动，但硬杀不得超过 3 个；
                 · 若 `hardKillRed` 条数不足 2，可从软杀/非杀池补足；
                   若 `softKillRed` 为空，则从非杀池补足；
               - 若 `killNumbers` 为 null 或各清单为空，则忽略本条约束，
                 退回冷热/形态/和值/跨度等结构约束选号。
            5. 【冷热温硬约束】若报告存在 `coldHotAnalysis` 字段：
               - `redHotBalls` / `redWarmBalls` / `redColdBalls` 为红球热/温/冷号清单，
                 `blueHotBalls` / `blueWarmBalls` / `blueColdBalls` 为蓝球热/温/冷号清单；
               - 须直接使用上述清单判定每个候选号码的冷热档位，**不得**再自行推断冷热；
               - 若 `coldHotAnalysis` 为 null，则冷热约束降为软参考，仍须满足杀号与结构硬约束。
            6. 【红球和值与跨度】分两层，禁止把 forecast 当硬锁：
               - 【安全网·真硬】所有 6 红球：和值 ∈ [90, 130]、跨度 ∈ [16, 28]；
                 越界须在输出前调整。这是结构底线，与 dueWindow/confidence 无关。
               - 【偏好中心·软】`featureForecast.span` / `sumRange` 只指示偏好中心，回测整维未中较多。
                 允许落在安全网内但偏离 forecast。若 value 连多期不变（如和值 85-90、跨度 26-30），
                 视为粘性中心而非规律，整包不追；value 落在安全网外时最多一组在网边缘试探。
               - 不得为贴 span/sumRange 而牺牲杀号/冷热/分区；禁止把该组拉出安全网去贴 forecast。
            7. 【红球奇偶与质合上限硬约束】所有红球输出（adjustedRedBalls、
               finalRecommendation.singleTickets.redBalls、
               finalRecommendation.complexTicket.redBalls）必须同时满足：
               - 奇数个数 ≤ 5（禁止 6 个红球全为奇数，即禁止 6:0 奇偶比）；
               - 质数个数 ≤ 4（质数取 2,3,5,7,11,13,17,19,23,29,31，1 视为合数；禁止 ≥5 个质数）。
               任一条件不满足即视为违规，须在输出前自行调整号码至合规。
            8. 【形态分层使用】报告必有 `featureForecast`（含 gapTrend/eta/dueWindow/confidence/reason）：
               各维独立评分，常自相矛盾；dueWindow 对理论众数几乎总为 true（含刚出、eta 已大幅为负），
               confidence 只是 Top1/Top2 分差、不是命中率，score 高也不代表会开出。
               冲突优先级：杀号硬约束 > 冷热/分区/连号/奇偶质合上限/和值跨度安全网 > 比例维分散假设 > 较弱形态维。
               【覆盖检查·先做】若某维 value∪alternatives 几乎盖住该维全部合法取值
               （蓝球大小奇偶 ≥3/4、蓝球012=3/3、奇偶已含 3:3+4:2+2:4），则该维不作选号过滤，
               只可作组间标签；覆盖已满时禁止再要求「一组贴主推、一组贴备选」。
               （1）比例维 oddEven/bigSmall/primeComposite：含备选命中高是因为备选盖住高频桶，主推仍常落空。
                   一组偏均衡（如 3:3 / 2:4），一组走另一侧备选（4:2 或 2:4）；不要全组同一比；
                   1:5/0:6 不要当默认，也不要当绝对禁区。与杀号/冷热/分区冲突时可弃。
               （2）蓝球：硬约束是杀号+冷热。形态先覆盖检查。
                   cooling 且遗漏=0（reason 含「理论众数刚出仍主推」）时不要追 value，
                   二元维（大/小、奇/偶）改走对立备选。blueRatio012 通常覆盖满，忽略。
                   blueBigSmallOddEven 覆盖未满时，多组蓝球应分散大小或奇偶，禁止全部挤同一号。
               （3）较弱维 ratio012/span/sumRange/sumTail/threeZone：只作方向，禁止硬锁/整组重调。
                   排除清单中的值仍可能开出，也可能漏进 alternatives；未列出 ≠ 禁选。
               （4）区个数与 threeZone 经常对不上、甚至 zone1+zone2+zone3≠6：禁止四维联立去硬凑。
                   以分区结构底线为准（避免某区 0 或 ≥5，允许某区=4）；threeZone 只作均衡偏好。
                   禁止为贴 forecast 去造 0:6、6:0、某区 0/5。
               （5）权重只看 gapTrend/遗漏/eta/reason，禁止用 dueWindow×confidence 给 value 加权：
                   cooling+遗漏0 或 reason 含「刚出仍主推」或 eta≤-3 → 降权 value、勿追；
                   heating 且遗漏>0 → 可略偏 value，仍非必须；
                   confidence<0.15（含 0）→ 该维无区分度，忽略。
                   「高频黏性连出」仅对蓝球奇偶这类二元维可略信，红球比例维仍只软参考。
               （6）组间分散要分散真能分开的维：奇偶 4:2 vs 2:4、大小 3:3 vs 4:2、
                   蓝球大 vs 小（覆盖未满时）。覆盖已满的维上拆「主推/备选」是假分散。
                   较弱维允许两组都偏离。
               （7）蓝球口径：1-8 小、9-16 大；奇偶按 n%2；大小奇偶为 小奇/小偶/大奇/大偶；
                   012路按 n%3（0路=3,6,9,12,15；1路=1,4,7,10,13,16；2路=2,5,8,11,14）。
            9. 【三区比预测软约束】若报告存在 `predictedThreeZoneRatio` 字段：
               - `candidates` 为下一期 Top-K 候选三区比及概率，`lastRatio` 为最近一期实际三区比；
               - 与 `featureForecast.threeZone` 都是软参考，都不得压过分区结构底线；
               - 冲突时优先较均衡形态（2:2:2 / 1:2:3 / 2:1:3 / 1:3:2 / 3:2:1），
                 不要为追最高概率去造某区 0 或 ≥5；
               - 不得强行追求概率最高的单一形态而违反杀号/冷热/和值/跨度/连号；
               - 若 `predictedThreeZoneRatio` 为 null，则仅软参考 `featureForecast.threeZone`。
            10. 【趋势均线补充约束】若报告存在 `trendAnalysis` 字段（堆叠 + MA5 斜率相位）：
               - `reboundingRedBalls` / `reboundingBlueBalls`：空头或交叉但斜率向上（回暖），
                 补号时**最优先**，禁止因「空头」回避；
               - `risingRedBalls` / `risingBlueBalls`：多头且斜率未下行，次优先；
               - `coolingRedBalls` / `coolingBlueBalls`：多头但斜率下行，谨慎；
               - `fallingRedBalls` / `fallingBlueBalls`：空头且斜率未上行（真趋冷），可降权但勿整池硬删；
               - 补号优先级：回暖 > 上升 > 平稳/未入榜 > 转弱 > 真趋冷；
               - 不得违反杀号/冷热/和值/跨度安全网，趋势仅作补号倾向；
               - 若 `trendAnalysis` 为 null，则忽略本条约束。
            11. 【组间差异】{count} 组方案须互有差异（至少红球或蓝球不同），须分别侧重不同形态假设
               （比例维均衡 / 比例维另一侧 / 温冷回补 / 分区均衡等），禁止 {count} 组完全相同，
               也禁止 {count} 组都贴同一套 featureForecast.value；覆盖已满的维不要当组间差异来源。
            12. 【上期开奖号码硬约束】若入参提供上一期开奖号码（lastRedBalls / lastBlueBall，
               见下方【上期开奖号码】区块），所有红球与蓝球输出（adjustedRedBalls、adjustedBlueBall、
               finalRecommendation.singleTickets.redBalls/blueBall、
               finalRecommendation.complexTicket.redBalls/blueBalls）必须同时满足：
               - 红球重号上限：当期 6 红球中，与上期红球（lastRedBalls）重合的号码至多 1 个（最多 1 个重号）；
               - 蓝球排斥：当期蓝球不得等于上期蓝球（lastBlueBall）；
               - 蓝红互斥：上期蓝球（lastBlueBall）不得出现在当期任何红球中；
               - 红球连号相邻排斥：若上期红球中存在 2 连号（相邻两个连续号，如 6、7），
                 则当期红球不得包含该连号两端的相邻号（即 5 与 8）；
                 多组连号时对每组连号分别套用本规则；连号长度 ≥3 时按相邻两两拆分
                 （如 6、7、8 视作含 6-7 与 7-8 两组连号，禁 5、8 与 6、9）。
               - 若 lastRedBalls 为空或 null，则忽略本条全部约束。
               - 优先级：杀号硬约束 > 上期号码约束 > 冷热/分区/连号/奇偶质合/和值跨度安全网 > 比例维分散假设 > 较弱形态维。
                 若上期连号相邻排斥与连号结构底线（最长≤2、至多 1 组 2 连号）冲突，
                 以连号结构底线为准并尽量满足上期约束。

            【特征分析报告】
            {report}

            【上期开奖号码】
            上期红球：{lastRedBalls}
            上期蓝球：{lastBlueBall}
            （若为空则忽略【上期开奖号码硬约束】全部规则）

            【推荐规则】
            一、生成 {count} 组单式（写入 adjustedTickets，恰好 {count} 组；禁止输出 complexTicket）
            1. 每组输出 6 红 + 1 蓝；红球 1-33 互异升序；蓝球 1-16。
            2. 因无原始号码：originalRedBalls / originalBlueBall 置 null；
               redReplacements 置空数组 []；blueReplacement 置 null。
            3. adjustedRedBalls / adjustedBlueBall 即为本组推荐单式。
            4. 单式硬性结构：
               - 冷热（取自 coldHotAnalysis）：理想热2-3、温2-3、冷1-2；热号≤3、冷号≤2；
               - 分区：一区(1-11)/二区(12-22)/三区(23-33) 尽量避免「某区 0 个」或「某区≥5」；某区=4 可接受；
               - 连号：最长连号长度≤2，且至多 1 组 2 连号；
               - 和值/跨度先满足安全网 [90,130]/[16,28]；粘性中心（如 85-90）整包不追；
               - 奇数个数 ≤ 5、质数个数 ≤ 4（质数取 2,3,5,7,11,13,17,19,23,29,31，1 视为合数）；
               - 形态：比例维（奇偶/大小/质合）一组偏均衡、一组走另一侧备选，冲突可弃；
                 较弱维（012路/跨度/和值/和尾/三区比）只作方向、不否决；
                 覆盖已满的维不作过滤，也不要再拆主推/备选；
                 蓝球先覆盖检查：cooling+刚出勿追 value，未满时分散大小或奇偶；
               - 杀号配比：默认「硬杀2 + 软杀1 + 非杀3」，可在「硬1–3 + 软0–2 + 非杀2–4」浮动，
                 红球硬杀命中数不得超过 3；蓝球严禁选 hardKillBlue，优先排除 softKillBlue，
                 仅在无可用非硬杀蓝时从 softKillBlue 降级选取。
               - 上期号码约束：6 红球与上期红球（lastRedBalls）重合至多 1 个；
                 蓝球不得等于上期蓝球（lastBlueBall）；红球不得包含上期蓝球；
                 红球不得包含上期红球任意 2 连号两端的相邻号；lastRedBalls 为空时忽略。
            5. id 可用 "R1"/"R2"/...；reason 说明本组比例维走均衡侧还是另一侧备选，以及选号依据（150 字内）。

            二、最终推荐包（必填 finalRecommendation，全响应仅此一份）
            综合特征报告 + 各组推荐单式，凝练成最终可购买方案，包含两部分：

            （1）两组单式 singleTickets（恰好 2 组，每组 6 红 + 1 蓝、totalBets=1）
            - 两组必须针对不同形态假设：一组比例维偏均衡（如 3:3），另一组走另一侧备选；
              不要两组都追逐同一套 forecast；覆盖已满的维不要再拆主推/备选。
            - 须满足全部真硬约束（杀号/冷热/和值跨度安全网/奇偶/质合/分区/连号/上期号码约束）；
            - 较弱形态维允许两组都偏离 forecast；
            - name、basis 说明形态假设与选号依据（200 字内）。

            （2）一组复式 complexTicket（红球 7-10、蓝球 2-5，全响应仅此一组）
            - 不要与某一组 adjustedTickets 简单等同；超热号入选总数红球≤3、蓝球≤2。
            - 红球 7-10、蓝球 2-5，互异升序，且必须同时满足：
              - 【形态·覆盖而非锁定】复式目的是让不同 6 红子集分别覆盖比例维的两侧假设；
                禁止要求整组同时落入全部 15 维；较弱维不作为入选门槛。
                蓝球 2-5 先覆盖检查：已满则分散冷温/分区即可，未满才补对立大小或奇偶。
              - 三区比：软参考 featureForecast.threeZone 与 predictedThreeZoneRatio.candidates，
                复式内不同 6 子集可覆盖 1-2 个较均衡三区比，禁止整包做成某区 0 或 ≥5。
              - 和值/跨度：复式内应存在至少一组 6 红子集满足安全网 [90,130]/[16,28]；
                不要求所有子集都贴 forecast。
              - 奇偶/质合硬约束：6 红球子集（任取 6 个）的奇数个数 ≤ 5、质数个数 ≤ 4
                （质数取 2,3,5,7,11,13,17,19,23,29,31，1 视为合数）；
              - 冷热（取自 coldHotAnalysis）：红球热≤4、温≥2、冷≥2；蓝球热≤2，且含≥1 温、≥1 冷；
              - 杀号配比：红球 hardKillRed 命中数 ≤3、softKillRed 命中数 ≤2；
                蓝球严禁选 hardKillBlue，优先排除 softKillBlue，仅在无可用非硬杀蓝时降级选 softKillBlue；
              - 分区：红球每区≥2；蓝球覆盖≥2 个四分区；
              - 连号：最长≤2，2 连组数≤2；
              - 邻狐传：红球相对上期重号≤2、狐号≥2；蓝球至少 1 个狐号。
              - 【上期号码约束】复式红球中与上期红球（lastRedBalls）重合的号码至多 1 个；
                复式蓝球不得包含上期蓝球（lastBlueBall）；复式红球不得包含上期蓝球；
                复式红球不得包含上期红球任意 2 连号两端的相邻号。
                若 lastRedBalls 为空则忽略本条。
            - totalBets = C(红球个数, 6) × 蓝球个数，需准确；
              name、basis、conclusion 说明为何选这套（各 200 字内）。
              conclusion 必须说明：如何用温冷号分散、限制连号与重号，
              以及两组单式如何分别走比例维均衡侧与另一侧备选，
              降低「热号集体回冷 + 号段错位 + 蓝球追热 + 形态全组赌同一套 forecast」四类风险。

            【输出要求】
            - 严格输出 JSON，结构必须符合以下 Schema：
            {format}
            - 不得输出 Markdown 代码块、解释文字或推理过程，只输出纯 JSON。
            - adjustedTickets 必须恰好 {count} 组；每个 AdjustedTicket 禁止包含 complexTicket 字段。
            - finalRecommendation 必填：singleTickets 恰好 2 组；
              complexTicket 恰好 1 组。
            - 所有号码为整数；红球 1-33 互异升序；蓝球 1-16 互异升序。
            - 若初稿违反任一真硬约束，须在输出前自行修正至合规，不得输出违规方案。
              形态维未贴 forecast 不属于违规。
            """;
}
