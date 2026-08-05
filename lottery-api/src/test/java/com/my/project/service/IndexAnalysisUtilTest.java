package com.my.project.service;

import com.alibaba.fastjson.JSON;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.service.support.analyse.IndexAnalysisUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 用全量历史开奖跑信息指数工具，只打印结果，不接入推荐/调优主流程。
 */
@SpringBootTest
class IndexAnalysisUtilTest {

    @Resource
    private IHistoryRecordRepository historyRecordRepository;

    @Test
    void printInformationIndexFromHistory() {
        List<HistoryRecord> windows =
                historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 30").list();

        windows.forEach(w -> {
            List<HistoryRecord> past = historyRecordRepository.lambdaQuery()
                    .le(HistoryRecord::getOpenDate, w.getOpenDate())
                    .orderByDesc(HistoryRecord::getOpenDate).list();
            var analyze = IndexAnalysisUtils.analyze(past);

            HistoryRecord next = historyRecordRepository.lambdaQuery()
                    .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            List<Integer> nextWins =
                    List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());
//            //红球选球
//            var chooseReds = analyze.getRedBalls().stream()
//                    .filter(r -> Objects.equals(r.getIndexTb(), "大")).toList();
            var killReds = analyze.getRedBalls().stream()
                    .filter(r -> !(Objects.equals(r.getIndexTb(), "小") && r.getConfidence() >= 0.05)).toList();
//            //篮球选球
//            var chooseBlues = analyze.getBlueBalls().stream()
//                    .filter(r -> !(Objects.equals(r.getIndexTb(), "小") && r.getConfidence() >= 0.1)).toList();
            var killBlues = analyze.getBlueBalls().stream()
                    .filter(r -> !(Objects.equals(r.getIndexTb(), "小") && r.getConfidence() >= 0.05)).toList();

//            List<Integer> redBalls = chooseReds.stream().map(IndexAnalysisUtils.IndexResult::getBall).toList();
            List<Integer> killsRedBalls = killReds.stream().map(IndexAnalysisUtils.IndexResult::getBall).toList();

//            List<Integer> blueBalls = chooseBlues.stream().map(IndexAnalysisUtils.IndexResult::getBall).toList();
            List<Integer> killsBlueBalls = killBlues.stream().map(IndexAnalysisUtils.IndexResult::getBall).toList();

//            List<Integer> hitPicks = (List<Integer>) CollectionUtils.intersection(redBalls, nextWins);
            List<Integer> missKills = (List<Integer>) CollectionUtils.intersection(killsRedBalls, nextWins);

            System.out.println("第"+w.getPeriod()+"期 "+" 红球杀球/误杀："+killsRedBalls.size()+"/"+missKills.size()
                    +" 篮球杀球/误杀："+killsBlueBalls.size()+"/"+killsBlueBalls.contains(next.getSpecial()));
        });
    }
}
