package com.my.project.service.selection.impl;

import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.persistence.entity.BuyRecord;
import com.my.project.persistence.entity.HistoryRecord;
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
        var buyRecords = CollectionUtils.emptyIfNull(adjustRespBo.getAdjustedTickets()).stream().map(
            t -> BuyRecord.builder().type(type.name()).openDate(openDate)
                .oriRedBalls(StringUtils.join(t.getOriginalRedBalls(), ","))
                .oriBlueBall(String.valueOf(t.getOriginalBlueBall()))
                .adjustedBlueBalls(StringUtils.join(t.getAdjustedBlueBall(), ","))
                .adjustedRedBalls(String.valueOf(t.getAdjustedRedBalls()))
                .redBalls(StringUtils.join(t.getComplexTicket().getRedBalls(), ","))
                .blueBalls(StringUtils.join(t.getComplexTicket().getBlueBalls(), ","))
                .totalBets(t.getComplexTicket().getTotalBets()).reason(t.getReason()).createTime(LocalDateTime.now())
                .build()).toList();
        if (CollectionUtils.isNotEmpty(buyRecords)) {
            buyRecordRepository.saveOrUpdateBatch(buyRecords);
        }
    }

    @Override
    public List<BuyRecordDto> getByOpenDate(LocalDate localDate) {
        var buyRecords = buyRecordRepository.lambdaQuery().eq(BuyRecord::getOpenDate, localDate).list();
        return CollectionUtils.emptyIfNull(buyRecords).stream().map(
            b -> BuyRecordDto.builder().id(b.getId()).openDate(b.getOpenDate()).oriRedBalls(b.getOriRedBalls())
                .oriBlueBall(b.getOriBlueBall()).adjustedRedBalls(b.getAdjustedRedBalls())
                .adjustedBlueBall(b.getAdjustedBlueBalls()).redBalls(b.getRedBalls()).blueBalls(b.getBlueBalls())
                .reason(b.getReason()).build()).toList();
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
                .redBalls(r.getRedBalls()).blueBalls(r.getBlueBalls()).build())
            .filter(vo -> {
                var oriHitPrizeLevels = SsqPrizeCheckerUtils.checkPrize(integers, latestRecords.getSpecial(),
                    convertFunc.apply(vo.getOriRedBalls()), convertFunc.apply(vo.getOriBlueBall()));
                var adjustHitPrizeLevels = SsqPrizeCheckerUtils.checkPrize(integers, latestRecords.getSpecial(),
                    convertFunc.apply(vo.getAdjustedRedBalls()), convertFunc.apply(vo.getAdjustedBlueBall()));

                var hitPrizeLevels = SsqPrizeCheckerUtils.checkPrize(integers, latestRecords.getSpecial(),
                    convertFunc.apply(vo.getRedBalls()), convertFunc.apply(vo.getBlueBalls()));
                return oriHitPrizeLevels.isHit() || adjustHitPrizeLevels.isHit() || hitPrizeLevels.isHit();
        }).map(vo -> {
            var oriHitPrizeLevels = SsqPrizeCheckerUtils.checkPrize(integers, latestRecords.getSpecial(),
                convertFunc.apply(vo.getOriRedBalls()), convertFunc.apply(vo.getOriBlueBall()));
            var adjustHitPrizeLevels = SsqPrizeCheckerUtils.checkPrize(integers, latestRecords.getSpecial(),
                convertFunc.apply(vo.getAdjustedRedBalls()), convertFunc.apply(vo.getAdjustedBlueBall()));

            var hitPrizeLevels = SsqPrizeCheckerUtils.checkPrize(integers, latestRecords.getSpecial(),
                convertFunc.apply(vo.getRedBalls()), convertFunc.apply(vo.getBlueBalls()));
            Integer i = Stream.of(oriHitPrizeLevels, adjustHitPrizeLevels, hitPrizeLevels).filter(PrizeLevelEnum::isHit)
                .map(PrizeLevelEnum::getLevel).min(Comparator.naturalOrder()).orElse(null);
            return Pair.of(PrizeLevelEnum.getPrizeLevel(i), vo);
        }).collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toList())));
    }
}
