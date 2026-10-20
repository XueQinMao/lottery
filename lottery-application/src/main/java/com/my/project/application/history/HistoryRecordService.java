package com.my.project.application.history;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.application.history.dto.HistoryRecordDto;
import com.my.project.application.history.vo.ColdHotTrendVo;
import com.my.project.application.history.vo.DrawTrendVo;
import com.my.project.application.history.vo.FeatureStatsVo;
import com.my.project.application.history.vo.PatternTrendVo;
import com.my.project.application.history.vo.NumberTrendVo;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author liuqiang
 * @since 2025-07-17
 */
public interface HistoryRecordService{

  /** 按期升序的红球、蓝球特征趋势；冷温热使用每期开奖前 30 期分类。 */
  DrawTrendVo analyzeDrawTrends(int sampleSize, String endPeriod);

  NumberTrendVo analyzeNumberTrends(int sampleSize, String endPeriod);

  void syncHistoryRecords();

  Page<HistoryRecordDto> findPage(Page<HistoryRecordDto> page);

  /**
   * 获取最近N期历史记录
   * @param count 期数
   * @return 历史记录列表（开奖日降序：最新→最旧）
   */
  List<HistoryRecord> getLatestRecords(int count);

  /**
   * 获取截止到指定期号（含该期）往前共 {@code count} 期。
   * <p>{@code endPeriod} 为空时等价于 {@link #getLatestRecords(int)}。
   *
   * @param endPeriod 截止期号，如 2026092；空白则取最新
   * @param count     期数
   * @return 历史记录列表（开奖日降序：截止期→更旧）
   */
  List<HistoryRecord> getRecordsEndingAt(String endPeriod, int count);

  /** 返回指定期及之前的开奖记录，期号尚未入库时返回空列表。 */
  List<HistoryRecord> findRecordsEndingAt(String endPeriod, int count);

  /**
   * 统计红球和值、跨度、和值相对上一期增长差值、质合比，以及红/蓝球奇偶比。
   *
   * @param sampleSize 最近期数
   * @param endPeriod  截止期号（含），空则最新
   * @return 形态统计结果
   */
  FeatureStatsVo analyzeFeatureStats(int sampleSize, String endPeriod);

    /**
     * 为 LLM 特征分析生成形态快照，复用已拉取的最近 N 期（最新在前）。
     */
    PatternTrendVo analyzePatternTrend(String feature, String ratio, List<HistoryRecord> latestNewestFirst);

    /**
     * 每期开奖号码中"冷/温/热"号个数趋势。
     * <p>对样本内每一期，取其前 30 期作为子样本调用 {@code ColdHotAnalysisUtils.calculate}
     * 得到冷温热号清单，再统计本期实际开出的红球/蓝球落入各档的个数。
     *
     * @param sampleSize 展示期数（每期需多取 30 期作为前置样本）
     * @param endPeriod  截止期号（含），空则最新
     * @return 冷温热号个数趋势（按期号升序）
     */
    ColdHotTrendVo analyzeColdHotTrend(int sampleSize, String endPeriod);
}
