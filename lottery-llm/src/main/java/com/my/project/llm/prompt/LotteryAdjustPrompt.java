package com.my.project.llm.prompt;

/**
 * LotteryAdjustPrompt
 *
 * <p>双色球号码 Prompt（两套，输出 Schema 相同；共享规则只维护一份）：
 * <ol>
 *   <li>{@link #USER_PROMPT}：调优模式（tickets 非空）— 逐组单式调整 + 最终推荐包</li>
 *   <li>{@link #RECOMMEND_PROMPT}：推荐模式（tickets 为空）— 按报告生成 N 组单式 + 最终推荐包</li>
 * </ol>
 *
 * <p>分层：真硬必须满足；结构底线尽量满足、冲突让给真硬；forecast / 三区 / 趋势为软偏好，禁止硬锁。
 * 最终推荐包：2 组单式 + 1 组复式（红 7-10 / 蓝 2-5）。
 *
 * <p>占位符：{report} / {tickets} / {count} / {format} / {lastRedBalls} / {lastBlueBall} / {userRequirement}
 *
 * @author 刘强
 * @version 2026/09/15
 **/
public final class LotteryAdjustPrompt {

    private LotteryAdjustPrompt() {
    }

    /**
     * 口径、真硬、结构底线、软偏好、冲突优先级（两套 Prompt 共用，只出现一次）。
     */
    private static final String SHARED_RULES = """
            【口径】
            - 红三区：1-11 / 12-22 / 23-33。蓝四分区：1-4 / 5-8 / 9-12 / 13-16。
            - 蓝球：1-8 小、9-16 大；奇偶按 n%2；012 路按 n%3
              （0路=3,6,9,12,15；1路=1,4,7,10,13,16；2路=2,5,8,11,14）。
            - 质数：2,3,5,7,11,13,17,19,23,29,31；1 视为合数。
            - 邻狐传（相对上期）：重号=同号；邻号=±1；狐号=±2。
            - 冷热档位只认报告 `coldHotAnalysis` 的热/温/冷清单，禁止自行推断。
            - 覆盖已满：蓝大小奇偶 ≥3/4、蓝012=3/3、奇偶已含 3:3+4:2+2:4
              → 该维不作选号过滤，只可作组间标签；禁止再拆「主推/备选」。

            【真硬·输出前必须满足；违反须自修后再输出】
            1. 号码：单式红 6 个、蓝 1 个；复式红 7-10、蓝 2-5。均为整数、互异、升序；红 1-33、蓝 1-16。
            2. 杀号（`killNumbers` 为空则忽略）：
               - 蓝球严禁 `hardKillBlue`，优先选非硬杀号码。
               - 红球不整池排除 `hardKillRed`，也不要全从硬杀选。单式 6 红：hardKillRed 0-2 个；
                 复式红 7-10：hardKillRed 1-3 个。
               - 红球硬杀分布底线：所有单式中至多一组包含 `hardKillRed` 中的号码，且该组 hardKillRed 个数为 1-2；
                 其余所有单式红球均不得出现 `hardKillRed` 中的号码。复式可从 `hardKillRed` 中选 1-3 个。
            3. 6 红和值 ∈ [90,130]、跨度 ∈ [16,28]。禁止为贴 forecast 拉出安全网。
            4. 6 红奇数个数 ≤5（禁 6:0）；质数个数 ≤4。
            5. 红球尾号（号码 % 10）：同一尾号在一组 6 红中至多 3 个，禁止 4 同尾
               （如 03,13,23,33 不得同时出现于一组）。默认每个尾号 1 个、尽量分散，默认一个尾号1个，
               最多允许个别尾号 2-3 个；见【软偏好】尾号分散。
            6. 上期（`lastRedBalls` 为空则忽略本条）：
               - 预测号码与上期开奖红球的重号最多 1-2 个：单式 6 红重号 ≤2，复式红球重号同样 ≤2。
                 蓝球 ≠ lastBlueBall；上期蓝球不得出现在当期任何红球中。
               - 上期每个 2 连号（≥3 连按相邻两两拆分，如 6,7,8 → 6-7 与 7-8）两端邻号尽量避开
                 （如 6,7 → 尽量禁 5 与 8）。与「当期连号底线」冲突时，先保当期连号合规，
                 basis 注明放弃了哪一组邻号排斥。
            7. 用户要求与安全网求交后执行，见【用户附加要求】。

            【结构底线·尽量满足，冲突让给真硬】
            - 红分区：避免某区 0 或 ≥5，允许某区=4；复式每区 ≥2。
            - 连号：单式最长 ≤2 且至多 1 组 2 连；复式最长 ≤2、2 连组数 ≤2。
            - 冷热（有 `coldHotAnalysis` 时）：单式热 ≤3、冷 ≤2，理想热2-3 / 温2-3 / 冷1-2；
              无该字段则冷热降为软参考。
            - 邻狐传：单式尽量含狐号，避免默认追邻号/重号/超热；复式红狐号 ≥2、蓝 ≥1 个狐号。
              复式重号上限以真硬 ≤2 为准（与上期红球最多 1-2 个，不要再写成 ≤1）。
            - 尾号分散：6 红尾号（% 10）默认每尾 1 个、尽量覆盖更多不同尾；
              真硬已禁 4 同尾，此处仅鼓励 2-3 同尾只作个别尾号、不要多尾扎堆。

            【软偏好·可弃。禁止为此突破真硬，禁止仅因未贴 forecast 而整组重调】
            1. `featureForecast` 是形态先验，不是开奖答案。value ∪ alternatives 只是偏好池，未列出 ≠ 禁选。
               禁止 15 维同时硬锁；禁止所有组贴同一套 value；不得臆造「高频号表」。
               dueWindow / confidence 不是命中率，禁止用它们给 value 加权。
               只看 gapTrend / 遗漏 / eta / reason：cooling+遗漏0、reason 含「刚出仍主推」、或 eta≤-3 → 勿追 value；
               heating 且遗漏>0 → 可略偏，仍非必须；confidence<0.15（含 0）→ 忽略该维。
            2. 先做覆盖检查。覆盖已满的维不要当组间差异来源。
            3. 比例维 oddEven / bigSmall / primeComposite：一组偏均衡（如 3:3 / 2:4），
               一组走另一侧备选（4:2 或 2:4）；不要全组同一比。1:5 / 0:6 不要当默认，也不要当绝对禁区。
            4. 较弱维 ratio012 / span / sumRange / sumTail / threeZone：只作方向。
               span / sumRange 是偏好中心；value 连多期不变（如和值 85-90）视为粘性中心，整包不追；
               value 在安全网外时最多一组在网边缘试探。
            5. 区个数与 threeZone 经常对不上：以分区结构底线为准，禁止为贴 forecast 造某区 0/5。
            6. `predictedThreeZoneRatio` 与 threeZone 都是软参考；优先较均衡比
               （2:2:2 / 1:2:3 / 2:1:3 / 1:3:2 / 3:2:1）。null 则只软参考 threeZone。
            7. `trendAnalysis` 补号倾向：回暖 > 上升 > 平稳/未入榜 > 转弱 > 真趋冷；
               禁止因「空头」回避回暖号。null 则忽略。
            8. `risingTrendRedBalls` 是遗漏均线抬头红球池（rising + rebounding 合并）：
               选号时优先从该池中挑选红球，不足或与其他真硬/结构底线冲突时再试其他号码；
               禁止因「未在池中」整组重调，也禁止整组 6 红全部出自该池导致扎堆。null 则忽略。
            9. 蓝球形态硬约束是杀号+冷热。覆盖已满则忽略形态；未满时多组应分散大小或奇偶，禁止全挤同一号。
               blueRatio012 通常覆盖满，忽略。

            【冲突优先级】（只此一份）
            杀号硬约束 > 上期号码约束 > 冷热/分区/连号/奇偶质合/和值跨度安全网
            > 已裁剪的用户附加要求 > 比例维分散假设 > 较弱形态维。
            """;

