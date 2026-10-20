package com.my.project.application.support.analyse;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.llm.bo.KillNumberResultBo;
import com.my.project.application.support.analyse.kill.KillBallSupport;
import com.my.project.application.support.analyse.kill.XieXiuKillBallStrategy;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Triple;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** 红球使用 test_20_zh 提取规则；蓝球汇总历史规则及邻号、近6期重复、遗漏35期规则。 */
public final class KillBallUtils {
    private static final XieXiuKillBallStrategy BLUE_STRATEGY = new XieXiuKillBallStrategy();

    private KillBallUtils() { }

    public static KillNumberResultBo calculate(List<HistoryRecord> historyRecords) {
        return calculate(historyRecords, null);
    }

    /** 下期号码仅供调试展示，不参与红蓝杀号计算。 */
    public static KillNumberResultBo calculate(List<HistoryRecord> historyRecords,
                                              Triple<List<Integer>, String, Integer> nextWinBalls) {
        return result(historyRecords, RedBallKillUtils.calculate(historyRecords), nextWinBalls);
    }

    /** 回测或已知开奖日时显式提供目标日期，不以当前系统日期替代历史日期。 */
    public static KillNumberResultBo calculate(List<HistoryRecord> historyRecords,
                                              Triple<List<Integer>, String, Integer> nextWinBalls,
                                              LocalDate targetDrawDate) {
        return result(historyRecords, RedBallKillUtils.calculate(historyRecords, targetDrawDate), nextWinBalls);
    }

    private static KillNumberResultBo result(List<HistoryRecord> history,
                                            List<KillNumberResultBo.KillItemBo> redKills,
                                            Triple<List<Integer>, String, Integer> nextWinBalls) {
        var last = history.getFirst();
        List<KillNumberResultBo.KillItemBo> blueKills = new ArrayList<>();
        // 最近四期蓝球 + 上期红球中≤16的号码 + 扩展后的邪修杀蓝。
        history.stream().limit(4).map(HistoryRecord::getSpecial)
            .map(ball -> KillBallSupport.kill("上期出现", ball)).forEach(blueKills::add);
        List.of(last.getNum1(), last.getNum2(), last.getNum3(), last.getNum4(), last.getNum5(), last.getNum6())
            .stream().filter(ball -> ball <= 16).map(ball -> KillBallSupport.kill("上期开出-红球", ball)).forEach(blueKills::add);
        if (BLUE_STRATEGY.enabled()) blueKills.addAll(BLUE_STRATEGY.killBlue(history));
        // 原有红蓝计算保持原样，只追加统一回测已启用的头条/小红书规则。
        var context = new UnifiedKillBallRules.Context(history, null);
        redKills = Stream.concat(redKills.stream(), collectedKills(context, false).stream()).distinct().toList();
        blueKills.addAll(collectedKills(context, true));
        var result = KillNumberResultBo.builder().hardKillRed(redKills)
            .hardKillBlue(blueKills.stream().distinct().toList()).build();
        if (nextWinBalls != null) {
            System.out.println("第" + nextWinBalls.getMiddle() + "开奖：" + nextWinBalls.getLeft() + " + " + nextWinBalls.getRight());
            System.out.println("红球杀号" + redKills.size() + "，误杀：" + CollectionUtils.intersection(
                redKills.stream().map(KillNumberResultBo.KillItemBo::getBall).toList(), nextWinBalls.getLeft())
                + "；蓝球杀号" + result.getHardKillBlue().size() + "，误杀：" + result.getHardKillBlue().stream()
                .anyMatch(item -> item.getBall().equals(nextWinBalls.getRight())));
        }
        return result;
    }

    private static List<KillNumberResultBo.KillItemBo> collectedKills(UnifiedKillBallRules.Context context, boolean blue) {
        return UnifiedKillBallRules.catalog().stream().filter(rule -> rule.blue() == blue)
            .flatMap(rule -> rule.kills(context).stream().map(ball -> KillNumberResultBo.KillItemBo.builder()
                .ball(ball).score(1.0).source(rule.source() + ":" + rule.id() + " " + rule.name())
                .reason(rule.name()).build())).toList();
    }
}
