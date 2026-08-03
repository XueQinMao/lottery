package com.my.project.service.predict.pojo.vo;

import com.my.project.python.bo.ModelPredictOutputBo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * 预测缓存展示/查询对象
 *
 * @author 刘强
 * @version 2026/07/28 20:03
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PredictCacheVo {

   private ConcurrentSkipListMap<Double, Set<String>> scoreIndex;

   private Map<String, ModelPredictOutputBo> cacheDatas;

   private long totalCount;
}
