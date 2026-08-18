package com.my.project.service.history.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.service.history.IHistoryRecordService;
import com.my.project.service.history.pojo.dto.HistoryRecordDto;
import com.my.project.service.history.pojo.client.SsqWebsiteClientDto;
import com.my.project.service.history.pojo.client.WebsiteDrawItemDto;
import com.my.project.service.history.pojo.vo.FeatureStatsVo;
import com.my.project.service.history.pojo.vo.PatternTrendVo;
import com.my.project.service.history.pojo.vo.TrendAnalysisVo;
import com.my.project.service.support.LotteryFeatureTrendUtils;
import com.my.project.service.support.LotteryFeatureTrendUtils.FeatureKind;
import com.my.project.service.support.LotteryPatternTrendUtils;
import com.my.project.service.support.LotteryPatternTrendUtils.PatternTrendResult;
import com.my.project.service.support.LotteryPatternTrendUtils.PatternTrendStats;
import com.my.project.service.support.LotteryTrendUtils;
import com.my.project.service.support.LotteryTrendUtils.TrendAnalysisResult;
import com.my.project.service.support.LotteryTrendUtils.TrendStats;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
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
public class HistoryRecordServiceImpl implements IHistoryRecordService {

    private final Logger logger = LoggerFactory.getLogger(HistoryRecordServiceImpl.class);

    private static final String URL_FORMAT =
        "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice?name=%s&pageNo=%d&pageSize=10&systemType=PC";

    /** 红球质数（1 视为合数） */
    private static final Set<Integer> PRIMES = Set.of(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31);

    private final IHistoryRecordRepository historyRecordRepository;

    public HistoryRecordServiceImpl(IHistoryRecordRepository historyRecordRepository) {
        this.historyRecordRepository = historyRecordRepository;
    }

    @Override
    public void syncHistoryRecords() {
        var lastRecord =
            historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last("limit 1").one();
        List<WebsiteDrawItemDto> results = null;
        var pageNum = new AtomicInteger(1);
        do {
            results = fetchByPageNumber(pageNum.getAndAdd(1), NumberUtils.INTEGER_ZERO,
                Objects.isNull(lastRecord) ? null : lastRecord.getOpenDate());
            var list = Optional.ofNullable(results).orElse(Collections.emptyList()).stream().map(mapper()).toList();
            if (CollectionUtil.isNotEmpty(list)) {
                historyRecordRepository.saveOrUpdateBatch(list);
            }
        } while (CollectionUtil.isNotEmpty(results));
    }

    private List<WebsiteDrawItemDto> fetchByPageNumber(Integer pageNum, Integer retry, LocalDate lastOpenDate) {
        Assert.isTrue(retry < 3, "重试次数超过3次，请检查网络");
        SsqWebsiteClientDto resp = null;
        try {
            var respStr = HttpUtil.get(String.format(URL_FORMAT, "ssq", pageNum), 15000);
            Assert.notBlank(respStr, "http response is null");
            resp = JSON.parseObject(respStr, SsqWebsiteClientDto.class);
            logger.info("请求成功，开始处理数据 {}", respStr);
        } catch (Exception e) {
            if (e instanceof HttpException || e instanceof JSONException) {
                return fetchByPageNumber(pageNum, retry + 1, lastOpenDate);
            }
            logger.error("请求失败，请检查网络", e);
        }
        return Optional.ofNullable(resp).map(SsqWebsiteClientDto::getResult).orElse(Collections.emptyList()).stream()
            .map(r -> Pair.of(parseDate(r.getDate()), r))
            .filter(r -> Objects.isNull(lastOpenDate) || r.getLeft().isAfter(lastOpenDate)).map(Pair::getRight)
            .collect(Collectors.toList());
    }

