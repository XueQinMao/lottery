package com.my.project.service;

import com.alibaba.fastjson.JSON;
import com.my.project.llm.bo.KillNumberResultBo;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.service.support.analyse.KillBallUtils;
import com.my.project.service.support.analyse.MaAnalysisUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 用全量历史开奖跑信息指数工具，只打印结果，不接入推荐/调优主流程。
 */
@SpringBootTest
class MaAnalysisUtilTest {

    @Resource
    private IHistoryRecordRepository historyRecordRepository;

    @Test
    void printInformationIndexFromHistory() {
        List<HistoryRecord> windows =
                historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 10").list();

        windows.forEach(w -> {
            List<HistoryRecord> past = historyRecordRepository.lambdaQuery()
                    .le(HistoryRecord::getOpenDate, w.getOpenDate())
                    .last(" limit 100")
                    .orderByDesc(HistoryRecord::getOpenDate).list();


            HistoryRecord next = historyRecordRepository.lambdaQuery()
                    .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            MaAnalysisUtils.MaAnalysisResult analyze = MaAnalysisUtils.analyze(past);
            System.out.println(JSON.toJSONString(analyze));
            List<Integer> nextWins =
                    List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());
            List<Integer> redKills = analyze.getRedBalls().stream()
                    .filter(b -> Objects.equals("杀号", b.getAction())).map(MaAnalysisUtils.MaResult::getBall).toList();

            List<Integer> blueKills = analyze.getBlueBalls().stream()
                    .filter(b -> Objects.equals("杀号", b.getAction())).map(MaAnalysisUtils.MaResult::getBall).toList();
            List<Integer> hitPicks = (List<Integer>) CollectionUtils.intersection(redKills, nextWins);
            System.out.println("根据第" + w.getPeriod() + "期数据计算杀球，红球/误杀" + redKills.size() + "/" + hitPicks.size() + " 篮球/误杀：" + blueKills.size() + "/" + blueKills.contains(next.getSpecial()));
            System.out.println("**************************************");
        });
    }


    @Test
    public void test_kill(){
        List<HistoryRecord> windows =
                historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last(" limit 51").list();
        AtomicInteger jxSuccess = new AtomicInteger();
        AtomicInteger zsSuccess = new AtomicInteger();
        AtomicInteger ylSuccess = new AtomicInteger();
        windows.forEach(w -> {
            List<HistoryRecord> past = historyRecordRepository.lambdaQuery()
                    .le(HistoryRecord::getOpenDate, w.getOpenDate())
                    .orderByDesc(HistoryRecord::getOpenDate).list();


            HistoryRecord next = historyRecordRepository.lambdaQuery()
                    .eq(HistoryRecord::getPeriod, String.valueOf(Integer.parseInt(w.getPeriod()) + 1)).one();
            if (Objects.isNull(next)) {
                return;
            }
            KillNumberResultBo calculate = KillBallUtils.calculate(past);
            var killRedMaps = calculate.getHardKillRed().stream().collect(Collectors.groupingBy(KillNumberResultBo.KillItemBo::getSource));
            var softRedMaps = calculate.getSoftKillRed().stream().collect(Collectors.groupingBy(KillNumberResultBo.KillItemBo::getSource));
//            var killBueMaps = calculate.getHardKillBlue().stream().collect(Collectors.groupingBy(KillNumberResultBo.KillItemBo::getSource));

            List<Integer> nextWins =
                    List.of(next.getNum1(), next.getNum2(), next.getNum3(), next.getNum4(), next.getNum5(), next.getNum6());
            System.out.println("第"+next.getPeriod()+" 期 开奖记录"+StringUtils.join(nextWins, ","));

            killRedMaps.forEach((key, value) -> {
                List<Integer> killReds = value.stream().map(KillNumberResultBo.KillItemBo::getBall).toList();
                List<Integer> softKillRed = CollectionUtils.emptyIfNull(softRedMaps.get(key)).stream().map(KillNumberResultBo.KillItemBo::getBall).toList();
                List<Integer> hitPicks = (List<Integer>) CollectionUtils.intersection(killReds, nextWins);
                List<Integer> softTitPicks = (List<Integer>) CollectionUtils.intersection(softKillRed, nextWins);
                System.out.println("根据第" + w.getPeriod() + "期数据【"+key+"】计算杀球总数"+(killReds.size()+softKillRed.size())
                        +"误杀总数 "+(hitPicks.size()+softTitPicks.size())
                        +"，红球/误杀" + killReds.size() + "/" + hitPicks.size()
                        +" 软杀/误杀："+softKillRed.size()+"/"+softTitPicks.size());
                if(Objects.equals(key,"均线杀球") && (hitPicks.size()+softTitPicks.size())==0){
                    jxSuccess.addAndGet(1);
                }

                if(Objects.equals(key,"指数杀球") && (hitPicks.size()+softTitPicks.size())==0){
                    zsSuccess.addAndGet(1);
                }
                if(Objects.equals(key,"遗漏杀球") && (hitPicks.size()+softTitPicks.size())==0){
                    ylSuccess.addAndGet(1);
                }
            });




            List<Integer> killReds = calculate.getHardKillRed().stream().map(KillNumberResultBo.KillItemBo::getBall).toList();
            List<Integer> softKillReds = calculate.getSoftKillRed().stream().map(KillNumberResultBo.KillItemBo::getBall).toList();

            List<Integer> killBlues = calculate.getHardKillBlue().stream().map(KillNumberResultBo.KillItemBo::getBall).toList();
            List<Integer> killSoftBlues = calculate.getSoftKillBlue().stream().map(KillNumberResultBo.KillItemBo::getBall).toList();
            List<Integer> hitPicks = (List<Integer>) CollectionUtils.intersection(killReds, nextWins);
            List<Integer> softHitPicks = (List<Integer>) CollectionUtils.intersection(softKillReds, nextWins);
            System.out.println("根据第" + w.getPeriod() + "期数据计算杀球，"
                    + "硬杀红球/误杀" + killReds.size() + "/" + hitPicks.size()+"  "+StringUtils.join(hitPicks,",")
//                    +" 红球硬杀："+StringUtils.join(killReds, ",")
                    + " 软杀红球/误杀：" + softKillReds.size() + "/" + softHitPicks.size()
//                    +" 红球软杀："+StringUtils.join(softKillReds,",")
                    + " 篮球必杀/误杀：" + killBlues.size() + "/" + killBlues.contains(next.getSpecial())
                    + " 篮球软杀/误杀：" + killSoftBlues.size() + "/" + killSoftBlues.contains(next.getSpecial())
                    +" 篮球杀球 "+(killBlues.contains(next.getSpecial())&&killSoftBlues.contains(next.getSpecial()))
            );
            System.out.println("**************************************");
        });
        System.out.println("jx="+jxSuccess.get()+"/"+30
                +" zs="+zsSuccess.get()+"/"+30
                +" yl="+ylSuccess.get()+"/"+30
        );
    }
}
