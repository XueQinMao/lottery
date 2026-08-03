package com.my.project.service.predict.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.persistence.repository.IPredictRecordRepository;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.entity.PredictRecord;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.service.predict.IPredictRecordService;
import com.my.project.service.config.LotteryModelConfig;
import com.my.project.service.predict.pojo.vo.PredictRecordVo;
import com.my.project.service.support.BatchQueryUtils;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * PredictRecordServiceImpl
 *
 * @author 刘强
 * @version 2025/10/31 17:12
 **/
@Service
@Primary
@AllArgsConstructor
public class PredictRecordServiceImpl implements IPredictRecordService {

    private final IPredictRecordRepository predictRecordRepository;

    private final IHistoryRecordRepository historyRecordRepository;

    @Override
    @Transactional
    public void deleteByOpenDate(LocalDate openDate) {
        new BatchQueryUtils(predictRecordRepository).processIds(openDate,
            ids -> predictRecordRepository.getBaseMapper().deleteByIds(ids));
        //回收mariaDB空间
        predictRecordRepository.optimizeTable();
    }

    @Override
    public IPage<PredictRecordVo> findPage(IPage<PredictRecordVo> page, LocalDate openDate) {
        Page<PredictRecord> predictResultPage = new Page<>(page.getCurrent(), page.getSize());
        Page<PredictRecord> resultPage = predictRecordRepository.lambdaQuery().eq(PredictRecord::getBlueBall, 10)
            .eq(Objects.nonNull(openDate), PredictRecord::getOpenDate, openDate)
            .orderBy(true, false, PredictRecord::getTotalScore).page(predictResultPage);
        HistoryRecord historyRecord =
            historyRecordRepository.lambdaQuery().eq(HistoryRecord::getOpenDate, openDate).one();
        return page.setRecords(convertPredictRecordDto(resultPage.getRecords(), Pair.of(
            List.of(historyRecord.getNum1(), historyRecord.getNum2(), historyRecord.getNum3(), historyRecord.getNum4(),
                historyRecord.getNum5(), historyRecord.getNum6()), List.of(historyRecord.getSpecial()))));
    }

    @Override
    @Transactional
    public void saveBatch(Map<String, ModelPredictOutputBo> predictRecords, LocalDate openDate) {
        if(MapUtils.isEmpty(predictRecords)){
            return;
        }
        var list = predictRecords.entrySet().stream().map(entry -> {
            String[] split = entry.getKey().split("\\|");
            PredictRecord predictRecord = new PredictRecord();
            predictRecord.setOpenDate(openDate);
            predictRecord.setRedBalls(split[1]);
            predictRecord.setBlueBall(Integer.parseInt(split[2]));
            predictRecord.setTotalScore(entry.getValue().getProbability());
            predictRecord.setExplanation(entry.getValue().getReason());
            predictRecord.setCreateTime(LocalDateTime.now());
            return predictRecord;
        }).toList();

        predictRecordRepository.saveOrUpdateBatch(list);
    }

    private List<PredictRecordVo> convertPredictRecordDto(List<PredictRecord> results,
        Pair<List<Integer>, List<Integer>> winPair) {
        return CollectionUtil.emptyIfNull(results).stream().map(result -> {
            PredictRecordVo dto = new PredictRecordVo();
            dto.setId(result.getId());
            dto.setOpenDate(result.getOpenDate());
            dto.setRecommendedDate(result.getCreateTime());
            dto.setWinningRedNumbers(winPair.getLeft());
            dto.setWinningBlueNumbers(winPair.getRight());
            dto.setRedContrasts(convertReds(winPair.getLeft(), result));
            dto.setBlueContrasts(convertBlues(winPair.getRight(), result));
            dto.setHitCount(dto.getRedContrasts().stream().filter(Pair::getRight).count());
            dto.setHitRate(new BigDecimal(dto.getHitCount()).divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP));
            dto.setExplanation(result.getExplanation());
            dto.setTotalScore(result.getTotalScore());
            return dto;
        }).sorted(Comparator.comparing(PredictRecordVo::getHitRate).reversed()).collect(Collectors.toList());
    }

    private List<Pair<Integer, Boolean>> convertBlues(List<Integer> winNumbers, PredictRecord result) {
        return Stream.of(result.getBlueBall()).map(number -> buildPair(winNumbers, number))
            .collect(Collectors.toList());
    }

    private List<Pair<Integer, Boolean>> convertReds(List<Integer> winNumbers, PredictRecord result) {
        return Arrays.stream(result.getRedBalls().split(",")).map(Integer::parseInt)
            .map(number -> buildPair(winNumbers, number)).collect(Collectors.toList());
    }

    private Pair<Integer, Boolean> buildPair(List<Integer> winNumbers, int number) {
        return Pair.of(number, winNumbers.stream().anyMatch(hitNumber -> hitNumber == number));
    }
}