    @Override
    public Page<HistoryRecordDto> findPage(Page<HistoryRecordDto> page) {
        var resultPage = historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate)
            .page(new Page<>(page.getCurrent(), page.getSize()));
        var collect = CollectionUtil.emptyIfNull(resultPage.getRecords()).stream()
            .map(r -> BeanUtil.copyProperties(r, HistoryRecordDto.class)).collect(Collectors.toList());
        return page.setRecords(collect);
    }

    private Function<WebsiteDrawItemDto, HistoryRecord> mapper() {
        return item -> {
            String[] split = item.getRed().split(",");
            return HistoryRecord.builder().period(item.getCode()).type("SSQ").openDate(parseDate(item.getDate()))
                .num1(Integer.valueOf(split[0])).num2(Integer.valueOf(split[1])).num3(Integer.valueOf(split[2]))
                .num4(Integer.valueOf(split[3])).num5(Integer.valueOf(split[4])).num6(Integer.valueOf(split[5]))
                .special(Integer.valueOf(item.getBlue())).build();
        };
    }

    private LocalDate parseDate(String dateString) {
        var pattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
        var matcher = pattern.matcher(dateString);

        if (matcher.find()) {
            var dateStr = matcher.group(1); // 提取 "2022-11-17"
            var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            return LocalDate.parse(dateStr, formatter); // 转换为 LocalDate
        } else {
            throw new IllegalArgumentException("无法从输入中提取日期: " + dateString);
        }
    }

    @Override
    public List<HistoryRecord> getLatestRecords(int count) {
        return historyRecordRepository.lambdaQuery().orderByDesc(HistoryRecord::getOpenDate).last("limit " + count)
            .list();
    }

    @Override
    public List<HistoryRecord> getRecordsEndingAt(String endPeriod, int count) {
        int size = Math.max(count, 1);
        if (!StringUtils.hasText(endPeriod)) {
            return getLatestRecords(size);
        }
        String period = endPeriod.trim();
        HistoryRecord anchor = historyRecordRepository.lambdaQuery()
            .eq(HistoryRecord::getPeriod, period)
            .one();
        if (anchor == null || anchor.getOpenDate() == null) {
            throw new IllegalArgumentException("期号不存在: " + period);
        }
        return historyRecordRepository.lambdaQuery()
            .le(HistoryRecord::getOpenDate, anchor.getOpenDate())
            .orderByDesc(HistoryRecord::getOpenDate)
            .last("limit " + size)
            .list();
    }

    @Override
    public TrendAnalysisVo analyzeTrend(String ballType, int ball, int sampleSize, String endPeriod) {
        String type = StringUtils.hasText(ballType) ? ballType.trim().toLowerCase() : "red";
        if (!"red".equals(type) && !"blue".equals(type)) {
            throw new IllegalArgumentException("ballType 仅支持 red / blue");
        }
        int maxBall = "red".equals(type) ? 33 : 16;
        if (ball < 1 || ball > maxBall) {
            throw new IllegalArgumentException("号码超出范围: " + ball + "（" + type + " 应为 1-" + maxBall + "）");
        }
        int size = Math.max(sampleSize, 1);
        List<HistoryRecord> latest = getRecordsEndingAt(endPeriod, size);
        if (CollectionUtil.isEmpty(latest)) {
            throw new IllegalStateException("无可用的历史开奖记录");
        }
        // getRecordsEndingAt 为降序（截止期→更旧），趋势计算需要升序（最旧→最新）
        List<HistoryRecord> chronological = new ArrayList<>(latest);
        Collections.reverse(chronological);

        List<String> periods = new ArrayList<>(chronological.size());
        List<Set<Integer>> draws = new ArrayList<>(chronological.size());
        for (HistoryRecord r : chronological) {
            periods.add(r.getPeriod());
            if ("red".equals(type)) {
                draws.add(new HashSet<>(Arrays.asList(r.getNum1(), r.getNum2(), r.getNum3(), r.getNum4(), r.getNum5(),
                    r.getNum6())));
            } else {
                draws.add(Set.of(r.getSpecial()));
            }
        }

        TrendAnalysisResult result = LotteryTrendUtils.analyze(draws, ball);
        TrendStats stats = result.getStats();
        return TrendAnalysisVo.builder()
            .ballType(type)
            .ball(ball)
            .periods(periods)
            .omissions(result.getOmissions())
            .indexValues(result.getIndexValues())
            .ma5(result.getMa5())
            .ma10(result.getMa10())
            .ma20(result.getMa20())
            .arrangement(result.getArrangement())
            .ma5Slope(result.getMa5Slope())
            .phase(result.getPhase())
            .stats(TrendAnalysisVo.Stats.builder()
                .maxOmission(stats.getMaxOmission())
                .avgOmission(stats.getAvgOmission())
                .currentOmission(stats.getCurrentOmission())
                .indexMean(stats.getIndexMean())
                .hitCount(stats.getHitCount())
                .totalPeriods(stats.getTotalPeriods())
                .build())
            .build();
    }

    @Override
    public FeatureStatsVo analyzeFeatureStats(int sampleSize, String endPeriod) {
        int size = Math.max(sampleSize, 1);
        List<HistoryRecord> latest = getRecordsEndingAt(endPeriod, size);
        if (CollectionUtil.isEmpty(latest)) {
            throw new IllegalStateException("无可用的历史开奖记录");
        }
        List<HistoryRecord> chronological = new ArrayList<>(latest);
        Collections.reverse(chronological);

        List<String> periods = new ArrayList<>(chronological.size());
        List<Integer> sums = new ArrayList<>(chronological.size());
        List<Integer> spans = new ArrayList<>(chronological.size());
        List<Integer> primeCounts = new ArrayList<>(chronological.size());
        List<String> primeRatios = new ArrayList<>(chronological.size());
        List<Integer> redOddCounts = new ArrayList<>(chronological.size());
        List<String> redOddEvenRatios = new ArrayList<>(chronological.size());
        List<Integer> blueOddFlags = new ArrayList<>(chronological.size());
        List<String> blueOddEvenLabels = new ArrayList<>(chronological.size());

        for (HistoryRecord r : chronological) {
            List<Integer> reds = Arrays.asList(
                r.getNum1(), r.getNum2(), r.getNum3(), r.getNum4(), r.getNum5(), r.getNum6());
            int sum = reds.stream().mapToInt(Integer::intValue).sum();
            int span = Collections.max(reds) - Collections.min(reds);
            int primeCount = (int) reds.stream().filter(PRIMES::contains).count();
            int oddCount = (int) reds.stream().filter(n -> n % 2 == 1).count();
            boolean blueOdd = r.getSpecial() != null && r.getSpecial() % 2 == 1;

            periods.add(r.getPeriod());
            sums.add(sum);
            spans.add(span);
            primeCounts.add(primeCount);
            primeRatios.add(primeCount + ":" + (6 - primeCount));
            redOddCounts.add(oddCount);
            redOddEvenRatios.add(oddCount + ":" + (6 - oddCount));
            blueOddFlags.add(blueOdd ? 1 : 0);
            blueOddEvenLabels.add(blueOdd ? "奇" : "偶");
        }

        return FeatureStatsVo.builder()
            .periods(periods)
            .sumValues(sums)
            .sumAvg(avg(sums))
            .spanValues(spans)
            .spanAvg(avg(spans))
            .primeCounts(primeCounts)
            .primeRatios(primeRatios)
            .primeAvg(avg(primeCounts))
            .redOddCounts(redOddCounts)
            .redOddEvenRatios(redOddEvenRatios)
            .redOddAvg(avg(redOddCounts))
            .blueOddFlags(blueOddFlags)
            .blueOddEvenLabels(blueOddEvenLabels)
            .blueOddAvg(avg(blueOddFlags))
            .build();
    }

    @Override
    public PatternTrendVo analyzePatternTrend(String feature, String ratio, int sampleSize, String endPeriod) {
        int size = Math.max(sampleSize, 1);
        List<HistoryRecord> latest = getRecordsEndingAt(endPeriod, size);
        if (CollectionUtil.isEmpty(latest)) {
            throw new IllegalStateException("无可用的历史开奖记录");
        }
        return analyzePatternTrend(feature, ratio, latest);
    }

    @Override
    public PatternTrendVo analyzePatternTrend(String feature, String ratio, List<HistoryRecord> latestNewestFirst) {
        FeatureKind kind = FeatureKind.fromCode(feature);
        String normalizedRatio = LotteryFeatureTrendUtils.normalizeBucket(kind, ratio);
        if (CollectionUtil.isEmpty(latestNewestFirst)) {
            throw new IllegalStateException("无可用的历史开奖记录");
        }
        List<HistoryRecord> chronological = new ArrayList<>(latestNewestFirst);
        Collections.reverse(chronological);

        List<String> periods = new ArrayList<>(chronological.size());
        List<Boolean> hits = new ArrayList<>(chronological.size());
        List<String> actuals = new ArrayList<>(chronological.size());
        Map<String, Integer> ratioCounts = new LinkedHashMap<>();
        for (String bucket : LotteryFeatureTrendUtils.buckets(kind)) {
            ratioCounts.put(bucket, 0);
        }
        for (HistoryRecord r : chronological) {
            periods.add(r.getPeriod());
            String actual = LotteryFeatureTrendUtils.extract(redsOf(r), r.getSpecial(), kind);
            actuals.add(actual);
            hits.add(normalizedRatio.equals(actual));
            ratioCounts.merge(actual, 1, Integer::sum);
        }
        double p = LotteryFeatureTrendUtils.theoreticalProb(kind, normalizedRatio);
        PatternTrendResult result = LotteryPatternTrendUtils.analyze(hits, p);
        PatternTrendStats stats = result.getStats();

        HistoryRecord last = chronological.get(chronological.size() - 1);
        String lastRatio = actuals.get(actuals.size() - 1);

        List<PatternTrendVo.RatioOption> options = new ArrayList<>();
        for (String optRatio : LotteryFeatureTrendUtils.buckets(kind)) {
            int optHits = ratioCounts.getOrDefault(optRatio, 0);
            double optP = LotteryFeatureTrendUtils.theoreticalProb(kind, optRatio);
            double optTheory = chronological.size() * optP;
            List<Boolean> optHitFlags = new ArrayList<>(actuals.size());
            for (String actual : actuals) {
                optHitFlags.add(optRatio.equals(actual));
            }
            PatternTrendResult optResult = LotteryPatternTrendUtils.analyze(optHitFlags, optP);
            PatternTrendStats optStats = optResult.getStats();
            options.add(PatternTrendVo.RatioOption.builder()
                .ratio(optRatio)
                .hitCount(optHits)
                .theoreticalProb(LotteryPatternTrendUtils.round6(optP))
                .theoreticalHits(LotteryPatternTrendUtils.round2(optTheory))
                .index(LotteryPatternTrendUtils.round2(optHits - optTheory))
                .currentOmission(optStats.getCurrentOmission())
                .avgOmission(optStats.getAvgOmission())
                .maxOmission(optStats.getMaxOmission())
                .omissions(optResult.getOmissions())
                .indexValues(optResult.getIndexValues())
                .hitIntervals(hitIntervals(optHitFlags))
                .build());
        }

        return PatternTrendVo.builder()
            .feature(kind.getCode())
            .featureLabel(kind.getLabel())
            .ratio(normalizedRatio)
            .periods(periods)
            .hits(result.getHits())
            .omissions(result.getOmissions())
            .indexValues(result.getIndexValues())
            .latestPeriod(last.getPeriod())
            .latestWinning(formatWinning(last))
            .latestRatio(lastRatio)
            .actuals(actuals)
            .stats(PatternTrendVo.Stats.builder()
                .maxOmission(stats.getMaxOmission())
                .avgOmission(stats.getAvgOmission())
                .currentOmission(stats.getCurrentOmission())
                .hitCount(stats.getHitCount())
                .totalPeriods(stats.getTotalPeriods())
                .theoreticalProb(stats.getTheoreticalProb())
                .theoreticalHits(stats.getTheoreticalHits())
                .index(stats.getIndex())
                .build())
            .ratioOptions(options)
            .build();
    }

    private static List<Integer> hitIntervals(List<Boolean> hits) {
        List<Integer> intervals = new ArrayList<>();
        int lastHit = -1;
        for (int i = 0; i < hits.size(); i++) {
            if (!Boolean.TRUE.equals(hits.get(i))) {
                continue;
            }
            if (lastHit >= 0) {
                intervals.add(i - lastHit);
            }
            lastHit = i;
        }
        return intervals;
    }

    private static List<Integer> redsOf(HistoryRecord r) {
        return Arrays.asList(r.getNum1(), r.getNum2(), r.getNum3(), r.getNum4(), r.getNum5(), r.getNum6());
    }

    private static String formatWinning(HistoryRecord r) {
        return String.format("%02d %02d %02d %02d %02d %02d + %02d",
            r.getNum1(), r.getNum2(), r.getNum3(), r.getNum4(), r.getNum5(), r.getNum6(),
            r.getSpecial());
    }

    private static double avg(List<Integer> values) {
        double mean = values.stream().mapToInt(Integer::intValue).average().orElse(0);
        return Math.round(mean * 100.0) / 100.0;
    }
}
