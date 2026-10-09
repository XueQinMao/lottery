package com.my.project.application.event.listener;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.application.predict.PredictCacheService;
import com.my.project.application.predict.PredictLogService;
import com.my.project.application.selection.SmartSelectService;
import com.my.project.application.config.LotteryModelProperties;
import com.my.project.application.event.PredictCompleteEvent;
import com.my.project.application.selection.model.SsqCombinationBo;
import com.my.project.application.selection.model.WeightConfigBo;
import com.my.project.application.support.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

/**
 * PredictCompleteEventListener
 *
 * <p>入库过滤：模型分落在历史命中分数的 P5~P95 主体区间内才写入 t_predict_result；
 * 区间外写文件归档。区间未就绪或概率为空时默认入库，避免误杀候选池。
 *
 * @author 刘强
 * @version 2025/10/30 16:10
 **/
@Component
@Slf4j
@AllArgsConstructor
public class PredictCompleteEventListener {
    private final SmartSelectService smartSelectService;

    private final LotteryModelProperties modelProperties;

    private final PredictLogService predictLogService;

    private final PredictCacheService predictCacheService;


    @EventListener
    public void handleEvent(PredictCompleteEvent event) {
        var predictOutput = JSONObject.parseObject(event.getPredictRecord(), ModelPredictOutputBo.class);
        var weightConfig = smartSelectService.getWeightConfig();
        boolean isPersist = shouldPersist(predictOutput.getProbability(), weightConfig);
        if (isPersist) {
            predictCacheService.addCache(predictOutput, event.getSsqCombinationBo(), event.getOpenDate());
        }
        CompletableFuture.runAsync(() -> {
            writePredictionToFiles(predictOutput, event.getSsqCombinationBo(), event.getOpenDate(), isPersist);
            predictLogService.addOrUpdate(event.getOpenDate(), event.getPosition());
        });
    }


    private void writePredictionToFiles(ModelPredictOutputBo predictOutput, SsqCombinationBo combination, LocalDate localDate, boolean isPersist) {
        var cacheKey = predictCacheService.getCacheKey(localDate, combination.getRedBalls(), combination.getBlueBall());
        if(isPersist){
            FileUtils.append(modelProperties.getPath() + "/" + localDate + "_cache_persistence.txt", cacheKey.concat("#").concat(
                JSON.toJSONString(predictOutput)));
        }
        FileUtils.append(modelProperties.getPath() + "/" + localDate + "_预测结果.txt", cacheKey.concat("#").concat(
            JSON.toJSONString(predictOutput)));
    }

    /**
     * 是否入库：落在 P5~P95 主体区间内；边界未配置或概率为空时放行入库。
     */
    private boolean shouldPersist(BigDecimal probability, WeightConfigBo config) {
        if (probability == null) {
            return false;
        }
        if (config == null || config.getProbabilityMin() == null || config.getProbabilityMax() == null) {
            return true;
        }
        double min = config.getProbabilityMin();
        double max = config.getProbabilityMax();
        // 无效区间（如未算出或 min>max）时放行
        if (min > max) {
            return false;
        }
        double p = probability.doubleValue();
        return p >= min && p <= max;
    }
}
