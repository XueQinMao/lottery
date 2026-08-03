package com.my.project.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.my.project.api.pojo.resp.Result;
import com.my.project.service.history.IHistoryRecordService;
import com.my.project.service.history.pojo.dto.HistoryRecordDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * HistoryRecordController
 *
 * @author 刘强
 * @version 2025/07/17 16:09
 **/
@RestController
@RequestMapping("api/history")
public class HistoryRecordController {

    @Autowired
    private IHistoryRecordService historyRecordService;

    @PostMapping()
    public Result<Void> syncHistoryRecords() {
        try {
            historyRecordService.syncHistoryRecords();
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping()
    public Result<Page<HistoryRecordDto>> findPage(
        @RequestParam(required = false, defaultValue = "10") int pageSize,
        @RequestParam(required = false, defaultValue = "0") int pageNum) {
        try {
            return Result.success(historyRecordService.findPage(new Page<>(pageNum, pageSize)));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

}
