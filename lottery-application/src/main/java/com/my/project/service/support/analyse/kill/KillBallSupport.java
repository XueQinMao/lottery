package com.my.project.service.support.analyse.kill;

import com.my.project.llm.bo.KillNumberResultBo;
import org.apache.commons.lang3.tuple.Pair;

import java.util.function.Function;

/**
 * 杀号项构造共用方法。
 */
public final class KillBallSupport {

    private KillBallSupport() {
    }

    public static Function<Pair<Integer, Double>, KillNumberResultBo.KillItemBo> boMapper(String source) {
        return pair -> KillNumberResultBo.KillItemBo.builder().ball(pair.getLeft()).score(pair.getValue())
            .source(source).build();
    }

    public static KillNumberResultBo.KillItemBo kill(String source, int ball) {
        return boMapper(source).apply(Pair.of(ball, 1.0));
    }
}
