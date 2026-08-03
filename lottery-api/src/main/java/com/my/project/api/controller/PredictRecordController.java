package com.my.project.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.my.project.api.pojo.req.PredictCacheReq;
import com.my.project.api.pojo.req.PredictRecordReq;
import com.my.project.api.pojo.resp.Result;
import com.my.project.service.enums.PrizeLevelEnum;
import com.my.project.service.predict.IPredictCacheService;
import com.my.project.service.predict.IPredictHitRecordService;
import com.my.project.service.predict.IPredictRecordService;
import com.my.project.service.predict.pojo.vo.PredictCacheVo;
import com.my.project.service.predict.pojo.vo.PredictHitRecordVo;
import com.my.project.service.predict.pojo.vo.PredictRecordVo;
import jakarta.annotation.Resource;
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
@RestController
@RequestMapping("/api/predictResult")
public class PredictRecordController {

    @Resource
    private IPredictRecordService predictRecordService;

    @Resource
    private IPredictHitRecordService predictHitRecordService;

    @Resource
    private IPredictCacheService predictCacheService;

    @PostMapping()
    public Result<IPage<PredictRecordVo>> findPage(@RequestBody PredictRecordReq request) {
        try {
            Page<PredictRecordVo> page =
                    new Page<>(request.getPage().getPageNum(), request.getPage().getPageSize());
            return Result.success(predictRecordService.findPage(page, request.getOpenDate()));
        } catch (Exception e) {
            return Result.error("查询失败".concat(e.getMessage()));
        }
    }


    @PostMapping("getHitsByPrizeLevel")
    public Result<Map<PrizeLevelEnum, List<PredictHitRecordVo>>> getHitsByPrizeLevel(
        @RequestBody PredictRecordReq request) {
        try {
            return Result.success(predictHitRecordService.getHitsByPrizeLevel(request.getOpenDate()));
        } catch (Exception e) {
            return Result.error("查询失败".concat(e.getMessage()));
        }
    }

    @PostMapping("/predict")
    public Result<PredictCacheVo> getPredictCache(@RequestBody PredictCacheReq req) {
        return Result.success(predictCacheService.queryCache(req.getSize(), req.getKeys()));
    }
}
