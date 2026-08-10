package com.my.project.service.predict.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.persistence.repository.IPredictHitRecordRepository;
import com.my.project.persistence.repository.IPredictRecordRepository;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.entity.PredictHitRecord;
import com.my.project.persistence.entity.PredictRecord;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.service.predict.IPredictHitRecordService;
import com.my.project.service.config.LotteryModelConfig;
import com.my.project.service.enums.PrizeLevelEnum;
import com.my.project.service.predict.pojo.vo.PredictHitRecordVo;
import com.my.project.service.selection.ISmartSelectService;
import com.my.project.service.support.BatchQueryUtils;
import com.my.project.service.support.FileUtils;
import com.my.project.service.support.SsqPrizeCheckerUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PredictRecordServiceImpl
 *
 * @author 刘强
 * @version 2025/10/31 17:12
 **/
@Service
@Primary
@Slf4j
@AllArgsConstructor
public class PredictHitRecordServiceImpl implements IPredictHitRecordService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PredictHitRecordServiceImpl.class);

    private final IPredictHitRecordRepository predictHitRecordRepository;

    private final IHistoryRecordRepository historyRecordRepository;

    private final IPredictRecordRepository predictRecordRepository;

    private final LotteryModelConfig lotteryModelConfig;

    private ISmartSelectService smartSelectService;



    @Override
    public void archiveHits(LocalDate openDate) {
        HistoryRecord first =
            historyRecordRepository.lambdaQuery().eq(HistoryRecord::getOpenDate, openDate).list().getFirst();
        List<Integer> integers =
            List.of(first.getNum1(), first.getNum2(), first.getNum3(), first.getNum4(), first.getNum5(),
                first.getNum6());
        Assert.notEmpty(integers, "第" + openDate + "期开奖结果为空");
        CompletableFuture<Void> dataBaseFuture =
            CompletableFuture.runAsync(() -> processDatabasePredictions(integers, first.getSpecial(), openDate));
        CompletableFuture<Void> fileFuture =
            CompletableFuture.runAsync(() -> processFilePredictions(integers, first.getSpecial(), openDate));
        CompletableFuture.allOf(dataBaseFuture, fileFuture).whenComplete((result, t) -> smartSelectService.refreshWeightConfig()).join();
    }

    @Override
    public Map<PrizeLevelEnum, List<PredictHitRecordVo>> getHitsByPrizeLevel(LocalDate openDate) {
        Map<PrizeLevelEnum, List<PredictHitRecordVo>> resultMap = new ConcurrentHashMap<>();
        predictHitRecordRepository.lambdaQuery().eq(PredictHitRecord::getOpenDate, openDate).list().parallelStream()
            .map(r -> {
                PrizeLevelEnum prizeLevel = PrizeLevelEnum.getPrizeLevel(r.getLevel());
                if (Objects.isNull(prizeLevel)) {
                    return null;
                }
                return Pair.of(prizeLevel, BeanUtil.copyProperties(r, PredictHitRecordVo.class));
            }).filter(Objects::nonNull).collect(Collectors.groupingBy(Pair::getKey)).forEach((key, value) -> {
                resultMap.put(key, value.parallelStream().map(Pair::getValue).toList());
            });
        return resultMap;
    }

    private void processDatabasePredictions(List<Integer> integers, Integer special, LocalDate openDate) {
        Consumer<List<PredictRecord>> consumer = list -> {
            List<PredictHitRecord> predictHitResults =
                list.parallelStream().map(toPredictHitRecordMapper(integers, special, null)).filter(Objects::nonNull)
                    .toList();
            if (CollectionUtil.isNotEmpty(list)) {
                try {
                    predictHitRecordRepository.saveBatch(predictHitResults);
                } catch (Exception e) {
                    LOGGER.error("第 {} 数据归档失败 {}", openDate, e.getMessage(), e);
                }
            }
        };
        BatchQueryUtils utils = new BatchQueryUtils(predictRecordRepository);
        utils.process(openDate, consumer);
    }

    private void processFilePredictions(List<Integer> integers, Integer special, LocalDate openDate) {
        try {
            FileUtils.readLine(lotteryModelConfig.getPath() + "/" + openDate + "_预测结果.txt",
                content -> Optional.of(content).map(s -> s.split("#"))
                    .map(array -> Pair.of(array[0].split("\\|"), JSON.parseObject(array[1], ModelPredictOutputBo.class)))
                    .map(pair -> {
                        PredictRecord result = new PredictRecord();
                        result.setOpenDate(openDate);
                        result.setRedBalls(pair.getLeft()[1]);
                        result.setBlueBall(Integer.valueOf(pair.getLeft()[2]));
                        result.setExplanation(pair.getRight().getReason());
                        result.setTotalScore(pair.getRight().getProbability());
                        return result;
                    }).map(toPredictHitRecordMapper(integers, special, "file"))
                    .ifPresent(predictHitRecordRepository::save));

            //删除预测结果和缓存持久化数据
            FileUtils.deleteFile(lotteryModelConfig.getPath() + "/" + openDate + "_预测结果.txt");
            FileUtils.deleteFile(lotteryModelConfig.getPath() + "/" + openDate + "_cache_persistence.txt");
        } catch (Exception ignored) {
        }
    }

    private Function<PredictRecord, PredictHitRecord> toPredictHitRecordMapper(List<Integer> integers, Integer special, String source) {
        return record -> {
            List<Integer> resultRedBalls =
                Arrays.stream(record.getRedBalls().split(",")).map(Integer::parseInt).toList();
            PrizeLevelEnum prizeLevel =
                SsqPrizeCheckerUtils.checkPrize(integers, special, resultRedBalls, List.of(record.getBlueBall()));
            if (!PrizeLevelEnum.getHitPrizeLevels().contains(prizeLevel)) {
                return null;
            }
            log.info("命中一次 {} 等奖",prizeLevel.name());
            PredictHitRecord result = new PredictHitRecord();
            result.setOpenDate(record.getOpenDate());
            result.setRedBalls(record.getRedBalls());
            result.setBlueBall(record.getBlueBall());
            result.setTotalScore(record.getTotalScore());
            result.setExplanation(record.getExplanation());
            result.setLevel(prizeLevel.getLevel());
            result.setCreateTime(record.getCreateTime());
            result.setSource(source);
            return result;
        };
    }
}
