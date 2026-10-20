package com.my.project.application.predict.impl;

import com.my.project.application.config.LotteryModelProperties;
import com.my.project.application.event.PredictCompleteEvent;
import com.my.project.application.event.listener.PredictCompleteEventListener;
import com.my.project.application.predict.*;
import com.my.project.application.selection.SmartSelectService;
import com.my.project.application.selection.model.SsqCombinationBo;
import com.my.project.application.selection.model.WeightConfigBo;
import com.my.project.application.support.SpringContextUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(15)
class PredictServiceStabilityTest {
    @TempDir Path directory;
    private ApplicationContext previous;
    private ApplicationContext context;
    private final LocalDate date = LocalDate.of(2026, 10, 20);
    private SsqCombinationBo valid() { return SsqCombinationBo.of(List.of(3, 11, 13, 14, 26, 27), 10); }
    @BeforeEach void before() {
        previous = SpringContextUtils.getApplicationContext();
        context = mock(ApplicationContext.class);
        new SpringContextUtils().setApplicationContext(context);
    }
    @AfterEach void after() { new SpringContextUtils().setApplicationContext(previous); }
    private ThreadPoolExecutor executor() {
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(3),
                (task, pool) -> { if (pool.isShutdown()) throw new RejectedExecutionException(); task.run(); });
    }

    @Test void waitsForEventFileAndProgressBeforeReturning() throws Exception {
        var python = mock(InitPythonProcessService.class);
        when(python.runInference(anyMap())).thenReturn("{\"probability\":0.5,\"reason\":\"unchanged\"}");
        var logs = mock(PredictLogService.class); var cache = mock(PredictCacheService.class);
        var selection = mock(SmartSelectService.class);
        var weights = new WeightConfigBo();
        weights.setProbabilityMin(.1); weights.setProbabilityMax(.9);
        when(selection.getWeightConfig()).thenReturn(weights);
        var properties = new LotteryModelProperties(); properties.setPath(directory.toString());
        when(cache.getCacheKey(any(), anyList(), anyInt())).thenReturn("fixed-key");
        var listener = new PredictCompleteEventListener(selection, properties, logs, cache);
        var writing = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(inv -> { writing.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return null; })
                .when(logs).addOrUpdate(eq(date), anyLong());
        doAnswer(inv -> { listener.handleEvent(inv.getArgument(0)); return null; })
                .when(context).publishEvent(any(ApplicationEvent.class));
        try (var prediction = executor(); var caller = Executors.newSingleThreadExecutor()) {
            var service = new PredictServiceImpl(python, logs, prediction);
            var result = caller.submit(() -> service.runPredictions(date, 0, consumer -> consumer.accept(valid())));
            assertTrue(writing.await(3, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            release.countDown(); result.get(3, TimeUnit.SECONDS);
            assertTrue(Files.readString(directory.resolve(date + "_预测结果.txt")).contains("unchanged"));
            assertTrue(Files.exists(directory.resolve(date + "_cache_persistence.txt")));
            verify(logs).addOrUpdate(date, 1L);
        } finally { release.countDown(); }
    }

    @Test void nullAndErrorResultsNeverPublishSuccessOrAdvanceProgress() {
        for (String response : new String[]{null, "{\"error\":\"failure\"}", "null"}) {
            var python = mock(InitPythonProcessService.class);
            when(python.runInference(anyMap())).thenReturn(response);
            try (var executor = executor()) {
                var service = new PredictServiceImpl(python, mock(PredictLogService.class), executor);
                assertThrows(IllegalStateException.class, () -> service.runPredictions(date, 0,
                        consumer -> consumer.accept(valid())));
            }
        }
        verify(context, never()).publishEvent(any(ApplicationEvent.class));
    }

    @Test void preservesFiltersAndCompletesEveryAcceptedTaskUnderBackpressure() {
        var python = mock(InitPythonProcessService.class);
        when(python.runInference(anyMap())).thenReturn("{\"probability\":0.5,\"reason\":\"ok\"}");
        var success = new AtomicInteger();
        doAnswer(inv -> { success.incrementAndGet(); return null; }).when(context).publishEvent(any(ApplicationEvent.class));
        try (var executor = executor()) {
            var service = new PredictServiceImpl(python, mock(PredictLogService.class), executor);
            service.runPredictions(date, 0, consumer -> {
                consumer.accept(SsqCombinationBo.of(List.of(1, 2, 3, 4, 5, 6), 1)); // 和值不符
                consumer.accept(SsqCombinationBo.of(List.of(16, 17, 18, 19, 20, 21), 1)); // 跨度不符
                consumer.accept(SsqCombinationBo.of(List.of(3, 11, 13, 15, 27, 29), 1)); // 全奇
                for (int i = 0; i < 20; i++) consumer.accept(valid());
            });
        }
        assertEquals(20, success.get());
        verify(python, times(20)).runInference(argThat(request -> request.get("model").equals("second_fusion_model")
                && request.get("red_balls").equals(valid().getRedBalls()) && request.get("blue_ball").equals(10)));
    }

    @Test void persistenceFailureDoesNotAdvanceCounterOrContinueRecommendation() {
        var python = mock(InitPythonProcessService.class);
        when(python.runInference(anyMap())).thenReturn("{\"probability\":0.5}");
        doThrow(new IllegalStateException("disk failed")).when(context).publishEvent(any(ApplicationEvent.class));
        try (var executor = executor()) {
            var service = new PredictServiceImpl(python, mock(PredictLogService.class), executor);
            assertThrows(IllegalStateException.class, () -> service.runPredictions(date, 0, consumer -> consumer.accept(valid())));
        }
    }

    @Test void drainTimeoutStopsRecommendationAndRejectsLatePrediction() throws Exception {
        var python = mock(InitPythonProcessService.class);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(python.runInference(anyMap())).thenAnswer(inv -> {
            started.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            return "{\"probability\":0.5}";
        });
        try (var executor = executor()) {
            var service = new PredictServiceImpl(python, mock(PredictLogService.class), executor,
                    Duration.ofMillis(100));
            try {
                var exception = assertThrows(IllegalStateException.class, () -> service.runPredictions(date, 0,
                        consumer -> {
                            consumer.accept(valid());
                            try { assertTrue(started.await(3, TimeUnit.SECONDS)); }
                            catch (InterruptedException e) { throw new IllegalStateException(e); }
                        }));
                assertInstanceOf(TimeoutException.class, exception.getCause());
                verify(python).shutdown();
            } finally { release.countDown(); }
        }
        verify(context, never()).publishEvent(any(ApplicationEvent.class));
    }
}
