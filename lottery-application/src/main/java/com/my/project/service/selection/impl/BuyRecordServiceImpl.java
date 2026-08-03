package com.my.project.service.selection.impl;

import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.persistence.entity.BuyRecord;
import com.my.project.persistence.repository.IBuyRecordRepository;
import com.my.project.service.selection.IBuyRecordService;
import com.my.project.service.selection.enums.BuyRecordTypeEnums;
import com.my.project.service.selection.pojo.dto.BuyRecordDto;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * BuyRecordServiceImpl
 *
 * @author 刘强
 * @version 2025/12/29 20:03
 **/
@Service
@Primary
public class BuyRecordServiceImpl implements IBuyRecordService {

    private final IBuyRecordRepository buyRecordRepository;

    public BuyRecordServiceImpl(IBuyRecordRepository buyRecordRepository) {
        this.buyRecordRepository = buyRecordRepository;
    }

    @Override
    public void batchSave(LotteryAdjustRespBo adjustRespBo, LocalDate openDate, BuyRecordTypeEnums type) {
        var buyRecords = CollectionUtils.emptyIfNull(adjustRespBo.getAdjustedTickets()).stream().map(
            t -> BuyRecord.builder().type(type.name()).openDate(openDate)
                .oriRedBalls(StringUtils.join(t.getOriginalRedBalls(), ","))
                .oriBlueBall(String.valueOf(t.getOriginalBlueBall()))
                .adjustedBlueBalls(StringUtils.join(t.getAdjustedBlueBall(), ","))
                .adjustedRedBalls(String.valueOf(t.getAdjustedRedBalls()))
                .redBalls(StringUtils.join(t.getComplexTicket().getRedBalls(), ","))
                .blueBalls(StringUtils.join(t.getComplexTicket().getBlueBalls(), ","))
                .totalBets(t.getComplexTicket().getTotalBets()).reason(t.getReason()).createTime(LocalDateTime.now()).build()).toList();
        if (CollectionUtils.isNotEmpty(buyRecords)) {
            buyRecordRepository.saveOrUpdateBatch(buyRecords);
        }
    }

    @Override
    public List<BuyRecordDto> getByOpenDate(LocalDate localDate) {
        var buyRecords = buyRecordRepository.lambdaQuery().eq(BuyRecord::getOpenDate, localDate).list();
        return CollectionUtils.emptyIfNull(buyRecords).stream().map(
            b -> BuyRecordDto.builder().id(b.getId()).openDate(b.getOpenDate()).oriRedBalls(b.getOriRedBalls())
                .oriBlueBall(b.getOriBlueBall()).adjustedRedBalls(b.getAdjustedRedBalls())
                .adjustedBlueBall(b.getAdjustedBlueBalls()).redBalls(b.getRedBalls()).blueBalls(b.getBlueBalls())
                .reason(b.getReason()).build()).toList();
    }
}
