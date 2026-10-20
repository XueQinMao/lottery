package com.my.project.application.history;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.my.project.application.history.impl.HistoryRecordServiceImpl;
import com.my.project.application.history.client.SsqDrawHistoryClient;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.infrastructure.persistence.repository.HistoryRecordRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NumberTrendServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void seedsOnlyEarlierHistoryAndKeepsTheInclusiveCutoff() {
        var repository = mock(HistoryRecordRepository.class);
        var service = spy(new HistoryRecordServiceImpl(repository, mock(SsqDrawHistoryClient.class)));
        LambdaQueryChainWrapper<HistoryRecord> query = mock(LambdaQueryChainWrapper.class);
        when(repository.lambdaQuery()).thenReturn(query);
        LocalDate oldestVisible = LocalDate.of(2026, 1, 3);
        when(query.lt(any(), eq(oldestVisible))).thenReturn(query);
        when(query.orderByAsc(any(com.baomidou.mybatisplus.core.toolkit.support.SFunction.class))).thenReturn(query);
        var prior = record("2026001", oldestVisible.minusDays(2), 16);
        var olderVisible = record("2026002", oldestVisible, 1);
        var newestVisible = record("2026003", oldestVisible.plusDays(2), 1);
        when(query.list()).thenReturn(List.of(prior));
        doReturn(List.of(newestVisible, olderVisible)).when(service).getRecordsEndingAt("2026003", 2);
        var result = service.analyzeNumberTrends(2, " 2026003 ");
        assertEquals(List.of("2026002", "2026003"), result.rows().stream().map(r -> r.period()).toList());
        assertEquals(2, result.rows().getLast().blueOmissions().getLast());
        verify(query).lt(any(), eq(oldestVisible));
        verify(service).getRecordsEndingAt("2026003", 2);
    }

    @Test
    void validatesQueriesAndHandlesAnEmptyDatabaseWithoutAnExtraHistoryQuery() {
        var repository = mock(HistoryRecordRepository.class);
        var service = spy(new HistoryRecordServiceImpl(repository, mock(SsqDrawHistoryClient.class)));
        assertThrows(IllegalArgumentException.class, () -> service.analyzeNumberTrends(101, null));
        assertThrows(IllegalArgumentException.class, () -> service.analyzeNumberTrends(30, "bad"));
        doReturn(List.of()).when(service).getRecordsEndingAt(null, 30);
        assertTrue(service.analyzeNumberTrends(30, null).rows().isEmpty());
        verifyNoInteractions(repository);
    }

    private static HistoryRecord record(String period, LocalDate date, int blue) {
        return HistoryRecord.builder().period(period).openDate(date).num1(1).num2(2).num3(3).num4(4).num5(5).num6(6).special(blue).build();
    }
}
