package com.my.project.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.my.project.persistence.entity.PredictFileRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 预测/特征结果文件记录 Mapper
 * </p>
 *
 * @author 刘强
 * @since 2026-09-11
 */
@Mapper
public interface PredictFileRecordMapper extends BaseMapper<PredictFileRecord> {
}
