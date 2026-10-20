package com.my.project.application.report;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.application.config.LotteryModelProperties;
import com.my.project.application.feature.support.PatternFeatureAnalysisSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Report persistence and history queries; preserves existing files and best-effort writes. */
@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryReportService {
    private final LotteryModelProperties modelProperties;
    private final ReportFileIndexService reportFileIndexService;
    private static final DateTimeFormatter ADJUST_FILE_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
        .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern ADJUST_TIMESTAMP = Pattern.compile("adjust_(\\d{14})(?:_[\\w.-]+)?\\.json");

    public LotteryAdjustViewBo loadAdjustHistory(String fileName) {
        Assert.isTrue(isSafeAdjustFileName(fileName), "非法推荐文件名");
        File file = new File(adjustHistoryDir(), fileName);
        Assert.isTrue(file.isFile(), "推荐文件不存在");
        LotteryAdjustViewBo view = JSON.parseObject(FileUtil.readString(file, StandardCharsets.UTF_8),
                LotteryAdjustViewBo.class);
        Assert.notNull(view, "推荐文件解析失败");
        return view;
    }

    public List<PredictFileRecordVo> listFileHistory(String type, int limit) {
        PredictFileRecordType recordType = PredictFileRecordType.of(type);
        return reportFileIndexService.listByType(recordType, limit);
    }

    public List<LotteryAdjustViewBo> findRecommendations(String targetPeriod, Instant cutoff) {
        File dir = new File(modelProperties.getPath(), "adjust");
        if (!dir.exists()) return List.of();
        String[] names = dir.list((parent, name) -> ADJUST_TIMESTAMP.matcher(name).matches());
        if (names == null) throw new IllegalStateException("无法读取号码推荐目录");
        return Arrays.stream(names)
            .flatMap(name -> readRecommendation(name, targetPeriod, cutoff).stream())
            .sorted(Comparator.comparing(LotteryAdjustViewBo::getGeneratedAt).reversed()
                .thenComparing(LotteryAdjustViewBo::getFileName))
            .toList();
    }

    public Optional<LotteryAdjustViewBo> findRecommendation(String fileName, String targetPeriod, Instant cutoff) {
        if (fileName == null || !ADJUST_TIMESTAMP.matcher(fileName).matches()) return Optional.empty();
        var directory = new File(modelProperties.getPath(), "adjust");
        if (!new File(directory, fileName).isFile()) return Optional.empty();
        return readRecommendation(fileName, targetPeriod, cutoff);
    }

    private Optional<LotteryAdjustViewBo> readRecommendation(String name, String targetPeriod, Instant cutoff) {
        try {
            var view = loadAdjustHistory(name);
            if (!targetPeriod.equals(view.getTargetPeriod())) return Optional.empty();
            var generatedAt = view.getGeneratedAt();
            if (generatedAt == null) {
                // 旧报告的文件名由服务端本地时间生成。
                String timestamp = name.substring("adjust_".length(), "adjust_".length() + 14);
                generatedAt = LocalDateTime.parse(timestamp, ADJUST_FILE_TIME).atZone(ZoneId.systemDefault()).toInstant();
            }
            if (!generatedAt.isBefore(cutoff)) return Optional.empty();
            view.setGeneratedAt(generatedAt);
            view.setFileName(name);
            return Optional.of(view);
        } catch (IllegalArgumentException | DateTimeParseException | JSONException | IORuntimeException e) {
            log.warn("跳过无法读取的号码推荐报告: {}", name, e);
            return Optional.empty();
        }
    }

    public LotteryAnalysisRespBo loadAnalysisHistory(String fileName) {
        Assert.isTrue(isSafeFileName(fileName), "非法特征预测文件名");
        // 优先按库记录的绝对路径读取，兼容存量文件（如 feature.analysis.cache*.json 在 cache 目录）
        String filePath = reportFileIndexService.findPathByFileName(fileName);
        File file = StringUtils.isNotBlank(filePath) && new File(filePath).isFile()
                ? new File(filePath)
                : new File(analysisHistoryDir(), fileName);
        Assert.isTrue(file.isFile(), "特征预测文件不存在");
        LotteryAnalysisRespBo view = JSON.parseObject(FileUtil.readString(file, StandardCharsets.UTF_8),
                LotteryAnalysisRespBo.class);
        Assert.notNull(view, "特征预测文件解析失败");
        if (view.getFeatureAnalysis() == null) {
            view.setFeatureAnalysis(PatternFeatureAnalysisSupport.adaptLegacy(view.getFeatureForecast()));
        }
        return view;
    }

    public LotteryAdjustViewBo saveRecommendation(LotteryAdjustViewBo view) {
        try {
            var generatedAt = Instant.now();
            view.setGeneratedAt(generatedAt);
            String name = "adjust_" + ADJUST_FILE_TIME.format(generatedAt.atZone(ZoneId.systemDefault()))
                + "_" + UUID.randomUUID() + ".json";
            view.setFileName(name);
            File file = new File(adjustHistoryDir(), name);
            FileUtil.writeString(JSON.toJSONString(view), file, StandardCharsets.UTF_8);
            reportFileIndexService.record(PredictFileRecordType.RECOMMEND, name, file.getAbsolutePath());
        } catch (Exception e) {
            log.warn("保存推荐结果文件失败", e);
        }
        return view;
    }

    /**
     * 将特征预测结果落盘并写入文件记录表（仅在多级缓存未命中、重新计算后调用）。
     */
    public void saveAnalysis(LotteryAnalysisRespBo view, String cacheKey) {
        try {
            String name =cacheKey+".json";
            File file = new File(analysisHistoryDir(), name);
            FileUtil.writeString(JSON.toJSONString(view), file, StandardCharsets.UTF_8);
            reportFileIndexService.record(PredictFileRecordType.ANALYSIS, name, file.getAbsolutePath());
        } catch (Exception e) {
            log.warn("保存特征预测结果文件失败", e);
        }
    }

    private File adjustHistoryDir() {
        File dir = new File(modelProperties.getPath(), "adjust");
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("创建推荐结果目录失败: {}", dir.getAbsolutePath());
        }
        return dir;
    }

    private File analysisHistoryDir() {
        File dir = new File(modelProperties.getPath(), "analysis");
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("创建特征预测结果目录失败: {}", dir.getAbsolutePath());
        }
        return dir;
    }

    private static boolean isSafeAdjustFileName(String fileName) {
        return fileName != null && !fileName.contains("..") && !fileName.contains("/") && !fileName.contains("\\")
                && fileName.matches("adjust_[\\w.-]+\\.json");
    }

    /** 通用文件名安全校验：防路径穿越，允许字母数字下划线点连字符。 */
    private static boolean isSafeFileName(String fileName) {
        return fileName != null && !fileName.contains("..") && !fileName.contains("/") && !fileName.contains("\\")
                && fileName.matches("[\\w.-]+\\.json");
    }

}
