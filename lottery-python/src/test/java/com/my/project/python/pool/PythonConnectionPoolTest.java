package com.my.project.python.pool;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PythonConnectionPoolTest {
    private PythonConnection healthy() {
        var c = mock(PythonConnection.class);
        when(c.isHealthy()).thenReturn(true);
        return c;
    }
    @Test void failedRebuildRetainsSlotAndNextBorrowRecovers() throws Exception {
        var original = healthy(); var replacement = healthy(); var calls = new AtomicInteger();
        var pool = new PythonConnectionPool(1, () -> {
            int call = calls.incrementAndGet();
            if (call == 2) throw new IOException("rebuild failed");
            return call == 1 ? original : replacement;
        }, Duration.ofMillis(100));
        try {
            assertSame(original, pool.borrowConnection());
            when(original.isHealthy()).thenReturn(false);
            pool.returnConnection(original);
            assertThrows(IOException.class, pool::borrowConnection);
            assertEquals(1, pool.getSize());
            assertSame(replacement, pool.borrowConnection());
            pool.returnConnection(replacement);
            pool.returnConnection(replacement);
            assertEquals(1, pool.getSize());
        } finally { pool.shutdown(); }
        verify(original, atLeastOnce()).close(); verify(replacement).close();
    }
    @Test void borrowingIsBoundedAndShutdownClosesBorrowedConnections() throws Exception {
        var connection = healthy();
        var pool = new PythonConnectionPool(1, () -> connection, Duration.ofMillis(50));
        assertSame(connection, pool.borrowConnection());
        assertThrows(IOException.class, pool::borrowConnection);
        pool.shutdown(); pool.shutdown();
        verify(connection).close();
        assertThrows(IOException.class, pool::borrowConnection);
    }
    @Test void partialStartupFailureClosesAlreadyCreatedConnections() {
        var connection = healthy(); var count = new AtomicInteger();
        assertThrows(IOException.class, () -> new PythonConnectionPool(2, () -> {
            if (count.incrementAndGet() == 2) throw new IOException("init failed");
            return connection;
        }, Duration.ofSeconds(1)));
        verify(connection).close();
    }
    @Test void shutdownDuringRebuildClosesLateReplacement() throws Exception {
        var original = healthy(); var replacement = healthy(); var count = new AtomicInteger();
        var rebuilding = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pool = new PythonConnectionPool(1, () -> {
            if (count.incrementAndGet() == 1) return original;
            rebuilding.countDown();
            try { if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("test latch timeout"); }
            catch (InterruptedException e) { throw new IOException(e); }
            return replacement;
        }, Duration.ofSeconds(1));
        pool.borrowConnection(); when(original.isHealthy()).thenReturn(false); pool.returnConnection(original);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> borrow = executor.submit(() -> assertThrows(IOException.class, pool::borrowConnection));
            assertTrue(rebuilding.await(3, TimeUnit.SECONDS));
            pool.shutdown(); release.countDown(); borrow.get(3, TimeUnit.SECONDS);
            verify(replacement).close();
        } finally { release.countDown(); pool.shutdown(); }
    }
}
