package com.my.project.api.controller;

import com.my.project.api.pojo.resp.Result;
import com.my.project.service.enums.PrizeLevelEnum;
import com.my.project.service.selection.IBuyRecordService;
import com.my.project.service.selection.pojo.vo.BuyRecordVo;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * BuyRecordController
 *
 * @author 刘强
 * @version 2026/08/06 15:51
 **/
@RestController
@RequestMapping("/api/buy")
public class BuyRecordController {

    @Resource
    private IBuyRecordService buyRecordService;

    @GetMapping()
    public Result<Map<PrizeLevelEnum, List<BuyRecordVo>>> getByOpenDate() {
        return Result.success(buyRecordService.statisticsHitSituations());
    }

}
