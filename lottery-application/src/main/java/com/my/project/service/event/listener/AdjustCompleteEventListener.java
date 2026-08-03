package com.my.project.service.event.listener;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.service.config.LotteryModelConfig;
import com.my.project.service.event.AdjustCompleteEvent;
import com.my.project.service.event.PredictCompleteEvent;
import com.my.project.service.predict.IPredictCacheService;
import com.my.project.service.predict.IPredictLogService;
import com.my.project.service.selection.IBuyRecordService;
import com.my.project.service.selection.ISmartSelectService;
import com.my.project.service.selection.enums.BuyRecordTypeEnums;
import com.my.project.service.selection.pojo.bo.SsqCombinationBo;
import com.my.project.service.selection.pojo.bo.WeightConfigBo;
import com.my.project.service.support.FileUtils;
import com.my.project.service.support.NextLotteryDateUtils;
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
public class AdjustCompleteEventListener {

    private final IBuyRecordService buyRecordService;

    @EventListener
    public void handleEvent(AdjustCompleteEvent event) {
        buyRecordService.batchSave(event.getAdjustRespBo(), NextLotteryDateUtils.nextDrawDate(),
            BuyRecordTypeEnums.MANUAL);
    }

}
