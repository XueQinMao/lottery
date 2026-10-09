package com.my.project.application.predict;

import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.application.predict.vo.PredictCacheVo;
import com.my.project.application.selection.model.SsqCombinationBo;

import java.time.LocalDate;
import java.util.List;

/**
 * PredictCacheService 缓存处理
 *
 * @author 刘强
 * @version 2026/07/28 19:38
 **/
public interface PredictCacheService {
    /**
     * 添加缓存
     *
     * @param predictResultBo
     * @param combination
     * @param localDate
     */
    void addCache(ModelPredictOutputBo predictResultBo, SsqCombinationBo combination, LocalDate localDate);

    /**
     * 添加缓存
     * @param cacheKey
     * @param modelPredictOutput
     */
    void addCache(String cacheKey, ModelPredictOutputBo modelPredictOutput);




    /**
     * 获取缓存 Key
     *
     * @param openDate
     * @param redBalls
     * @param blue
     * @return
     */
    String getCacheKey(LocalDate openDate, List<Integer> redBalls, Integer blue);

    /**
     *
     * @param size
     * @param keys
     * @return
     */
    PredictCacheVo queryCache(Integer size, List<String> keys);
}
