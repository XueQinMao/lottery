package com.my.project.application.predict;

import com.alibaba.fastjson.JSONObject;
import com.my.project.application.config.LotteryModelProperties;
import com.my.project.application.event.PredictCompleteEvent;
import com.my.project.application.event.listener.PredictCompleteEventListener;
import com.my.project.application.selection.SmartSelectService;
import com.my.project.application.selection.model.SsqCombinationBo;
import com.my.project.application.selection.model.WeightConfigBo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PredictionPersistenceRegressionTest {
    @TempDir Path directory;

    @Test void preservesInclusiveThresholdsNullConfigAndInvalidRange() {
        var listener = new PredictCompleteEventListener(null, null, null, null);
        var config = new WeightConfigBo();
        config.setProbabilityMin(.1); config.setProbabilityMax(.9);
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(listener, "shouldPersist", new BigDecimal("0.1"), config));
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(listener, "shouldPersist", new BigDecimal("0.9"), config));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(listener, "shouldPersist", new BigDecimal("0.0999"), config));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(listener, "shouldPersist", null, config));
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(listener, "shouldPersist", new BigDecimal("0.5"), null));
        config.setProbabilityMin(.9); config.setProbabilityMax(.1);
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(listener, "shouldPersist", new BigDecimal("0.5"), config));
    }

    @Test void realModelBaselineAndFixedResponsesKeepSamePersistenceDecisions() throws Exception {
        Path comparison = Path.of(System.getProperty("prediction.comparison.dir", "../lottery-python/target/stability/comparison"));
        assumeTrue(Files.exists(comparison.resolve("comparison.json")), "运行真实模型compare后再执行此集成验证");
        var baseline = Files.readAllLines(comparison.resolve("baseline.jsonl")).stream().filter(l -> l.contains("\"index\""))
                .map(JSONObject::parseObject).toList();
        var fixed = Files.readAllLines(comparison.resolve("fixed.jsonl")).stream().filter(l -> l.contains("\"index\""))
                .map(JSONObject::parseObject).toList();
        assertEquals(64, baseline.size()); assertEquals(baseline.size(), fixed.size());
        var selection = mock(SmartSelectService.class); var cache = mock(PredictCacheService.class);
        var progress = mock(PredictLogService.class); var properties = new LotteryModelProperties();
        properties.setPath(directory.toString());
        when(cache.getCacheKey(any(), anyList(), anyInt())).thenReturn("fixed-key");
        var listener = new PredictCompleteEventListener(selection, properties, progress, cache);
        int checked = 0;
        for (double[] range : new double[][]{{0, 1}, {.05, .95}, {.1, .9}, {.2, .8}, {.9, .1}}) {
            var config = new WeightConfigBo(); config.setProbabilityMin(range[0]); config.setProbabilityMax(range[1]);
            when(selection.getWeightConfig()).thenReturn(config);
            for (int i = 0; i < baseline.size(); i++) {
                JSONObject before = baseline.get(i).getJSONObject("response"), after = fixed.get(i).getJSONObject("response");
                assertEquals(before.getString("reason"), after.getString("reason"));
                Boolean expected = ReflectionTestUtils.invokeMethod(listener, "shouldPersist", before.getBigDecimal("probability"), config);
                Boolean actual = ReflectionTestUtils.invokeMethod(listener, "shouldPersist", after.getBigDecimal("probability"), config);
                assertEquals(expected, actual, "case=" + i + " min=" + range[0]);
                clearInvocations(cache);
                var event = PredictCompleteEvent.of(this, SsqCombinationBo.of(List.of(3, 11, 13, 14, 26, 27), 10),
                        after.toJSONString(), LocalDate.of(2026, 10, 20), (long) ++checked);
                listener.handleEvent(event);
                verify(cache, times(Boolean.TRUE.equals(expected) ? 1 : 0)).addCache(any(), any(), any());
            }
        }
        verify(progress, times(320)).addOrUpdate(any(), anyLong());
    }
}
