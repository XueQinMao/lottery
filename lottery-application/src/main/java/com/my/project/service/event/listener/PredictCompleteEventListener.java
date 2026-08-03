package com.my.project.service.event.listener;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.service.predict.IPredictCacheService;
import com.my.project.service.predict.IPredictLogService;
import com.my.project.service.selection.ISmartSelectService;
import com.my.project.service.config.LotteryModelConfig;
import com.my.project.service.event.PredictCompleteEvent;
import com.my.project.service.selection.pojo.bo.SsqCombinationBo;
import com.my.project.service.selection.pojo.bo.WeightConfigBo;
import com.my.project.service.support.FileUtils;
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
    private final ISmartSelectService smartSelectService;

    private final LotteryModelConfig lotteryModelConfig;

    private final IPredictLogService predictLogService;

    private final IPredictCacheService predictCacheService;


    @EventListener
    public void handleEvent(PredictCompleteEvent event) {
        ModelPredictOutputBo predictOutput = JSONObject.parseObject(event.getPredictRecord(), ModelPredictOutputBo.class);
        WeightConfigBo optimizedWeightConfig = smartSelectService.getWeightConfig();
        boolean isPersist = shouldPersist(predictOutput.getProbability(), optimizedWeightConfig);
        if (isPersist) {
            predictCacheService.addCache(predictOutput, event.getSsqCombinationBo(), event.getOpenDate());
        }
        CompletableFuture.runAsync(() -> {
            writePredictionToFiles(predictOutput, event.getSsqCombinationBo(), event.getOpenDate(), isPersist);
            predictLogService.addOrUpdate(event.getOpenDate(), event.getPosition());
        });
    }


    private void writePredictionToFiles(ModelPredictOutputBo predictOutput, SsqCombinationBo ssqCombinationBo, LocalDate localDate, boolean isPersist) {
        var cacheKey = predictCacheService.getCacheKey(localDate, ssqCombinationBo.getRedBalls(), ssqCombinationBo.getBlueBall());
        if(isPersist){
            FileUtils.append(lotteryModelConfig.getPath() + "/" + localDate + "_cache_persistence.txt", cacheKey.concat("#").concat(
                JSON.toJSONString(predictOutput)));
        }
        FileUtils.append(lotteryModelConfig.getPath() + "/" + localDate + "_预测结果.txt", cacheKey.concat("#").concat(
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
            return false;
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
