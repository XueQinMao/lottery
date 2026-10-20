package com.my.project.python.pool;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** 固定逻辑槽位；坏连接下次借用受控重建，重建失败不会丢失池容量。 */
public class PythonConnectionPool {
    @FunctionalInterface
    public interface ConnectionFactory { PythonConnection create() throws IOException; }
    private static final class Slot { PythonConnection connection; }
    private final BlockingQueue<Slot> available;
    private final List<Slot> slots = new ArrayList<>();
    private final Map<PythonConnection, Slot> borrowed = new IdentityHashMap<>();
    private final ConnectionFactory factory;
    private final Duration borrowTimeout;
    private volatile boolean closed;

    public PythonConnectionPool(int size, String scriptName, String modelDir) throws IOException {
        this(size, () -> new PythonConnection(scriptName, modelDir),
                Duration.ofMillis(Long.getLong("lottery.python.borrow-timeout-ms", 120_000L)));
    }

    public PythonConnectionPool(int size, ConnectionFactory factory, Duration borrowTimeout) throws IOException {
        if (size <= 0) throw new IllegalArgumentException("连接数必须为正");
        this.factory = factory;
        this.borrowTimeout = borrowTimeout;
        available = new ArrayBlockingQueue<>(size);
        try {
            for (int i = 0; i < size; i++) {
                Slot slot = new Slot(); slots.add(slot);
                slot.connection = factory.create(); available.add(slot);
            }
        } catch (IOException | RuntimeException e) { shutdown(); throw e; }
    }

    public PythonConnection borrowConnection() throws InterruptedException, IOException {
        long deadline = System.nanoTime() + borrowTimeout.toNanos();
        Slot slot = null;
        while (slot == null) {
            if (closed) throw new IOException("Python连接池已关闭");
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IOException("等待Python连接超时: " + borrowTimeout);
            slot = available.poll(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS);
        }
        try {
            if (slot.connection == null || !slot.connection.isHealthy()) {
                if (slot.connection != null) slot.connection.close();
                PythonConnection replacement = factory.create();
                synchronized (this) {
                    if (closed) { replacement.close(); throw new IOException("Python连接池已关闭"); }
                    slot.connection = replacement;
                }
            }
            synchronized (this) {
                if (closed) throw new IOException("Python连接池已关闭");
                borrowed.put(slot.connection, slot);
                return slot.connection;
            }
        } catch (IOException | RuntimeException e) {
            synchronized (this) { if (!closed) available.offer(slot); }
            throw e;
        }
    }

    public synchronized Integer getSize() { return available.size(); }

    public synchronized void returnConnection(PythonConnection connection) {
        Slot slot = borrowed.remove(connection);
        if (slot == null) return;
        if (!connection.isHealthy()) { connection.close(); slot.connection = null; }
        if (!closed) available.offer(slot);
        else connection.close();
    }

    public void shutdown() {
        List<PythonConnection> connections;
        synchronized (this) {
            if (closed) return;
            closed = true; available.clear();
            connections = slots.stream().map(s -> s.connection).filter(Objects::nonNull).toList();
            borrowed.clear();
        }
        connections.forEach(PythonConnection::close);
    }
}
