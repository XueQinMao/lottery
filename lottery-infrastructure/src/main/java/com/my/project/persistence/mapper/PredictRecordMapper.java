package com.my.project.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.my.project.persistence.entity.PredictRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author liuqiang
 * @since 2025-07-17
 */
@Mapper
public interface PredictRecordMapper extends BaseMapper<PredictRecord> {

    @Update("optimize table t_predict_result")
    void optimizeTable();
}
