package com.my.project.service.record.impl;

import com.my.project.persistence.entity.PredictFileRecord;
import com.my.project.persistence.repository.IPredictFileRecordRepository;
import com.my.project.service.record.IPredictFileRecordService;
import com.my.project.service.record.pojo.enums.PredictFileRecordType;
import com.my.project.service.record.pojo.vo.PredictFileRecordVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * PredictFileRecordServiceImpl
 *
 * @author 刘强
 * @version 2026/09/11
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class PredictFileRecordServiceImpl implements IPredictFileRecordService {

    private final IPredictFileRecordRepository predictFileRecordRepository;

    @Override
    public void record(PredictFileRecordType type, String fileName, String filePath) {
        if (type == null || StringUtils.isBlank(fileName)) {
            return;
        }
        try {
            PredictFileRecord entity = new PredictFileRecord();
            entity.setType(type.getCode());
            entity.setFileName(fileName);
            entity.setFilePath(filePath);
            predictFileRecordRepository.save(entity);
        } catch (Exception e) {
            // best-effort：记录失败不影响主流程
            log.warn("写入预测文件记录失败: type={}, fileName={}", type.getCode(), fileName, e);
        }
    }

    @Override
    public List<PredictFileRecordVo> listByType(PredictFileRecordType type, int limit) {
        int size = Math.max(Math.min(limit, 100), 1);
        return predictFileRecordRepository.lambdaQuery()
                .eq(type != null, PredictFileRecord::getType, type == null ? null : type.getCode())
                .orderByDesc(PredictFileRecord::getCreateTime)
                .last("LIMIT " + size)
                .list()
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    public String findPathByFileName(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return null;
        }
        return predictFileRecordRepository.lambdaQuery()
                .eq(PredictFileRecord::getFileName, fileName)
                .oneOpt()
                .map(PredictFileRecord::getFilePath)
                .orElse(null);
    }

    private PredictFileRecordVo toVo(PredictFileRecord entity) {
        return PredictFileRecordVo.builder()
                .id(entity.getId())
                .type(entity.getType())
                .typeName(Optional.ofNullable(PredictFileRecordType.of(entity.getType()))
                        .map(PredictFileRecordType::getLabel).orElse(entity.getType()))
                .fileName(entity.getFileName())
                .filePath(entity.getFilePath())
                .createTime(entity.getCreateTime() == null ? null
                        : entity.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .build();
    }
}
