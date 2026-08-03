package com.my.project.service.history;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.history.pojo.dto.HistoryRecordDto;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author liuqiang
 * @since 2025-07-17
 */
public interface IHistoryRecordService{

  void syncHistoryRecords();

  Page<HistoryRecordDto> findPage(Page<HistoryRecordDto> page);

  /**
   * 获取最近N期历史记录
   * @param count 期数
   * @return 历史记录列表
   */
  List<HistoryRecord> getLatestRecords(int count);
}
