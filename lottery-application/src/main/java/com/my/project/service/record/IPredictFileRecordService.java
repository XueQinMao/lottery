package com.my.project.service.record;

import com.my.project.service.record.pojo.enums.PredictFileRecordType;
import com.my.project.service.record.pojo.vo.PredictFileRecordVo;

import java.util.List;

/**
 * IPredictFileRecordService
 *
 * <p>预测/特征结果文件记录的写入与历史查询。
 *
 * @author 刘强
 * @version 2026/09/11
 **/
public interface IPredictFileRecordService {

    /**
     * 记录一条文件信息（best-effort，失败仅告警，不抛异常）。
     *
     * @param type     类型
     * @param fileName 文件名
     * @param filePath 文件绝对路径
     */
    void record(PredictFileRecordType type, String fileName, String filePath);

    /**
     * 按类型查询历史记录（按创建时间倒序）。
     *
     * @param type  类型，为 null 时查全部
     * @param limit 最多返回条数
     * @return 历史记录列表
     */
    List<PredictFileRecordVo> listByType(PredictFileRecordType type, int limit);

    /**
     * 按文件名查询记录的绝对路径（用于回看时定位文件，不依赖固定目录）。
     *
     * @param fileName 文件名
     * @return 文件绝对路径；查不到返回 null
     */
    String findPathByFileName(String fileName);
}
