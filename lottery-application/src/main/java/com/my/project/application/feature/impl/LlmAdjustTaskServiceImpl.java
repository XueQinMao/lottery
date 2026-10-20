package com.my.project.application.feature.impl;

import com.my.project.application.feature.LlmAdjustTaskService;
import com.my.project.application.feature.enums.LlmAdjustTaskStatus;
import com.my.project.application.feature.vo.LlmAdjustTaskVo;
import com.my.project.application.feature.vo.LlmAdjustTaskVo.Phase;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

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
    private final HybridRecommendationWorkflow recommendations;
    private final ThreadPoolExecutor llmAdjustExecutor;

    private final ConcurrentHashMap<String, RecommendationTask> tasks = new ConcurrentHashMap<>();
    private final AtomicReference<RecommendationTask> activeTask = new AtomicReference<>();

    public LlmAdjustTaskServiceImpl(
            HybridRecommendationWorkflow recommendations,
            @Qualifier("llmAdjustExecutor") ThreadPoolExecutor llmAdjustExecutor) {
        this.recommendations = recommendations;
        this.llmAdjustExecutor = llmAdjustExecutor;
    }

    @Override
    public LlmAdjustTaskVo submit(boolean useNumberTrend) {
        purgeExpired();
        RecommendationTask task = new RecommendationTask(useNumberTrend);
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
        log.info("LLM 推荐任务已下发 taskId={}", task.taskId);
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
        try (var ignored = MDC.putCloseable("llmRecommendTaskId", task.taskId)) {
            var view = recommendations.recommend(task.useNumberTrend, () -> task.phase = Phase.GENERATING_RECOMMENDATION);
            task.fileName = view.getFileName();
            task.status = LlmAdjustTaskStatus.SUCCESS;
            log.info("LLM 推荐任务完成 taskId={} fileName={}", task.taskId, task.fileName);
        } catch (Exception e) {
            log.error("LLM 推荐任务失败 taskId={}", task.taskId, e);
            task.message = StringUtils.defaultIfBlank(e.getMessage(), "推荐失败");
            task.status = LlmAdjustTaskStatus.FAILED;
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
        private final String taskId = UUID.randomUUID().toString();
        private final Instant createdAt = Instant.now();
        private final boolean useNumberTrend;
        private volatile LlmAdjustTaskStatus status = LlmAdjustTaskStatus.PENDING;
        private volatile Phase phase;
        private volatile String message;
        private volatile String fileName;
        private volatile Instant finishedAt;

        private RecommendationTask(boolean useNumberTrend) {
            this.useNumberTrend = useNumberTrend;
            phase = useNumberTrend ? Phase.ANALYZING_NUMBER_TREND : Phase.GENERATING_RECOMMENDATION;
        }

        private LlmAdjustTaskVo snapshot() {
            return LlmAdjustTaskVo.builder()
                    .taskId(taskId)
                    .status(status)
                    .useNumberTrend(useNumberTrend)
                    .phase(phase)
                    .message(message)
                    .fileName(fileName)
                    .createdAt(createdAt)
                    .finishedAt(finishedAt)
                    .build();
        }
    }
}
