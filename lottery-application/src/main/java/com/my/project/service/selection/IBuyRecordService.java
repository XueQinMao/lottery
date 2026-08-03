package com.my.project.service.selection;

import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.service.selection.enums.BuyRecordTypeEnums;
import com.my.project.service.selection.pojo.dto.BuyRecordDto;

import java.time.LocalDate;
import java.util.List;

/**
 * IBuyRecordService
 *
 * @author 刘强
 * @version 2025/12/29 20:03
 **/
public interface IBuyRecordService {
    void batchSave(LotteryAdjustRespBo adjustRespBo, LocalDate openData, BuyRecordTypeEnums type);

    List<BuyRecordDto> getByOpenDate(LocalDate localDate);
}
