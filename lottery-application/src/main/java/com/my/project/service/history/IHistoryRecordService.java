package com.my.project.service.history;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.history.pojo.dto.HistoryRecordDto;
import com.my.project.service.history.pojo.vo.FeatureStatsVo;
import com.my.project.service.history.pojo.vo.PatternTrendVo;
import com.my.project.service.history.pojo.vo.TrendAnalysisVo;

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

  /**
   * 分析指定号码的遗漏/趋势指数（基于 {@code LotteryTrendUtils}）。
   *
   * @param ballType   red / blue
   * @param ball       号码（红 1-33，蓝 1-16）
   * @param sampleSize 最近期数
   * @param endPeriod  截止期号（含），空则最新
   * @return 趋势分析结果
   */
  TrendAnalysisVo analyzeTrend(String ballType, int ball, int sampleSize, String endPeriod);

  /**
   * 统计红球和值、差值（跨度）、质合比，以及红/蓝球奇偶比。
   *
   * @param sampleSize 最近期数
   * @param endPeriod  截止期号（含），空则最新
   * @return 形态统计结果
   */
  FeatureStatsVo analyzeFeatureStats(int sampleSize, String endPeriod);

    /**
     * 分析指定形态比例的遗漏与超额指数（基于 {@code LotteryFeatureTrendUtils}）。
     *
     * @param feature    oddEven / bigSmall / primeComp / ...
     * @param ratio      分桶键
     * @param sampleSize 最近期数
     * @param endPeriod  截止期号（含），空则最新
     * @return 形态趋势分析结果
     */
    PatternTrendVo analyzePatternTrend(String feature, String ratio, int sampleSize, String endPeriod);

    /**
     * 与 {@link #analyzePatternTrend(String, String, int, String)} 相同算法，复用已拉取的最近 N 期（最新在前）。
     */
    PatternTrendVo analyzePatternTrend(String feature, String ratio, List<HistoryRecord> latestNewestFirst);
}
