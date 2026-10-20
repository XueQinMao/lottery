package com.my.project.api.llm;

import com.my.project.api.response.ApiResponse;
import com.my.project.application.feature.FeatureTrendForecastService;
import com.my.project.application.feature.dto.FeatureTrendForecast.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/llm/feature-trends")
public class FeatureTrendForecastController {
    private final FeatureTrendForecastService service;

    @GetMapping("/input")
    public ApiResponse<Plan> input(@RequestParam(required = false) String endPeriod) {
        return ApiResponse.success(service.prepare(endPeriod));
    }

    @PostMapping
    public ApiResponse<Summary> analyze(@RequestBody Run request) {
        return ApiResponse.success(service.analyze(request));
    }

    @GetMapping
    public ApiResponse<List<String>> history(@RequestParam(required = false) String before,
                                             @RequestParam(defaultValue = "12") int limit) {
        return ApiResponse.success(service.recentPeriods(before, limit));
    }

    @GetMapping("/{targetPeriod}")
    public ApiResponse<List<Summary>> summaries(@PathVariable String targetPeriod) {
        return ApiResponse.success(service.findPeriod(targetPeriod));
    }

    @GetMapping("/{targetPeriod}/{featureCode}")
    public ResponseEntity<ApiResponse<Detail>> detail(@PathVariable String targetPeriod, @PathVariable String featureCode) {
        return service.findDetail(targetPeriod, featureCode)
            .map(value -> ResponseEntity.ok(ApiResponse.success(value)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(404, "未找到该期特征详情", null)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalidInput(IllegalArgumentException failure) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, failure.getMessage(), null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> analysisFailure(IllegalStateException failure) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiResponse<>(502, failure.getMessage(), null));
    }
}