    /**
     * 用户附加要求。占位符：{userRequirement}
     */
    private static final String USER_REQUIREMENT_BLOCK = """
            【用户附加要求】
            {userRequirement}
            （空或「无」则忽略）
            叠加到所有输出号码。不得突破真硬；与和值/跨度安全网求交，仍可行则按交集硬执行，
            完全越界则裁到网内最近侧（和值例：>100 → (100,130]；>150 → 贴近 130；<80 → 贴近 90。跨度同理）。
            reason / basis 须注明采纳了哪些要求、哪些被裁到安全网。
            """;

    /**
     * 报告与上期开奖。占位符：{report} / {lastRedBalls} / {lastBlueBall}
     */
    private static final String DATA_BLOCK = """
            【特征分析报告】
            {report}

            【上期开奖号码】
            上期红球：{lastRedBalls}
            上期蓝球：{lastBlueBall}
            （若为空则忽略上期真硬全部规则）

            """;

    private static final String ADJUST_STEPS = """
            【调优步骤】（仅单式；各组 AdjustedTicket 禁止输出 complexTicket）
            对每一组写入 adjustedTickets：先核验真硬与结构底线，再叠加比例维分散；较弱维不否决。
            红球 1-33 互异升序共 6 个。默认局部替换 1-2 个，写出 from/to/basis，替换后重新升序：
            - 热≥4：换出 1-2 个超热→温或遗漏适中的冷，使热≤3（basis：热号回冷防御）。
            - 冷≥3：换出 1-2 个极冷→温热，使冷≤2（basis：冷号复苏平衡）。
            - 号段失衡 / 连号违规 / 和值跨度越网或未满足用户交集 / 奇偶质合越界 /
              尾号 4 同尾 / 杀号配比越界 / 上期约束违规：按真硬与结构底线修复，basis 点明校准项。
            - 已均衡且真硬合规：可不换。未贴 forecast 不是必须替换的理由。
            - 【极端整组重调】仅当局部替换仍无法同时满足真硬
              （如单式 hardKillRed ≥3，或含 hardKillRed 的单式超过一组，
               或蓝球落入 hardKillBlue 难单点替换，或多条真硬同时冲突）。
              允许一次替换全部 6 红（可同时换蓝），按报告从零重选合规单式；
              补号优先 `risingTrendRedBalls`（均线抬头池），其次 `rebounding*` / `rising*` 与温号池。redReplacements 须覆盖全部 from→to；
              reason 首句写「极端整组重调」及触发原因。禁止因较弱维偏离而整组重调。
            蓝球：禁 hardKillBlue；优先非硬杀；过热换温；优先狐号或温号，勿默认追邻号/重号/超热；≠上期蓝。
            已整体合理（含较弱维偏离 forecast）仍须输出 adjustedRedBalls / adjustedBlueBall。
            reason 150 字内：是否触达真硬、比例维走均衡侧还是另一侧、用户要求采纳/裁剪。
            """;

