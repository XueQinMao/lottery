package com.my.project.api.prediction;

import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.my.project.api.request.PredictionCacheRequest;
import com.my.project.api.request.PredictionRecordRequest;
import com.my.project.api.response.ApiResponse;
import com.my.project.application.enums.PrizeLevelEnum;
import com.my.project.application.predict.PredictCacheService;
import com.my.project.application.predict.PredictHitRecordService;
import com.my.project.application.predict.PredictRecordService;
import com.my.project.application.predict.vo.PredictCacheVo;
import com.my.project.application.predict.vo.PredictHitRecordVo;
import com.my.project.application.predict.vo.PredictRecordVo;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


/**
 * <p>
 * 彩票推荐结果表 前端控制器
 * </p>
 *
 * @author liuqiang
 * @since 2025-07-29
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/predictResult")
public class PredictionController {

    private final PredictRecordService predictRecordService;

    private final PredictHitRecordService predictHitRecordService;

    private final PredictCacheService predictCacheService;

    @PostMapping()
    public ApiResponse<IPage<PredictRecordVo>> findPage(@RequestBody PredictionRecordRequest request) {
        try {
            Page<PredictRecordVo> page =
                    new Page<>(request.getPage().getPageNum(), request.getPage().getPageSize());
            return ApiResponse.success(predictRecordService.findPage(page, request.getOpenDate()));
        } catch (Exception e) {
            return ApiResponse.error("查询失败".concat(e.getMessage()));
        }
    }


    @PostMapping("getHitsByPrizeLevel")
    public ApiResponse<Map<PrizeLevelEnum, List<PredictHitRecordVo>>> getHitsByPrizeLevel(
        @RequestBody PredictionRecordRequest request) {
        try {
            return ApiResponse.success(predictHitRecordService.getHitsByPrizeLevel(request.getOpenDate()));
        } catch (Exception e) {
            return ApiResponse.error("查询失败".concat(e.getMessage()));
        }
    }

    @PostMapping("/predict")
    public ApiResponse<PredictCacheVo> getPredictCache(@RequestBody PredictionCacheRequest request) {
        return ApiResponse.success(predictCacheService.queryCache(request.getSize(), request.getKeys()));
    }
}
