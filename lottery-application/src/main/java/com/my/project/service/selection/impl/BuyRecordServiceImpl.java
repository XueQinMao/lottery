package com.my.project.service.selection.impl;

import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.persistence.entity.BuyRecord;
import com.my.project.persistence.repository.IBuyRecordRepository;
import com.my.project.service.enums.PrizeLevelEnum;
import com.my.project.service.history.IHistoryRecordService;
import com.my.project.service.selection.IBuyRecordService;
import com.my.project.service.selection.enums.BuyRecordTypeEnums;
import com.my.project.service.selection.pojo.dto.BuyRecordDto;
import com.my.project.service.selection.pojo.vo.BuyRecordVo;
import com.my.project.service.support.SsqPrizeCheckerUtils;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * BuyRecordServiceImpl
 *
 * @author 刘强
 * @version 2025/12/29 20:03
 **/
@Service
@Primary
@AllArgsConstructor
public class BuyRecordServiceImpl implements IBuyRecordService {

    private final IBuyRecordRepository buyRecordRepository;

    private final IHistoryRecordService historyRecordService;

    @Override
    public void batchSave(LotteryAdjustRespBo adjustRespBo, LocalDate openDate, BuyRecordTypeEnums type) {
        if (adjustRespBo == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<BuyRecord> buyRecords = new ArrayList<>();

        // 各组单式：购买号=调整后单式，不含组内复式
        CollectionUtils.emptyIfNull(adjustRespBo.getAdjustedTickets()).forEach(t ->
            buyRecords.add(BuyRecord.builder()
                .type(type.name())
                .openDate(openDate)
                .oriRedBalls(joinBalls(t.getOriginalRedBalls()))
                .oriBlueBall(toBallStr(t.getOriginalBlueBall()))
                .adjustedRedBalls(joinBalls(t.getAdjustedRedBalls()))
                .adjustedBlueBalls(toBallStr(t.getAdjustedBlueBall()))
                .redBalls(joinBalls(t.getAdjustedRedBalls()))
                .blueBalls(toBallStr(t.getAdjustedBlueBall()))
                .totalBets(1)
                .reason(t.getReason())
                .createTime(now)
                .build()));

        // 最终推荐包：3 胆码 + 2 单式 + 1 复式
        LotteryAdjustRespBo.FinalRecommendation finalRec = adjustRespBo.getFinalRecommendation();
        if (finalRec != null) {
            String danBalls = joinBalls(finalRec.getDanBalls());
            CollectionUtils.emptyIfNull(finalRec.getSingleTickets()).forEach(s ->
                buyRecords.add(BuyRecord.builder()
                    .type(type.name())
                    .openDate(openDate)
                    .coreRedBalls(danBalls)
                    .redBalls(joinBalls(s.getRedBalls()))
                    .blueBalls(toBallStr(s.getBlueBall()))
                    .totalBets(s.getTotalBets() == null ? 1 : s.getTotalBets())
                    .reason(StringUtils.defaultIfBlank(s.getBasis(), finalRec.getDanBasis()))
                    .createTime(now)
                    .build()));

            LotteryAdjustRespBo.ComplexTicket complex = finalRec.getComplexTicket();
            if (complex != null) {
                buyRecords.add(BuyRecord.builder()
                    .type(type.name())
                    .openDate(openDate)
                    .coreRedBalls(danBalls)
                    .redBalls(joinBalls(complex.getRedBalls()))
                    .blueBalls(joinBalls(complex.getBlueBalls()))
                    .totalBets(complex.getTotalBets())
                    .reason(StringUtils.defaultIfBlank(complex.getBasis(), adjustRespBo.getConclusion()))
                    .createTime(now)
                    .build());
            }
        }

        if (CollectionUtils.isNotEmpty(buyRecords)) {
            buyRecordRepository.saveOrUpdateBatch(buyRecords);
        }
    }

    private static String joinBalls(List<Integer> balls) {
        return CollectionUtils.isEmpty(balls) ? null : StringUtils.join(balls, ",");
    }

    private static String toBallStr(Integer ball) {
        return ball == null ? null : String.valueOf(ball);
    }

    @Override
    public List<BuyRecordDto> getByOpenDate(LocalDate localDate) {
        var buyRecords = buyRecordRepository.lambdaQuery().eq(BuyRecord::getOpenDate, localDate).list();
        return CollectionUtils.emptyIfNull(buyRecords).stream().map(
            b -> BuyRecordDto.builder().id(b.getId()).openDate(b.getOpenDate()).oriRedBalls(b.getOriRedBalls())
                .oriBlueBall(b.getOriBlueBall()).adjustedRedBalls(b.getAdjustedRedBalls())
                .adjustedBlueBall(b.getAdjustedBlueBalls()).coreRedBalls(b.getCoreRedBalls())
                .redBalls(b.getRedBalls()).blueBalls(b.getBlueBalls())
                .reason(b.getReason()).totalBets(b.getTotalBets()).build()).toList();
    }

    @Override
    public Map<PrizeLevelEnum, List<BuyRecordVo>> statisticsHitSituations() {
        var latestRecords = Optional.ofNullable(historyRecordService.getLatestRecords(NumberUtils.INTEGER_ONE))
            .orElse(Collections.emptyList()).stream().findFirst()
            .orElseThrow(() -> new RuntimeException("未找到最新开奖记录"));
        List<Integer> integers =
            List.of(latestRecords.getNum1(), latestRecords.getNum2(), latestRecords.getNum3(), latestRecords.getNum4(),
                latestRecords.getNum5(), latestRecords.getNum6());
        var buyRecords =
            buyRecordRepository.lambdaQuery().eq(BuyRecord::getOpenDate, latestRecords.getOpenDate()).list();

        Function<String, List<Integer>> convertFunc =
            str -> Arrays.stream(str.replace("[","").replace("]","").trim().split(","))
                .map(s ->Integer.parseInt(s.trim())).toList();
        return CollectionUtils.emptyIfNull(buyRecords).stream().map(
            r -> BuyRecordVo.builder().oriRedBalls(r.getOriRedBalls()).oriBlueBall(r.getOriBlueBall())
                .adjustedRedBalls(r.getAdjustedRedBalls()).adjustedBlueBall(r.getAdjustedBlueBalls())
                .coreRedBalls(r.getCoreRedBalls())
                .redBalls(r.getRedBalls()).blueBalls(r.getBlueBalls()).build())
            .filter(vo -> {
                boolean oriHit = StringUtils.isNotBlank(vo.getOriRedBalls()) && StringUtils.isNotBlank(vo.getOriBlueBall())
                    && SsqPrizeCheckerUtils.checkPrize(integers, latestRecords.getSpecial(),
                    convertFunc.apply(vo.getOriRedBalls()), convertFunc.apply(vo.getOriBlueBall())).isHit();
                boolean adjustHit = StringUtils.isNotBlank(vo.getAdjustedRedBalls())
                    && StringUtils.isNotBlank(vo.getAdjustedBlueBall())
                    && SsqPrizeCheckerUtils.checkPrize(integers, latestRecords.getSpecial(),
                    convertFunc.apply(vo.getAdjustedRedBalls()), convertFunc.apply(vo.getAdjustedBlueBall())).isHit();
                boolean buyHit = StringUtils.isNotBlank(vo.getRedBalls()) && StringUtils.isNotBlank(vo.getBlueBalls())
                    && SsqPrizeCheckerUtils.checkPrize(integers, latestRecords.getSpecial(),
                    convertFunc.apply(vo.getRedBalls()), convertFunc.apply(vo.getBlueBalls())).isHit();
                return oriHit || adjustHit || buyHit;
        }).map(vo -> {
            PrizeLevelEnum oriHitPrizeLevels = StringUtils.isNotBlank(vo.getOriRedBalls())
                && StringUtils.isNotBlank(vo.getOriBlueBall())
                ? SsqPrizeCheckerUtils.checkPrize(integers, latestRecords.getSpecial(),
                convertFunc.apply(vo.getOriRedBalls()), convertFunc.apply(vo.getOriBlueBall()))
                : PrizeLevelEnum.NO_PRIZE;
            PrizeLevelEnum adjustHitPrizeLevels = StringUtils.isNotBlank(vo.getAdjustedRedBalls())
                && StringUtils.isNotBlank(vo.getAdjustedBlueBall())
                ? SsqPrizeCheckerUtils.checkPrize(integers, latestRecords.getSpecial(),
                convertFunc.apply(vo.getAdjustedRedBalls()), convertFunc.apply(vo.getAdjustedBlueBall()))
                : PrizeLevelEnum.NO_PRIZE;
            PrizeLevelEnum hitPrizeLevels = StringUtils.isNotBlank(vo.getRedBalls())
                && StringUtils.isNotBlank(vo.getBlueBalls())
                ? SsqPrizeCheckerUtils.checkPrize(integers, latestRecords.getSpecial(),
                convertFunc.apply(vo.getRedBalls()), convertFunc.apply(vo.getBlueBalls()))
                : PrizeLevelEnum.NO_PRIZE;
            Integer i = Stream.of(oriHitPrizeLevels, adjustHitPrizeLevels, hitPrizeLevels).filter(PrizeLevelEnum::isHit)
                .map(PrizeLevelEnum::getLevel).min(Comparator.naturalOrder()).orElse(null);
            return Pair.of(PrizeLevelEnum.getPrizeLevel(i), vo);
        }).collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toList())));
    }
}