    private static final String RECOMMEND_STEPS = """
            【推荐步骤】从零生成 {count} 组单式；各组 AdjustedTicket 禁止输出 complexTicket
            1. 每组 6 红 + 1 蓝。originalRedBalls / originalBlueBall 置 null；
               redReplacements=[]；blueReplacement=null。adjusted* 即推荐单式。
            2. 满足真硬 + 结构底线。比例维一组偏均衡、一组走另一侧；覆盖已满维不要当组间差异。
               尾号（% 10）默认每尾 1 个、最多 3 同尾，禁止 4 同尾。
            3. {count} 组须互异（红或蓝不同），禁止都贴同一套 value。
               可分别侧重：比例均衡 / 比例另一侧 / 温冷回补 / 分区均衡。
            4. 杀号：单式至多一组包含 hardKillRed（个数 1-2，默认 1），其余单式 hardKillRed 必须为 0；蓝球规则同真硬。
            5. 红球优先从 `risingTrendRedBalls`（均线抬头池）中挑选，不足或冲突时再试其他号码；
               但禁止整组 6 红全部出自该池导致扎堆。null 则忽略。
            6. id 用 R1/R2/...；reason 说明比例维侧别与选号依据（150 字内）。
            """;

    private static final String FINAL_RECOMMENDATION = """
            【最终推荐包】必填 finalRecommendation，全响应仅此一份
            （1）singleTickets 恰好 2 组（6 红 + 1 蓝，totalBets=1）
            两组必须针对不同形态假设：一组比例维偏均衡，一组走另一侧备选；不要都追同一套 forecast。
            须满足真硬 + 结构底线；较弱维允许两组都偏离。name / basis 200 字内。

            （2）complexTicket 恰好 1 组（红 7-10、蓝 2-5）
            不要与某一组 adjustedTickets 简单等同。热号（redHotBalls / blueHotBalls）入选：红≤3、蓝≤2。
            - 形态：用不同 6 红子集覆盖比例维两侧；禁止整组锁 15 维；较弱维不作为入选门槛。
              蓝 2-5 覆盖已满则只分散冷温/分区，未满才补对立大小或奇偶。
            - 三区软参考，禁止整包做成某区 0 或 ≥5。
            - 和值/跨度：至少存在一组 6 红子集落在安全网（若有用户交集还须落在交集内）；
              不要求所有子集贴 forecast。
            - 奇偶/质合：不要求枚举全部 C(n,6)；至少存在一组 6 红子集奇数≤5 且质数≤4。
              整包质数不要过密（建议质数个数 ≤ 红球数-3）。
            - 冷热：红温≥2、冷≥2；蓝热≤2，且含 ≥1 温、≥1 冷。
            - 杀号：单式至多一组包含 hardKillRed（个数 1-2，默认 1），其余单式 hardKillRed 必须为 0；复式 hardKillRed 1-3 个；蓝球规则同真硬。
            - 分区：红每区≥2；蓝覆盖 ≥2 个四分区。连号：最长≤2，2 连组数≤2。
            - 尾号：红同一尾号（% 10）≤3，禁止 4 同尾；尽量覆盖 ≥4 个不同尾。
            - 邻狐传：红狐号≥2、蓝≥1 狐号；重号上限走真硬：与上期红球最多 1-2 个（≤2）。上期约束同真硬。
            totalBets = C(红球个数, 6) × 蓝球个数，须准确。name / basis 200 字内。
            根字段 conclusion（不要写在 complexTicket 内）须说明：如何用温冷分散、限制连号与重号，
            以及两组单式如何分走比例维两侧，降低「热号集体回冷 + 号段错位 + 蓝球追热 + 全组赌同一套 forecast」四类风险。
            """;

