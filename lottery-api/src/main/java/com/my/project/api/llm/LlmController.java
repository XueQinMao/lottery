package com.my.project.api.llm;


import lombok.RequiredArgsConstructor;

import com.my.project.api.response.ApiResponse;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.application.feature.LotteryFeatureAnalysisService;
import org.springframework.web.bind.annotation.*;


/**
 * LlmController
 *
 * <p>大模型号码特征分析接口。
 * <ul>
 *     <li>{@code GET /api/llm/analyze/latest?sampleSize=30}：自动拉取最近30期红蓝球形态分析</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/07/21 20:40
 **/
@RequiredArgsConstructor
@RestController
@RequestMapping("api/llm")
public class LlmController {

    private final LotteryFeatureAnalysisService lotteryFeatureAnalysisService;



    /**
     * 自动分析历史开奖；大模型分支固定最近30期，sampleSize兼容旧调用方。
     */
    @GetMapping("/analyze/latest")
    public ApiResponse<LotteryAnalysisRespBo> analyzeLatest(
            @RequestParam(required = false, defaultValue = "30") int sampleSize) {
        return ApiResponse.capture(() -> lotteryFeatureAnalysisService.analyzeLatest(sampleSize));
    }

}
