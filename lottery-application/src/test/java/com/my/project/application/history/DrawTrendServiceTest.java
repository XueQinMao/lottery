package com.my.project.application.history;

import com.my.project.application.history.impl.HistoryRecordServiceImpl;
import com.my.project.application.history.client.SsqDrawHistoryClient;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.infrastructure.persistence.repository.HistoryRecordRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrawTrendServiceTest {
    private final HistoryRecordServiceImpl service = spy(new HistoryRecordServiceImpl(mock(HistoryRecordRepository.class), mock(SsqDrawHistoryClient.class)));

    @Test
    void fetchesExtraPriorDrawsAndHonorsTheTrimmedInclusiveCutoff() {
        var record = HistoryRecord.builder().period("2026001").num1(1).num2(2).num3(3).num4(4).num5(5).num6(6).special(1).build();
        doReturn(List.of(record)).when(service).getRecordsEndingAt("2026001", 60);
        assertEquals(List.of("2026001"), service.analyzeDrawTrends(30, " 2026001 ").periods());
        verify(service).getRecordsEndingAt("2026001", 60);
    }

    @Test
    void rejectsInvalidQueriesBeforeReadingHistory() {
        assertThrows(IllegalArgumentException.class, () -> service.analyzeDrawTrends(0, null));
        assertThrows(IllegalArgumentException.class, () -> service.analyzeDrawTrends(101, null));
        assertThrows(IllegalArgumentException.class, () -> service.analyzeDrawTrends(Integer.MAX_VALUE, null));
        assertThrows(IllegalArgumentException.class, () -> service.analyzeDrawTrends(30, "abc"));
        verify(service, never()).getRecordsEndingAt(any(), anyInt());
    }

    @Test
    void returnsAnEmptyTrendWhenNoRecordsAreAvailable() {
        doReturn(List.of()).when(service).getRecordsEndingAt(null, 130);
        assertTrue(service.analyzeDrawTrends(100, null).periods().isEmpty());
    }
}
