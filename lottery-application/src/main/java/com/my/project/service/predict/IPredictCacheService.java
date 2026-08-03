package com.my.project.service.predict;

import com.my.project.python.bo.ModelPredictOutputBo;
import com.my.project.service.predict.pojo.vo.PredictCacheVo;
import com.my.project.service.selection.pojo.bo.SsqCombinationBo;

import java.time.LocalDate;
import java.util.List;

/**
 * IPredictCacheService 缓存处理
 *
 * @author 刘强
 * @version 2026/07/28 19:38
 **/
public interface IPredictCacheService {
    /**
     * 添加缓存
     *
     * @param predictResultBo
     * @param ssqCombinationBo
     * @param localDate
     */
    void addCache(ModelPredictOutputBo predictResultBo, SsqCombinationBo ssqCombinationBo, LocalDate localDate);

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
