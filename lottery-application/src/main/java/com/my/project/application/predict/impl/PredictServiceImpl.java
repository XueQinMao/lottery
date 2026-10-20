package com.my.project.application.predict.impl;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.my.project.infrastructure.persistence.entity.PredictLog;
import com.my.project.application.event.PredictCompleteEvent;
import com.my.project.application.predict.InitPythonProcessService;
import com.my.project.application.predict.PredictLogService;
import com.my.project.application.predict.PredictService;
import com.my.project.application.selection.model.SsqCombinationBo;
import com.my.project.application.support.SsqCombinationUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * PredictServiceImpl
 *
 * @author 刘强
 * @version 2025/10/28 19:59
 **/
@Service
@Primary
@Slf4j
public class PredictServiceImpl implements PredictService {

    private final InitPythonProcessService pythonProcessService;

    private final PredictLogService predictLogService;

    private final ThreadPoolExecutor predictConsumerPool;
    private final Duration drainTimeout;

    @Autowired
    public PredictServiceImpl(InitPythonProcessService pythonProcessService, PredictLogService predictLogService,
        ThreadPoolExecutor predictConsumerPool) {
        this(pythonProcessService, predictLogService, predictConsumerPool,
            Duration.ofMillis(Long.getLong("lottery.prediction.drain-timeout-ms", 1_800_000L)));
    }

    PredictServiceImpl(InitPythonProcessService pythonProcessService, PredictLogService predictLogService,
                      ThreadPoolExecutor predictConsumerPool, Duration drainTimeout) {
        this.pythonProcessService = pythonProcessService;
        this.predictLogService = predictLogService;
        this.predictConsumerPool = predictConsumerPool;
        this.drainTimeout = drainTimeout;
    }

    @Override
    public synchronized void autoPredict(LocalDate openDate) {
        PredictLog predictLog = predictLogService.getByOpenDate(openDate);
        // position=已完成组数；例如已预测 1000 组则传入 1000，从第 1001 组继续
        long startIndex = SsqCombinationUtils.getIndex(predictLog == null ? null : predictLog.getPosition());
        if (startIndex >= SsqCombinationUtils.TOTAL_COMBINATIONS) {
            log.info("开奖日 {} 已预测完成，跳过", openDate);
            pythonProcessService.shutdown();
            return;
        }
        runPredictions(openDate, startIndex, consumer -> SsqCombinationUtils.generateNaturalRandom(10000000, consumer));
    }

    /** 流水线只保存有界执行器内的任务计数；不积累百万个Future。 */
    void runPredictions(LocalDate openDate, long startIndex, Consumer<Consumer<SsqCombinationBo>> generator) {
        var handler = new EventPublishingPredictionHandler(openDate, new AtomicLong(startIndex), log);
        var pending = new Phaser(1);
        var failure = new AtomicReference<Throwable>();
        var aborted = new AtomicBoolean();
        try {
            generator.accept(combinationConsumer(handler, pending, failure, aborted));
        } catch (RuntimeException e) {
            failure.compareAndSet(null, e);
        } finally {
            int phase = pending.arriveAndDeregister();
            try { pending.awaitAdvanceInterruptibly(phase, drainTimeout.toMillis(), TimeUnit.MILLISECONDS); }
            catch (TimeoutException e) {
                aborted.set(true);
                pythonProcessService.shutdown();
                throw new IllegalStateException("等待预测及结果写入超时，停止后续推荐", e);
            }
            catch (InterruptedException e) {
                aborted.set(true);
                pythonProcessService.shutdown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待预测完成被中断，已终止Python通信", e);
            }
        }
        if (failure.get() != null) throw new IllegalStateException("自动预测未完整成功，停止后续推荐", failure.get());
    }

    private Consumer<SsqCombinationBo> combinationConsumer(PredictionResultHandler handler, Phaser pending,
                                                           AtomicReference<Throwable> failure, AtomicBoolean aborted) {
        return combination -> {
            if (failure.get() != null) throw new IllegalStateException("已有预测失败，停止提交新任务", failure.get());
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
            Runnable prediction = () -> {
                // 与 Python predict.py 单注入口统一：type=predict + second_fusion_model
                var params =
                    Map.of("type", "predict", "model", "second_fusion_model", "red_balls", combination.getRedBalls(),
                        "blue_ball", combination.getBlueBall());
                String result = pythonProcessService.runInference(params);
                if (aborted.get()) throw new IllegalStateException("本轮预测已终止，丢弃迟到响应");
                JSONObject response = result == null ? null : JSONObject.parseObject(result);
                if (response == null || response.containsKey("error") || response.getBigDecimal("probability") == null)
                    throw new IllegalStateException("Python未返回有效预测结果: " + result);
                handler.onSuccess(combination, result, singleStart);
            };
            pending.register();
            try {
                // 实际I/O超时由PythonConnection控制，不能用Future超时伪装成已回收工作。
                CompletableFuture.runAsync(prediction, predictConsumerPool).whenComplete((unused, t) -> {
                    try {
                        if (t != null) { failure.compareAndSet(null, t); handler.onFailure(combination, t); }
                    } finally { pending.arriveAndDeregister(); }
                });
            } catch (RuntimeException e) {
                pending.arriveAndDeregister();
                throw e;
            }
        };
    }

    @FunctionalInterface
    interface PredictionResultHandler {
        void onSuccess(SsqCombinationBo combination, String result, Instant singleStart);

        default void onFailure(SsqCombinationBo combination, Throwable t) {
        }
    }

    @Slf4j
    @AllArgsConstructor
    public static class EventPublishingPredictionHandler implements PredictionResultHandler {
        private final LocalDate openDate;
        private final AtomicLong counter;
        private final Logger logger;

        @Override
        public synchronized void onSuccess(SsqCombinationBo combination, String result, Instant singleStart) {
            var finished = counter.get() + 1;
            PredictCompleteEvent.of(this, combination, result, openDate, finished).publish();
            counter.incrementAndGet();
            logger.info("已执行到第 {} 组 耗时 {} 毫秒，号码组 {}  预测结果: {} ", finished, ChronoUnit.MILLIS.between(singleStart, Instant.now()), JSONObject.toJSONString(combination),
                result);
        }

        @Override
        public void onFailure(SsqCombinationBo combination, Throwable t) {
            log.error("号码组 {} 预测异常", JSONObject.toJSONString(combination), t);
        }
    }

}
