package com.my.project.service.llm.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AdjustHistoryFileVo
 *
 * <p>推荐结果落盘文件的列表项，仅含名称与元数据，不含正文。
 *
 * @author 刘强
 * @version 2026/08/20
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjustHistoryFileVo {

    /** 文件名，如 adjust_20260820171530_c2_topNfalse.json */
    private String fileName;

    /** 最后修改时间（毫秒） */
    private Long lastModified;

    /** 文件大小（字节） */
    private Long size;
}
