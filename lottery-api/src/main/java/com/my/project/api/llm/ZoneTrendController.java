package com.my.project.api.llm;

import com.my.project.api.response.ApiResponse;
import com.my.project.application.feature.ZoneTrendAnalysisService;
import com.my.project.application.feature.dto.ZoneTrendAnalysisCommand;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/llm/zone-trends")
public class ZoneTrendController {
    private final ZoneTrendAnalysisService service;

    @PostMapping
    public ResponseEntity<ApiResponse<LotteryAnalysisRespBo>> analyze(@RequestBody ZoneTrendAnalysisCommand request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(service.analyze(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(400, e.getMessage(), null));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(new ApiResponse<>(502, e.getMessage(), null));
        }
    }
}