    private static final String OUTPUT_COMMON = """
            【输出要求】
            - 只输出纯 JSON，结构必须符合以下 Schema，不得输出 Markdown、解释或推理过程：
            {format}
            - 每个 AdjustedTicket 禁止包含 complexTicket 字段。
            - finalRecommendation 必填：singleTickets 恰好 2 组；complexTicket 恰好 1 组。
            - 若初稿违反任一真硬，须在输出前自行修正；形态维未贴 forecast 不属于违规。
            - 若存在用户附加要求：所有单式须满足「用户要求 ∩ 安全网」；
              复式须存在至少一组 6 红子集同时满足该交集。
            """;

    public static final String USER_PROMPT = """
            请基于以下双色球特征分析报告，对给出的预测号码组逐一调优（仅单式）；
            最后综合输出【一组】最终可购买方案：2 组单式 + 1 组复式。

            """ + SHARED_RULES + """

            """ + DATA_BLOCK + USER_REQUIREMENT_BLOCK + """

            【待调整的预测号码组】
            {tickets}

            """ + ADJUST_STEPS + """

            """ + FINAL_RECOMMENDATION + """

            """ + OUTPUT_COMMON + """
            - adjustedTickets 顺序与输入 tickets 一致，id 回填输入 id（若有）。
            """;

    /**
     * 推荐模式 Prompt（tickets 为空时使用）。
     * <p>占位符：{report} / {count} / {format} / {lastRedBalls} / {lastBlueBall} / {userRequirement}
     */
    public static final String RECOMMEND_PROMPT = """
            请基于以下双色球特征分析报告，直接推荐 {count} 组可购买的单式；
            最后综合输出【一组】最终可购买方案：2 组单式 + 1 组复式。
            本组任务是「从零生成」，不是对已有号码调优；输出 Schema 与调优模式相同。

            """ + SHARED_RULES + """

            """ + DATA_BLOCK + USER_REQUIREMENT_BLOCK + """

            """ + RECOMMEND_STEPS + """

            """ + FINAL_RECOMMENDATION + """

            """ + OUTPUT_COMMON + """
            - adjustedTickets 必须恰好 {count} 组。
            """;
}
