package com.my.project.service;

import com.alibaba.fastjson.JSON;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.service.enums.FeatureKindEnums;
import com.my.project.service.support.OmissionDueProbabilityUtils;
import com.my.project.service.support.OmissionDueProbabilityUtils.DueStat;
import com.my.project.service.support.OmissionDueProbabilityUtils.Report;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用全量历史开奖跑遗漏到期概率工具，只打印结果，不接入推荐/调优主流程。
 */
@SpringBootTest
class OmissionDueProbabilityTest {

    @Resource
    private IHistoryRecordRepository historyRecordRepository;

    @Test
    void printOmissionDueProbabilityFromHistory() {
        List<HistoryRecord> windows =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 30").list();

        windows.forEach(w ->{
            List<HistoryRecord> past100 = historyRecordRepository.lambdaQuery().le(HistoryRecord::getOpenDate, w.getOpenDate())
                .orderByDesc(HistoryRecord::getOpenDate).last("limit 100").list();
            if (CollectionUtils.size(past100) < 10) {
                throw new IllegalStateException("历史开奖不足，无法计算遗漏到期概率");
            }
            HistoryRecord next = historyRecordRepository.lambdaQuery()
                .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if(Objects.isNull(next)){
                return;
            }
            List<Integer> nextWins =
                List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());
            Map<String, Map<String, Double>> stringMapMap = OmissionDueProbabilityUtils.analyzeHistory(past100);
            Map<String, Double> reds = stringMapMap.get("红球");
            Map<String, Double> blues = stringMapMap.get("蓝球");
            List<Integer> kill0Reds = reds.entrySet().stream().filter(entry -> entry.getValue() <= 0).map(Map.Entry::getKey)
                .mapToInt(Integer::parseInt).boxed().toList();

            List<Integer> kill0blues = blues.entrySet().stream().filter(entry -> entry.getValue() <= 0).map(Map.Entry::getKey)
                .mapToInt(Integer::parseInt).boxed().toList();
            List<Integer> intersection = (List<Integer>) CollectionUtils.intersection(kill0Reds, nextWins);

            System.out.println("红球杀球："+ StringUtils.join(kill0Reds, ","));
            System.out.println("蓝球杀球："+ StringUtils.join(kill0blues, ","));
            System.out.println("第"+w.getPeriod()+"期杀号，红球："+kill0Reds.size()+" 误杀："+intersection.size()+" 蓝球："+kill0blues.size()
                +" 蓝球误杀："+kill0blues.contains(next.getSpecial()));

            var objects = new ArrayList<>();
            for (FeatureKindEnums value : FeatureKindEnums.values()) {
                String apply = value.getFunction().apply(nextWins, next.getSpecial());
                Map<String, Double> stringDoubleMap = stringMapMap.get(value.getLabel());
                Map<String, Double> collect = stringDoubleMap.entrySet().stream().filter(entry -> entry.getValue() > 0)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                System.out.println("第"+w.getPeriod()+"预测"+value.getLabel()+"特征结果："+collect+" 实际结果："+apply+ "  是否命中："
                    +collect.containsKey(apply));
                if(!collect.containsKey(apply)){
                    objects.add(value.getLabel());
                }
            } System.out.println("特征未命中的："+StringUtils.join(objects,","));
            System.out.println("***********************************");
        });

    }
}
