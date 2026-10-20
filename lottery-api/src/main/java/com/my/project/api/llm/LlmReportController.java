package com.my.project.api.llm;

import com.my.project.api.response.ApiResponse;
import com.my.project.application.report.LotteryReportService;
import com.my.project.application.report.PredictFileRecordVo;
import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/llm")
public class LlmReportController {
    private final LotteryReportService reportService;

    /**
     * 按文件名读取推荐详情。
     */
    @GetMapping("/adjust/history/{fileName:.+}")
    public ApiResponse<LotteryAdjustViewBo> loadAdjustHistory(@PathVariable String fileName) {
        return ApiResponse.capture(() -> reportService.loadAdjustHistory(fileName));
    }

    /**
     * 预测/特征结果文件历史列表（按创建时间倒序）。
     *
     * @param type 类型：RECOMMEND=号码推荐，ANALYSIS=特征预测；为空查全部
     * @param limit 最多返回条数（默认 4，上限 100）
     */
    @GetMapping("/file-history")
    public ApiResponse<List<PredictFileRecordVo>> listFileHistory(
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "4") int limit) {
        return ApiResponse.capture(() -> reportService.listFileHistory(type, limit));
    }

    /**
     * 按文件名读取特征预测结果详情。
     */
    @GetMapping("/analyze/history/{fileName:.+}")
    public ApiResponse<LotteryAnalysisRespBo> loadAnalysisHistory(@PathVariable String fileName) {
        return ApiResponse.capture(() -> reportService.loadAnalysisHistory(fileName));
    }
}
