package com.my.project.api.history;

import com.my.project.api.response.ApiResponse;
import com.my.project.application.review.DrawReview;
import com.my.project.application.review.DrawReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history/reviews")
@RequiredArgsConstructor
public class DrawReviewController {
    private final DrawReviewService service;

    @GetMapping
    public ResponseEntity<ApiResponse<DrawReview>> find(@RequestParam(required = false) String period) {
        return service.find(period)
            .map(review -> ResponseEntity.ok(ApiResponse.success(review)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(404,
                period == null ? "暂无已入库的开奖记录" : "该期开奖记录尚未入库", null)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalidPeriod(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiResponse<>(400, exception.getMessage(), null));
    }
}
