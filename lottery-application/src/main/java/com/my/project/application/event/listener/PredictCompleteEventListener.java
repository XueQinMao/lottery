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

/**
 * PredictCompleteEventListener
 *
 * <p>入库过滤：模型分落在历史命中分数的 P5~P95 主体区间内才写入 t_predict_result；
 * 区间外写文件归档。区间未配置时放行；概率为空或区间反向时不入库。
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
        if (predictOutput == null || predictOutput.getProbability() == null)
            throw new IllegalArgumentException("预测结果缺少probability，不发布成功结果");
        var weightConfig = smartSelectService.getWeightConfig();
        boolean isPersist = shouldPersist(predictOutput.getProbability(), weightConfig);
        if (isPersist) {
            predictCacheService.addCache(predictOutput, event.getSsqCombinationBo(), event.getOpenDate());
        }
        // 事件在有界预测执行器中同步处理，Future完成即表示文件与进度已写入。
        writePredictionToFiles(predictOutput, event.getSsqCombinationBo(), event.getOpenDate(), isPersist);
        predictLogService.addOrUpdate(event.getOpenDate(), event.getPosition());
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
     * 是否入库：落在 P5~P95 主体区间内；边界未配置时放行，概率为空时拒绝。
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
        // 反向区间不入库，保持现有筛选行为。
        if (min > max) {
            return false;
        }
        double p = probability.doubleValue();
        return p >= min && p <= max;
    }
}
