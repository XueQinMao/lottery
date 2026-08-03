package com.my.project.service.selection.pojo.bo;

import com.my.project.service.support.NextLotteryDateUtils;
import lombok.Data;

import java.time.LocalDate;

/**
 * StartupContextBo
 *
 * @author 刘强
 * @version 2025/09/01 15:22
 **/
@Data
public class StartupContextBo<T> {

    // 当前处理步骤
    private String currentStep;

    // 处理结果
    private boolean success = true;

    // 错误信息
    private String errorMessage;

    private T result;

    private LocalDate openDate  = NextLotteryDateUtils.nextDrawDate();
}
