package com.my.project.application.selection;

import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.application.enums.PrizeLevelEnum;
import com.my.project.application.selection.enums.BuyRecordType;
import com.my.project.application.selection.dto.BuyRecordDto;
import com.my.project.application.selection.vo.BuyRecordVo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * BuyRecordService
 *
 * @author 刘强
 * @version 2025/12/29 20:03
 **/
public interface BuyRecordService {
    void batchSave(LotteryAdjustRespBo adjustRespBo, LocalDate openData, BuyRecordType type);

    List<BuyRecordDto> getByOpenDate(LocalDate localDate);

    Map<PrizeLevelEnum, List<BuyRecordVo>> statisticsHitSituations();
}
