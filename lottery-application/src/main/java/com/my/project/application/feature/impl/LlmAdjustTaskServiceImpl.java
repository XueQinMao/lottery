package com.my.project.application.feature.impl;

import com.my.project.application.feature.dto.RecommendationCommand;

import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.application.feature.LlmAdjustTaskService;
import com.my.project.application.feature.LotteryFeatureAnalysisService;
import com.my.project.application.feature.enums.LlmAdjustTaskMode;
import com.my.project.application.feature.enums.LlmAdjustTaskStatus;
import com.my.project.application.feature.dto.LlmAdjustmentCommand;
import com.my.project.application.feature.dto.LlmRecommendationTaskCommand;
import com.my.project.application.feature.vo.LlmAdjustTaskVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 内存任务表 + 单飞线程池执行 LLM 推荐。
 **/
@Slf4j
@Service
public class LlmAdjustTaskServiceImpl implements LlmAdjustTaskService {

    private static final Duration RETAIN = Duration.ofMinutes(30);
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 10;
    private static final int DEFAULT_COUNT = 2;

    private final LotteryFeatureAnalysisService lotteryFeatureAnalysisService;
    private final ThreadPoolExecutor llmAdjustExecutor;

    private final ConcurrentHashMap<String, RecommendationTask> tasks = new ConcurrentHashMap<>();
    private final AtomicReference<RecommendationTask> activeTask = new AtomicReference<>();

    public LlmAdjustTaskServiceImpl(
            LotteryFeatureAnalysisService lotteryFeatureAnalysisService,
            @Qualifier("llmAdjustExecutor") ThreadPoolExecutor llmAdjustExecutor) {
        this.lotteryFeatureAnalysisService = lotteryFeatureAnalysisService;
        this.llmAdjustExecutor = llmAdjustExecutor;
    }

    @Override
    public LlmAdjustTaskVo submit(LlmRecommendationTaskCommand command) {
        purgeExpired();
        Assert.notNull(command, "任务参数不能为空");
        LlmAdjustTaskMode mode = command.getMode();
        Assert.notNull(mode, "mode 必须为 FEATURE 或 CACHE");

        int count = command.getCount() == null ? DEFAULT_COUNT : command.getCount();
        Assert.isTrue(count >= MIN_COUNT && count <= MAX_COUNT, "count 范围为 1-10");
        boolean isTopN = command.getIsTopN() == null || command.getIsTopN();
        String userRequirement = StringUtils.trimToNull(command.getUserRequirement());

        RecommendationTask task = new RecommendationTask(UUID.randomUUID().toString(), mode, count, isTopN, userRequirement);
        if (!activeTask.compareAndSet(null, task)) {
            throw new IllegalStateException("已有推荐任务执行中");
        }
        tasks.put(task.taskId, task);
        try {
            llmAdjustExecutor.execute(() -> execute(task));
        } catch (RejectedExecutionException e) {
            activeTask.compareAndSet(task, null);
            tasks.remove(task.taskId);
            throw new IllegalStateException("推荐任务队列已满，请稍后重试");
        }
        log.info("LLM 推荐任务已下发 taskId={} mode={} count={}", task.taskId, mode, count);
        return task.snapshot();
    }

    @Override
    public LlmAdjustTaskVo get(String taskId) {
        purgeExpired();
        if (StringUtils.isBlank(taskId)) {
            return null;
        }
        RecommendationTask task = tasks.get(taskId);
        return task == null ? null : task.snapshot();
    }

    @Override
    public LlmAdjustTaskVo running() {
        purgeExpired();
        RecommendationTask task = activeTask.get();
        if (task == null || !task.status.isActive()) {
            return null;
        }
        return task.snapshot();
    }

    private void execute(RecommendationTask task) {
        task.status = LlmAdjustTaskStatus.RUNNING;
        try {
            RecommendationCommand command = switch (task.mode) {
                case FEATURE -> new RecommendationCommand.Feature(
                    LlmAdjustmentCommand.builder().count(task.count).userRequirement(task.userRequirement).build());
                case CACHE -> new RecommendationCommand.Cache(
                    task.count, task.isTopN, task.userRequirement);
            };
            LotteryAdjustViewBo view = lotteryFeatureAnalysisService.recommend(command);
            task.fileName = view == null ? null : view.getFileName();
            task.status = LlmAdjustTaskStatus.SUCCESS;
            log.info("LLM 推荐任务完成 taskId={} fileName={}", task.taskId, task.fileName);
        } catch (Exception e) {
            log.error("LLM 推荐任务失败 taskId={}", task.taskId, e);
            task.status = LlmAdjustTaskStatus.FAILED;
            task.message = StringUtils.defaultIfBlank(e.getMessage(), "推荐失败");
        } finally {
            task.finishedAt = Instant.now();
            activeTask.compareAndSet(task, null);
        }
    }

    private void purgeExpired() {
        Instant deadline = Instant.now().minus(RETAIN);
        tasks.entrySet().removeIf(entry -> {
            RecommendationTask task = entry.getValue();
            if (!task.status.isTerminal()) {
                return false;
            }
            Instant end = task.finishedAt != null ? task.finishedAt : task.createdAt;
            return end.isBefore(deadline);
        });
    }

    private static final class RecommendationTask {
        private final String taskId;
        private final LlmAdjustTaskMode mode;
        private final int count;
        private final boolean isTopN;
        private final String userRequirement;
        private final Instant createdAt = Instant.now();
        private volatile LlmAdjustTaskStatus status = LlmAdjustTaskStatus.PENDING;
        private volatile String message;
        private volatile String fileName;
        private volatile Instant finishedAt;

        private RecommendationTask(String taskId, LlmAdjustTaskMode mode, int count, boolean isTopN, String userRequirement) {
            this.taskId = taskId;
            this.mode = mode;
            this.count = count;
            this.isTopN = isTopN;
            this.userRequirement = userRequirement;
        }

        private LlmAdjustTaskVo snapshot() {
            return LlmAdjustTaskVo.builder()
                    .taskId(taskId)
                    .status(status)
                    .mode(mode)
                    .message(message)
                    .fileName(fileName)
                    .createdAt(createdAt)
                    .finishedAt(finishedAt)
                    .build();
        }
    }
}
