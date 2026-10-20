package com.my.project.application.feature;

import com.my.project.application.learning.DrawLearningService;

import com.my.project.application.feature.dto.FeatureTrendForecast.Run;
import com.my.project.application.feature.support.ForecastTargetPeriod;
import com.my.project.application.feature.support.FeatureTrendPreparation;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.history.vo.FeatureStatsVo;
import com.my.project.application.support.analyse.ColdHotAnalysisUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.infrastructure.persistence.repository.FeatureTrendForecastRepository;
import com.my.project.llm.bo.FeatureTrendInference;
import com.my.project.llm.bo.FeatureTrendInput;
import com.my.project.llm.bo.FeatureTrendKind;
import com.my.project.llm.bo.ZoneTrendImage;
import com.my.project.llm.service.ZoneTrendModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeatureTrendForecastServiceTest {
    private static final String SOURCE = "2026132";
    private static final String TARGET = "2026133";
    private final HistoryRecordService history = mock(HistoryRecordService.class);
    private final ForecastTargetPeriod target = mock(ForecastTargetPeriod.class);
    private final FeatureTrendForecastRepository repository = mock(FeatureTrendForecastRepository.class);
    private final ZoneTrendModelService model = mock(ZoneTrendModelService.class);
    private final DrawLearningService learning = mock(DrawLearningService.class);
    private final FeatureTrendForecastService service = new FeatureTrendForecastService(new FeatureTrendPreparation(history, target), repository, model, learning);
    private final List<String> periods = IntStream.rangeClosed(103, 132)
        .mapToObj(period -> "2026" + period).toList();

    @BeforeEach
    void sample() {
        var historyRows = IntStream.rangeClosed(73, 132).boxed().sorted(java.util.Comparator.reverseOrder())
            .map(period -> HistoryRecord.builder().period("2026" + (period < 100 ? "0" : "") + period)
                .num1(1).num2(6).num3(12).num4(16).num5(25).num6(33).special(8).build()).toList();
        when(history.getRecordsEndingAt(null, 60)).thenReturn(historyRows);
        when(history.getRecordsEndingAt(SOURCE, 60)).thenReturn(historyRows);
        when(target.after(any())).thenReturn(TARGET);
        var deltas = IntStream.range(0, 30).mapToObj(index -> index == 0 ? null : index - 15).toList();
        var primes = IntStream.range(0, 30).map(index -> 2).boxed().toList();
        var tails = IntStream.range(0, 10).mapToObj(digit -> primes).toList();
        when(history.analyzeFeatureStats(30, SOURCE)).thenReturn(FeatureStatsVo.builder()
            .periods(periods).primeCounts(primes).sumDeltaValues(deltas).tailCounts(tails).build());
    }

    @Test void preparesAllRequestedChartGroupsInOneAlignedWindow() {
        var plan = service.prepare();
        assertEquals(TARGET, plan.targetPeriod());
        assertEquals(ColdHotAnalysisUtils.calculate(history.getRecordsEndingAt(null, 60)), plan.coldHotAnalysis());
        assertEquals(plan.coldHotAnalysis(), service.prepare(SOURCE).coldHotAnalysis());
        var expected = Map.ofEntries(
            Map.entry("redZones", List.of(3, 28)), Map.entry("redTemperature", List.of(3, 3, 3, 3)),
            Map.entry("redParity", List.of(2, 7)), Map.entry("redRoad", List.of(3, 28)),
            Map.entry("redSum", List.of(1, 1)), Map.entry("redSpan", List.of(1)),
            Map.entry("redSize", List.of(2)), Map.entry("redPrime", List.of(7)),
            Map.entry("redHead", List.of(3)), Map.entry("redTail", List.of(3)),
            Map.entry("redEndings", List.of(10)), Map.entry("blueTemperature", List.of(3)),
            Map.entry("blueParity", List.of(2)), Map.entry("blueRoad", List.of(3)),
            Map.entry("blueSize", List.of(2)), Map.entry("blueInterval", List.of(4)));
        assertEquals(expected.size(), plan.items().size());
        for (var item : plan.items()) {
            assertEquals(periods, item.input().periods());
            assertEquals(expected.get(item.code()), item.input().groups().stream().map(group -> group.series().size()).toList());
        }
        var sum = plan.items().stream().filter(item -> item.code().equals("redSum")).findFirst().orElseThrow().input();
        assertNull(sum.groups().get(1).series().getFirst().values().getFirst());
        assertTrue(sum.groups().get(1).min() < 0);
        verifyNoInteractions(model);
    }

    @Test void rejectsMismatchedImagesThenStoresValidatedValuesAndReusesTheSavedResult() throws Exception {
        var input = service.prepare().items().stream().filter(item -> item.code().equals("blueParity"))
            .findFirst().orElseThrow().input();
        var saved = new AtomicReference<FeatureTrendForecastRepository.Detail>();
        when(repository.findSummary(SOURCE, "blueParity"))
            .thenAnswer(call -> Optional.ofNullable(saved.get()).map(FeatureTrendForecastRepository.Detail::summary));
        when(repository.insertIfAbsent(any())).thenAnswer(call -> {
            saved.set(call.getArgument(0));
            return saved.get().summary();
        });
        when(repository.findDetail(TARGET, "blueParity")).thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(model.modelName()).thenReturn("vision-test");

        assertThrows(IllegalArgumentException.class,
            () -> service.analyze(new Run("blueParity", input, List.of())));
        verifyNoInteractions(model);
        try (var png = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(1200, 580, BufferedImage.TYPE_INT_RGB), "png", png);
            var images = List.of(new ZoneTrendImage("oddEven-1", "data:image/png;base64," +
                Base64.getEncoder().encodeToString(png.toByteArray())));
            var inference = new FeatureTrendInference("走势观察", List.of(
                new FeatureTrendInference.Candidate("偶", "局部摆动", "可能转向", List.of(SOURCE))), "反例");
            when(model.analyzeFeature(FeatureTrendKind.BLUE_PARITY, input, images)).thenReturn(inference);
            var command = new Run("blueParity", input, images);

            var summary = service.analyze(command);
            assertEquals(TARGET, summary.targetPeriod());
            assertEquals(List.of("偶"), summary.values());
            assertEquals(summary, service.analyze(command));
            verify(model).analyzeFeature(FeatureTrendKind.BLUE_PARITY, input, images);
            var detail = service.findDetail(TARGET, "blueParity").orElseThrow();
            assertEquals(inference, detail.evidence().inference());
            assertEquals(input, detail.evidence().input());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentRequestsShareTheDatabaseLookupAndModelOutcome(boolean modelFails) throws Exception {
        var kind = FeatureTrendKind.BLUE_PARITY;
        var input = new FeatureTrendInput(SOURCE, periods, List.of());
        var command = new Run(kind.code(), input, List.of());
        var preparation = mock(FeatureTrendPreparation.class);
        var forecast = new FeatureTrendForecastService(preparation, repository, model, learning);
        var modelEntered = new CountDownLatch(1);
        var releaseModel = new CountDownLatch(1);
        var providerFailure = new IllegalStateException("视觉模型请求失败");
        var inference = new FeatureTrendInference("走势观察", List.of(), "反例");
        when(repository.findSummary(SOURCE, kind.code())).thenReturn(Optional.empty());
        when(preparation.prepare(command, kind, SOURCE))
            .thenReturn(new FeatureTrendPreparation.Prepared(input, List.of(), TARGET));
        when(repository.insertIfAbsent(any())).thenAnswer(call ->
            call.<FeatureTrendForecastRepository.Detail>getArgument(0).summary());
        when(model.analyzeFeature(kind, input, command.images())).thenAnswer(call -> {
            modelEntered.countDown();
            assertTrue(releaseModel.await(5, TimeUnit.SECONDS), "测试未放行模型调用");
            if (modelFails) throw providerFailure;
            return inference;
        }).thenReturn(inference);

        var first = new FutureTask<>(() -> forecast.analyze(command));
        var second = new FutureTask<>(() -> forecast.analyze(command));
        Thread.ofPlatform().daemon().start(first);
        try {
            assertTrue(modelEntered.await(5, TimeUnit.SECONDS), "首个请求未进入模型调用");
            Thread follower = Thread.ofPlatform().daemon().start(second);
            waitUntilBlocked(follower);
            assertFalse(second.isDone(), "重复请求应等待首个请求");
            verify(repository).findSummary(SOURCE, kind.code());
            verify(model).analyzeFeature(kind, input, command.images());
        } finally {
            releaseModel.countDown();
        }

        if (modelFails) {
            assertSame(providerFailure, assertThrows(ExecutionException.class,
                () -> first.get(5, TimeUnit.SECONDS)).getCause());
            assertSame(providerFailure, assertThrows(ExecutionException.class,
                () -> second.get(5, TimeUnit.SECONDS)).getCause());
            verify(repository, never()).insertIfAbsent(any());
            assertEquals(TARGET, forecast.analyze(command).targetPeriod());
            verify(model, times(2)).analyzeFeature(kind, input, command.images());
            return;
        }
        assertEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        verify(model).analyzeFeature(kind, input, command.images());
        verify(repository).insertIfAbsent(any());
    }

    private static void waitUntilBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.isAlive() && thread.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertEquals(Thread.State.WAITING, thread.getState(), "重复请求未进入等待");
    }
}
