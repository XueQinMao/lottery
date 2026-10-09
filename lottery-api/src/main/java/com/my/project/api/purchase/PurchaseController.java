package com.my.project.api.purchase;

import lombok.RequiredArgsConstructor;

import com.my.project.api.response.ApiResponse;
import com.my.project.application.enums.PrizeLevelEnum;
import com.my.project.application.selection.BuyRecordService;
import com.my.project.application.selection.vo.BuyRecordVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * PurchaseController
 *
 * @author 刘强
 * @version 2026/08/06 15:51
 **/
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/buy")
public class PurchaseController {

    private final BuyRecordService buyRecordService;

    @GetMapping()
    public ApiResponse<Map<PrizeLevelEnum, List<BuyRecordVo>>> getByOpenDate() {
        return ApiResponse.success(buyRecordService.statisticsHitSituations());
    }

}
