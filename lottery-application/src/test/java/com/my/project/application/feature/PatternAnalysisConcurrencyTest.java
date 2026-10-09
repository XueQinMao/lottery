package com.my.project.application.feature;

import com.my.project.application.feature.support.PatternFeatureAnalysisSupport;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.llm.bo.PatternFeatureAnalysisBo;
import com.my.project.llm.bo.PatternFeatureAnalysisReqBo;
import com.my.project.llm.service.LotteryAnalysisService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PatternAnalysisConcurrencyTest {
    @Test
    void serializesFeatureCallsAcrossConcurrentReportsAndPreservesTheirOrder() throws Exception {
        var model = mock(LotteryAnalysisService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var overlappingCall = new CountDownLatch(1);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        when(model.analyzePattern(any())).thenAnswer(call -> {
            int concurrency = active.incrementAndGet();
            maximum.accumulateAndGet(concurrency, Math::max);
            if (concurrency > 1) overlappingCall.countDown();
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
                PatternFeatureAnalysisReqBo request = call.getArgument(0);
                return PatternFeatureAnalysisBo.builder().code(request.getCode()).status("OK").build();
            } finally {
                active.decrementAndGet();
            }
        });
        var records = IntStream.range(0, 60).mapToObj(index -> HistoryRecord.builder()
            .period("P" + index).num1(1).num2(2).num3(3).num4(4).num5(5).num6(6).special(1).build()).toList();
        try (var support = new PatternFeatureAnalysisSupport(model);
             var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = callers.submit(() -> support.analyze(records));
            var second = callers.submit(() -> support.analyze(records));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertFalse(overlappingCall.await(200, TimeUnit.MILLISECONDS), "reports must share one LLM worker");
            } finally {
                release.countDown();
            }
            var firstReport = first.get(5, TimeUnit.SECONDS);
            var secondReport = second.get(5, TimeUnit.SECONDS);
            assertEquals(support.prepare(records).stream().map(PatternFeatureAnalysisReqBo::getCode).toList(),
                firstReport.stream().map(PatternFeatureAnalysisBo::getCode).toList());
            assertEquals(firstReport, secondReport);
            assertEquals(1, maximum.get());
            verify(model, times(36)).analyzePattern(any());
        }
    }
}
