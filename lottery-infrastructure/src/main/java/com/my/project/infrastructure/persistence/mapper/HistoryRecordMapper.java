package com.my.project.infrastructure.persistence.mapper;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author liuqiang
 * @since 2025-07-17
 */
@Mapper
public interface HistoryRecordMapper extends BaseMapper<HistoryRecord> {

}
