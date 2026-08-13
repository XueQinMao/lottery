package com.my.project.service.predict.impl;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.my.project.persistence.entity.PredictLog;
import com.my.project.service.event.PredictCompleteEvent;
import com.my.project.service.predict.IInitPythonProcessService;
import com.my.project.service.predict.IPredictLogService;
import com.my.project.service.predict.IPredictService;
import com.my.project.service.selection.pojo.bo.SsqCombinationBo;
import com.my.project.service.support.SsqCombinationUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * PredictServiceImpl
 *
 * @author 刘强
 * @version 2025/10/28 19:59
 **/
@Service
@Primary
@Slf4j
public class PredictServiceImpl implements IPredictService {

    private final IInitPythonProcessService pythonProcessService;

    private final IPredictLogService predictLogService;

    private final ThreadPoolExecutor predictConsumerPool;

    public PredictServiceImpl(IInitPythonProcessService iInitPythonProcessService, IPredictLogService predictLogService,
        ThreadPoolExecutor predictConsumerPool) {
        this.pythonProcessService = iInitPythonProcessService;
        this.predictLogService = predictLogService;
        this.predictConsumerPool = predictConsumerPool;
    }

    @Override
    public void autoPredict(LocalDate openDate) {
        PredictLog predictLog = predictLogService.getByOpenDate(openDate);
        // position=已完成组数；例如已预测 1000 组则传入 1000，从第 1001 组继续
        long startIndex = SsqCombinationUtils.getIndex(predictLog == null ? null : predictLog.getPosition());
        if (startIndex >= SsqCombinationUtils.TOTAL_COMBINATIONS) {
            log.info("开奖日 {} 已预测完成，跳过", openDate);
            pythonProcessService.shutdown();
            return;
        }
        var counter = new AtomicLong(startIndex);
        var handler = new DefaultPredictResultHandler(openDate, counter, log);
        var consumer = combinationConsumer(handler);
        SsqCombinationUtils.generateNaturalRandom(10000000, consumer);
    }

    private Consumer<SsqCombinationBo> combinationConsumer(IPredictResultHandler handler) {
        return combination -> {
            Instant singleStart = Instant.now();
            //计算红球总和要在[90-150]
            int sum = combination.getRedBalls().stream().mapToInt(Integer::intValue).sum();
            if(sum<=90 || sum>=150){
                log.warn("生成的号码组 {} 和值不满足特征，跳过", JSON.toJSONString(combination));
                return;
            }
            List<Integer> sortedList = combination.getRedBalls().stream().sorted(Comparator.naturalOrder()).toList();
            int diff = sortedList.getLast()- sortedList.getFirst();
            if(diff<16 || diff>28){
                log.warn("生成的号码组 {} 差值不满足特征，跳过", JSON.toJSONString(combination));
                return;
            }
            //奇偶数数量赞比
            long oddNumber = combination.getRedBalls().stream().filter(i -> i % 2 > 0).count();
            if(oddNumber>5){
                log.warn("生成的号码组 {} 奇数数量>5不满足特征，跳过", JSON.toJSONString(combination));
                return;
            }
            Supplier<String> supplyAsyncSupplier = () -> {
                // 与 Python predict.py 单注入口统一：type=predict + second_fusion_model
                var params =
                    Map.of("type", "predict", "model", "second_fusion_model", "red_balls", combination.getRedBalls(),
                        "blue_ball", combination.getBlueBall());
                return pythonProcessService.runInference(params);
            };
            CompletableFuture.supplyAsync(supplyAsyncSupplier, predictConsumerPool)
                .orTimeout(NumberUtils.INTEGER_TWO, TimeUnit.MINUTES).whenComplete((result, t) -> {
                    if (Objects.isNull(t)) {
                        handler.onSuccess(combination, result, singleStart);
                    } else {
                        handler.onFailure(combination, t);
                    }
                });
        };
    }

    @FunctionalInterface
    interface IPredictResultHandler {
        void onSuccess(SsqCombinationBo combination, String result, Instant singleStart);

        default void onFailure(SsqCombinationBo combination, Throwable t) {
        }
    }

    @Slf4j
    @AllArgsConstructor
    public static class DefaultPredictResultHandler implements IPredictResultHandler {
        private final LocalDate openDate;
        private final AtomicLong counter;
        private final Logger logger;

        @Override
        public void onSuccess(SsqCombinationBo combination, String result, Instant singleStart) {
            var finished = counter.incrementAndGet();
            logger.info("已执行到第 {} 组 耗时 {} 毫秒，号码组 {}  预测结果: {} ", finished, ChronoUnit.MILLIS.between(singleStart, Instant.now()), JSONObject.toJSONString(combination),
                result);
            PredictCompleteEvent.of(this, combination, result, openDate, finished).publish();
        }

        @Override
        public void onFailure(SsqCombinationBo combination, Throwable t) {
            log.error("号码组 {} 预测异常", JSONObject.toJSONString(combination), t);
        }
    }

}
