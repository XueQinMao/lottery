package com.my.project.application.history.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.infrastructure.persistence.repository.HistoryRecordRepository;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.history.dto.HistoryRecordDto;
import com.my.project.application.history.client.WebsiteDrawItemDto;
import com.my.project.application.history.client.SsqDrawHistoryClient;
import com.my.project.application.history.support.ColdHotTrendCalculator;
import com.my.project.application.history.support.FeatureStatsCalculator;
import com.my.project.application.history.vo.ColdHotTrendVo;
import com.my.project.application.history.vo.DrawTrendVo;
import com.my.project.application.history.support.DrawTrendCalculator;
import com.my.project.application.history.vo.FeatureStatsVo;
import com.my.project.application.history.vo.PatternTrendVo;
import com.my.project.application.history.vo.NumberTrendVo;
import com.my.project.application.history.support.NumberTrendCalculator;
import com.my.project.application.support.OmissionUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author liuqiang
 * @since 2025-07-17
 */
@Service
@Primary
public class HistoryRecordServiceImpl implements HistoryRecordService {

    private final HistoryRecordRepository historyRecordRepository;
    private final SsqDrawHistoryClient drawHistoryClient;

    public HistoryRecordServiceImpl(HistoryRecordRepository historyRecordRepository, SsqDrawHistoryClient drawHistoryClient) {
        this.historyRecordRepository = historyRecordRepository;
        this.drawHistoryClient = drawHistoryClient;
    }

    @Override
    public void syncHistoryRecords() {
        var latest = historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last("limit 1").one();
        LocalDate cutoff = latest == null ? null : latest.getOpenDate();
        var fetched = new ArrayList<HistoryRecord>();
        for (int page = 1; ; page++) {
            var records = drawHistoryClient.fetchNewerThan(page, cutoff).stream()
                .map(HistoryRecordServiceImpl::toHistoryRecord).toList();
            if (records.isEmpty()) break;
            fetched.addAll(records);
        }
        // 全部分页拉取成功后再入库，避免中途失败把同步截止日期提前到第一页。
        if (!fetched.isEmpty() && !historyRecordRepository.saveOrUpdateBatch(fetched)) {
            throw new IllegalStateException("开奖记录入库失败");
        }
    }

    @Override
    public Page<HistoryRecordDto> findPage(Page<HistoryRecordDto> page) {
        var resultPage = historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate)
            .page(new Page<>(page.getCurrent(), page.getSize()));
        var collect = CollectionUtil.emptyIfNull(resultPage.getRecords()).stream()
            .map(HistoryRecordServiceImpl::toDto).collect(Collectors.toCollection(ArrayList::new));
        return page.setRecords(collect);
    }

    private static HistoryRecord toHistoryRecord(WebsiteDrawItemDto item) {
        String[] split = item.getRed().split(",");
        return HistoryRecord.builder().period(item.getCode()).type("SSQ")
            .openDate(SsqDrawHistoryClient.parseDate(item.getDate()))
            .num1(Integer.valueOf(split[0])).num2(Integer.valueOf(split[1])).num3(Integer.valueOf(split[2]))
            .num4(Integer.valueOf(split[3])).num5(Integer.valueOf(split[4])).num6(Integer.valueOf(split[5]))
            .special(Integer.valueOf(item.getBlue())).build();
    }

    private static HistoryRecordDto toDto(HistoryRecord record) {
        return new HistoryRecordDto(record.getId(), record.getType(), record.getPeriod(), record.getOpenDate(),
            record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(), record.getNum5(),
            record.getNum6(), record.getSpecial(), record.getCreateTime());
    }

    @Override
    public List<HistoryRecord> getLatestRecords(int count) {
        return historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last("limit " + count)
            .list();
    }

    @Override
    public List<HistoryRecord> getRecordsEndingAt(String endPeriod, int count) {
        if (!StringUtils.hasText(endPeriod)) {
            return getLatestRecords(Math.max(count, 1));
        }
        var records = findRecordsEndingAt(endPeriod, count);
        if (records.isEmpty()) throw new IllegalArgumentException("期号不存在: " + endPeriod.trim());
        return records;
    }

    @Override
    public List<HistoryRecord> findRecordsEndingAt(String endPeriod, int count) {
        int size = Math.max(count, 1);
        String period = endPeriod.trim();
        HistoryRecord anchor = historyRecordRepository.lambdaQuery().eq(HistoryRecord::getPeriod, period).one();
        if (anchor == null || anchor.getOpenDate() == null) {
            return List.of();
        }
        return historyRecordRepository.lambdaQuery().le(HistoryRecord::getOpenDate, anchor.getOpenDate())
            .orderByDesc(HistoryRecord::getOpenDate).last("limit " + size).list();
    }

    @Override
    public NumberTrendVo analyzeNumberTrends(int sampleSize, String endPeriod) {
        String period = validateTrendQuery(sampleSize, endPeriod);
        List<HistoryRecord> latest = getRecordsEndingAt(period, sampleSize);
        if (latest.isEmpty()) return NumberTrendCalculator.calculate(List.of(), sampleSize);
        // Seed from all earlier stored draws so changing the visible window never resets omissions.
        List<HistoryRecord> chronological = new ArrayList<>(historyRecordRepository.lambdaQuery()
            .lt(HistoryRecord::getOpenDate, latest.getLast().getOpenDate())
            .orderByAsc(HistoryRecord::getOpenDate).list());
        chronological.addAll(latest.reversed());
        return NumberTrendCalculator.calculate(chronological, sampleSize);
    }

    @Override
    public DrawTrendVo analyzeDrawTrends(int sampleSize, String endPeriod) {
        String period = validateTrendQuery(sampleSize, endPeriod);
        return DrawTrendCalculator.calculate(
            getRecordsEndingAt(period, sampleSize + DrawTrendCalculator.COLD_HOT_WINDOW), sampleSize);
    }

    private static String validateTrendQuery(int sampleSize, String endPeriod) {
        if (sampleSize < 1 || sampleSize > 100) {
            throw new IllegalArgumentException("展示期数应为 1–100");
        }
        String period = endPeriod == null ? null : endPeriod.trim();
        if (StringUtils.hasText(period) && !period.matches("[0-9]{7}")) {
            throw new IllegalArgumentException("截止期号应为 7 位数字，如 2026092");
        }
        return period;
    }

    @Override
    public FeatureStatsVo analyzeFeatureStats(int sampleSize, String endPeriod) {
        int size = Math.max(sampleSize, 1);
        List<HistoryRecord> latest = getRecordsEndingAt(endPeriod, size);
        if (CollectionUtil.isEmpty(latest)) {
            throw new IllegalStateException("无可用的历史开奖记录");
        }
        return FeatureStatsCalculator.calculate(latest);
    }

    @Override
    public PatternTrendVo analyzePatternTrend(String feature, String ratio, List<HistoryRecord> latestNewestFirst) {
        return OmissionUtils.omissionFeatureTrendAnalyzer(feature, ratio, latestNewestFirst);
    }

    @Override
    public ColdHotTrendVo analyzeColdHotTrend(int sampleSize, String endPeriod) {
        int size = Math.max(sampleSize, 1);
        return ColdHotTrendCalculator.calculate(getRecordsEndingAt(endPeriod, size + 30), size);
    }
}
