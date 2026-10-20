package com.my.project.application.predict.impl;

import com.my.project.application.config.LotteryModelProperties;
import com.my.project.python.pool.PythonConnection;
import com.my.project.python.pool.PythonConnectionPool;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.IOException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InitPythonProcessServiceImplTest {
    @Test void repeatedInitReloadsModelsAndClosesPreviousPool() throws Exception {
        var first = mock(PythonConnectionPool.class); var second = mock(PythonConnectionPool.class);
        var service = spy(new InitPythonProcessServiceImpl(new LotteryModelProperties()));
        doReturn(first, second).when(service).createPool();
        service.init(); service.init();
        verify(first).shutdown();
        assertSame(second, ReflectionTestUtils.getField(service, "connectionPool"));
        service.shutdown(); verify(second).shutdown();
    }

    @Test void failedReloadDoesNotLoseTheExistingPool() throws Exception {
        var first = mock(PythonConnectionPool.class);
        var service = spy(new InitPythonProcessServiceImpl(new LotteryModelProperties()));
        doReturn(first).doThrow(new IOException("model load failed")).when(service).createPool();
        service.init();
        assertThrows(IOException.class, service::init);
        assertSame(first, ReflectionTestUtils.getField(service, "connectionPool"));
        verify(first, never()).shutdown(); service.shutdown();
    }

    @Test void retriesSameInputOnceAndReturnsOriginalResult() throws Exception {
        var pool = mock(PythonConnectionPool.class);
        var bad = mock(PythonConnection.class); var good = mock(PythonConnection.class);
        var service = new InitPythonProcessServiceImpl(new LotteryModelProperties());
        ReflectionTestUtils.setField(service, "connectionPool", pool);
        var request = Map.<String, Object>of("type", "predict", "red_balls", java.util.List.of(1, 2, 3, 4, 5, 6));
        when(pool.borrowConnection()).thenReturn(bad, good);
        when(bad.execute(request)).thenThrow(new IOException("broken pipe"));
        when(good.execute(request)).thenReturn("original JSON");
        assertEquals("original JSON", service.runInference(request));
        verify(bad).execute(same(request)); verify(good).execute(same(request));
        verify(pool).returnConnection(bad); verify(pool).returnConnection(good);
    }
    @Test void persistentFailureThrowsInsteadOfNullAndStopsAtTwoAttempts() throws Exception {
        var pool = mock(PythonConnectionPool.class);
        var service = new InitPythonProcessServiceImpl(new LotteryModelProperties());
        ReflectionTestUtils.setField(service, "connectionPool", pool);
        when(pool.borrowConnection()).thenThrow(new IOException("init failed"));
        assertThrows(IllegalStateException.class, () -> service.runInference(Map.of()));
        verify(pool, times(2)).borrowConnection();
    }
    @Test void interruptionIsPreservedAndNotRetried() throws Exception {
        var pool = mock(PythonConnectionPool.class);
        var service = new InitPythonProcessServiceImpl(new LotteryModelProperties());
        ReflectionTestUtils.setField(service, "connectionPool", pool);
        when(pool.borrowConnection()).thenThrow(new InterruptedException());
        try {
            assertThrows(IllegalStateException.class, () -> service.runInference(Map.of()));
            assertTrue(Thread.currentThread().isInterrupted()); verify(pool).borrowConnection();
        } finally { Thread.interrupted(); }
    }
}
