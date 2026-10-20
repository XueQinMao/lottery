package com.my.project.application.feature.impl;

import com.my.project.infrastructure.persistence.repository.HistoryRecordRepository;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.application.feature.csv.FeatureCsvExporter;
import com.my.project.application.feature.csv.TrainingFeatureCalculator;
import com.my.project.application.selection.model.SsqCombinationBo;
import com.my.project.application.feature.FeatureCalculatorService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;


/**
 * FeatureCalculatorServiceImpl
 * 特征数据相关计算
 *
 * <p>注意：自优化版起，<b>特征工程权威实现迁移至 Python 侧 {@code ssq_features.py}</b>。
 * 本类仍负责生成 history.csv（Python 训练/预测的原始数据源）以及 ml_features /
 * sequence / markov 三个 CSV（用于人工分析与兼容旧链路）。新特征列（AC 值、遗漏值、
 * 重号、012 路、三区分布）与多目标标签已同步加入 ml_features.csv，与
 * {@code ssq_features.FEATURE_COLUMNS / LABEL_COLUMNS} 保持一致。</p>
 *
 * @author 刘强
 * @version 2025/10/23 16:25
 **/
@Service
@Primary
public class FeatureCalculatorServiceImpl implements FeatureCalculatorService {

    private final HistoryRecordRepository historyRecordRepository;
    private final FeatureCsvExporter exporter;

    public FeatureCalculatorServiceImpl(HistoryRecordRepository historyRecordRepository, FeatureCsvExporter exporter) {
        this.historyRecordRepository = historyRecordRepository;
        this.exporter = exporter;
    }

    @Override
    public void calculateAndExportFeatures() throws IOException {
        var chronological = historyRecordRepository.lambdaQuery().list().stream()
            .sorted(Comparator.comparing(HistoryRecord::getPeriod)).toList();
        exporter.export(chronological);
    }

    @Override
    public Map<String, Object> calculateFeature(SsqCombinationBo combination) {
        return calculateFeatures(combination);
    }

    public static Map<String, Object> calculateFeatures(SsqCombinationBo combination) {
        return TrainingFeatureCalculator.calculateFeatures(combination);
    }
}
