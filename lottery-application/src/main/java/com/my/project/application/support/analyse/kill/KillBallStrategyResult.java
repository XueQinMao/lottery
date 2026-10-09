package com.my.project.application.support.analyse.kill;

import com.my.project.llm.bo.KillNumberResultBo;

import java.util.List;

/**
 * 单策略杀号结果。
 *
 * @param redKills              红球候选
 * @param blueKills             篮球候选（策略完整输出，含调试用）
 * @param aggregationBlueKills  参与篮球硬杀汇总的子集；默认与 {@code blueKills} 相同
 */
public record KillBallStrategyResult(
    List<KillNumberResultBo.KillItemBo> redKills,
    List<KillNumberResultBo.KillItemBo> blueKills,
    List<KillNumberResultBo.KillItemBo> aggregationBlueKills
) {
    public KillBallStrategyResult(List<KillNumberResultBo.KillItemBo> redKills,
        List<KillNumberResultBo.KillItemBo> blueKills) {
        this(redKills, blueKills, blueKills);
    }

    public static KillBallStrategyResult empty() {
        return new KillBallStrategyResult(List.of(), List.of(), List.of());
    }
}
