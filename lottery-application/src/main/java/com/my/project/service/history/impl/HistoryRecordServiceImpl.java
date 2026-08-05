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
import com.my.project.service.history.pojo.vo.ColdHotTrendVo;
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
import com.my.project.service.support.OmissionUtils;
import com.my.project.service.support.analyse.ColdHotAnalysisUtils;
import com.my.project.llm.bo.ColdHotAnalysisBo;
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
        HistoryRecord anchor = historyRecordRepository.lambdaQuery().eq(HistoryRecord::getPeriod, period).one();
        if (anchor == null || anchor.getOpenDate() == null) {
            throw new IllegalArgumentException("期号不存在: " + period);
        }
        return historyRecordRepository.lambdaQuery().le(HistoryRecord::getOpenDate, anchor.getOpenDate())
            .orderByDesc(HistoryRecord::getOpenDate).last("limit " + size).list();
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
        Assert.notEmpty(latest);
        return OmissionUtils.omissionBallAnalyzer(latest, ballType, ball);
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
        List<List<Integer>> tailCounts = new ArrayList<>(10);
        for (int t = 0; t < 10; t++) {
            tailCounts.add(new ArrayList<>(chronological.size()));
        }

        for (HistoryRecord r : chronological) {
            List<Integer> reds =
                Arrays.asList(r.getNum1(), r.getNum2(), r.getNum3(), r.getNum4(), r.getNum5(), r.getNum6());
            int sum = reds.stream().mapToInt(Integer::intValue).sum();
            int span = Collections.max(reds) - Collections.min(reds);
            int primeCount = (int)reds.stream().filter(PRIMES::contains).count();
            int oddCount = (int)reds.stream().filter(n -> n % 2 == 1).count();
            boolean blueOdd = r.getSpecial() != null && r.getSpecial() % 2 == 1;
            int[] tails = new int[10];
            for (Integer ball : reds) {
                if (ball != null) {
                    tails[Math.floorMod(ball, 10)]++;
                }
            }

            periods.add(r.getPeriod());
            sums.add(sum);
            spans.add(span);
            primeCounts.add(primeCount);
            primeRatios.add(primeCount + ":" + (6 - primeCount));
            redOddCounts.add(oddCount);
            redOddEvenRatios.add(oddCount + ":" + (6 - oddCount));
            blueOddFlags.add(blueOdd ? 1 : 0);
            blueOddEvenLabels.add(blueOdd ? "奇" : "偶");
            for (int t = 0; t < 10; t++) {
                tailCounts.get(t).add(tails[t]);
            }
        }

        List<Integer> sumDeltas = new ArrayList<>(sums.size());
        for (int i = 0; i < sums.size(); i++) {
            if (i == 0) {
                sumDeltas.add(null);
            } else {
                sumDeltas.add(sums.get(i) - sums.get(i - 1));
            }
        }

        return FeatureStatsVo.builder().periods(periods).sumValues(sums).sumAvg(avg(sums))
            .sumDeltaValues(sumDeltas).sumDeltaAvg(avg(sumDeltas)).spanValues(spans)
            .spanAvg(avg(spans)).primeCounts(primeCounts).primeRatios(primeRatios).primeAvg(avg(primeCounts))
            .redOddCounts(redOddCounts).redOddEvenRatios(redOddEvenRatios).redOddAvg(avg(redOddCounts))
            .blueOddFlags(blueOddFlags).blueOddEvenLabels(blueOddEvenLabels).blueOddAvg(avg(blueOddFlags))
            .tailCounts(tailCounts)
            .tailAvgs(tailCounts.stream().map(HistoryRecordServiceImpl::avg).toList()).build();
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
        return OmissionUtils.omissionFeatureTrendAnalyzer(feature, ratio, latestNewestFirst);
    }

    @Override
    public ColdHotTrendVo analyzeColdHotTrend(int sampleSize, String endPeriod) {
        int size = Math.max(sampleSize, 1);
        // 每期需前 30 期作为冷温热分类样本，故多取 30 期
        int fetchSize = size + 30;
        List<HistoryRecord> latest = getRecordsEndingAt(endPeriod, fetchSize);
        if (CollectionUtil.isEmpty(latest) || latest.size() <= 30) {
            throw new IllegalStateException("历史开奖记录不足，至少需要 31 期才能计算冷温热趋势");
        }
        // latest 为降序（最新在前）。可展示的样本为 latest[0..size-1]，
        // 对下标 i 的期，其前 30 期为 latest[i+1..i+30]。
        int displayCount = Math.min(size, latest.size() - 30);

        // 按期号升序（最旧 → 最新）输出
        List<String> periods = new ArrayList<>(displayCount);
        List<Integer> redHotCounts = new ArrayList<>(displayCount);
        List<Integer> redWarmCounts = new ArrayList<>(displayCount);
        List<Integer> redColdCounts = new ArrayList<>(displayCount);
        List<Integer> blueBandFlags = new ArrayList<>(displayCount);
        List<String> blueBandLabels = new ArrayList<>(displayCount);
        String basis = null;

        // 从最旧的展示期向最新遍历：i 从 displayCount-1 递减到 0
        for (int i = displayCount - 1; i >= 0; i--) {
            List<HistoryRecord> prior30 = latest.subList(i + 1, i + 31);
            ColdHotAnalysisBo bo = ColdHotAnalysisUtils.calculate(prior30);
            if (basis == null) {
                basis = bo.getBasis();
            }
            Set<Integer> redHot = new HashSet<>(bo.getRedHotBalls());
            Set<Integer> redWarm = new HashSet<>(bo.getRedWarmBalls());
            Set<Integer> redCold = new HashSet<>(bo.getRedColdBalls());
            Set<Integer> blueHot = new HashSet<>(bo.getBlueHotBalls());
            Set<Integer> blueWarm = new HashSet<>(bo.getBlueWarmBalls());
            Set<Integer> blueCold = new HashSet<>(bo.getBlueColdBalls());

            HistoryRecord cur = latest.get(i);
            List<Integer> reds =
                Arrays.asList(cur.getNum1(), cur.getNum2(), cur.getNum3(), cur.getNum4(), cur.getNum5(), cur.getNum6());
            int hot = 0, warm = 0, cold = 0;
            for (Integer r : reds) {
                if (r == null) continue;
                if (redHot.contains(r)) hot++;
                else if (redWarm.contains(r)) warm++;
                else if (redCold.contains(r)) cold++;
            }
            int blueBand;
            String blueLabel;
            Integer blue = cur.getSpecial();
            if (blue != null && blueHot.contains(blue)) {
                blueBand = 2;
                blueLabel = "热";
            } else if (blue != null && blueWarm.contains(blue)) {
                blueBand = 1;
                blueLabel = "温";
            } else {
                blueBand = 0;
                blueLabel = "冷";
            }

            periods.add(cur.getPeriod());
            redHotCounts.add(hot);
            redWarmCounts.add(warm);
            redColdCounts.add(cold);
            blueBandFlags.add(blueBand);
            blueBandLabels.add(blueLabel);
        }

        return ColdHotTrendVo.builder()
            .periods(periods)
            .redHotCounts(redHotCounts).redWarmCounts(redWarmCounts).redColdCounts(redColdCounts)
            .redHotAvg(avg(redHotCounts)).redWarmAvg(avg(redWarmCounts)).redColdAvg(avg(redColdCounts))
            .blueBandFlags(blueBandFlags).blueBandLabels(blueBandLabels)
            .blueHotAvg(avg(blueBandFlags.stream().map(f -> f == 2 ? 1 : 0).toList()))
            .blueWarmAvg(avg(blueBandFlags.stream().map(f -> f == 1 ? 1 : 0).toList()))
            .blueColdAvg(avg(blueBandFlags.stream().map(f -> f == 0 ? 1 : 0).toList()))
            .basis(basis)
            .build();
    }

    private static double avg(List<Integer> values) {
        double mean = values.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0);
        return Math.round(mean * 100.0) / 100.0;
    }
}
