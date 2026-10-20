package com.my.project.python.pool;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.io.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class PythonConnectionTest {
    private Process child(String mode) throws IOException {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), Server.class.getName(), mode).start();
    }
    private PythonConnection connect(Process child) throws IOException {
        return new PythonConnection(child, Duration.ofSeconds(10), Duration.ofMillis(200));
    }

    @Test void normalJsonIsUnchangedAndCloseKillsProcess() throws Exception {
        Process child = child("normal");
        try (PythonConnection connection = connect(child)) {
            assertEquals("{\"probability\":0.123456789,\"reason\":\"original\"}", connection.execute(Map.of("type", "predict")));
            assertTrue(connection.isHealthy());
        }
        assertFalse(child.isAlive());
    }
    @Test void timeoutTerminatesBlockedReadAndRejectsReuse() throws Exception {
        Process child = child("hang");
        try (PythonConnection connection = connect(child)) {
            long start = System.nanoTime();
            assertThrows(IOException.class, () -> connection.execute(Map.of("type", "predict")));
            assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 5);
            assertFalse(child.isAlive());
            assertFalse(connection.isHealthy());
            assertThrows(IOException.class, () -> connection.execute(Map.of("type", "predict")));
        }
    }
    @Test void unexpectedExitAndProtocolErrorsInvalidateConnection() throws Exception {
        for (String mode : new String[]{"exit", "error", "malformed", "null"}) {
            Process child = child(mode);
            try (PythonConnection connection = connect(child)) {
                assertThrows(IOException.class, () -> connection.execute(Map.of("type", "predict")), mode);
                assertFalse(connection.isHealthy(), mode);
                assertFalse(child.isAlive(), mode);
            }
        }
    }
    @Test void startupTimeoutDoesNotLeakProcess() throws Exception {
        Process child = child("slow-init");
        assertThrows(IOException.class, () -> new PythonConnection(child, Duration.ofMillis(100), Duration.ofSeconds(1)));
        assertFalse(child.isAlive());
    }
    @Test void concurrentCloseUnblocksRunningRequest() throws Exception {
        Process child = child("hang");
        try (PythonConnection connection = new PythonConnection(child, Duration.ofSeconds(10), Duration.ofSeconds(10));
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> request = executor.submit(() -> assertThrows(IOException.class,
                    () -> connection.execute(Map.of("type", "predict"))));
            connection.close();
            request.get(3, TimeUnit.SECONDS);
            assertFalse(child.isAlive());
        }
    }

    @Test void concurrentRequestsCannotConsumeEachOthersResponses() throws Exception {
        Process child = child("echo");
        try (PythonConnection connection = connect(child); var executor = Executors.newFixedThreadPool(4)) {
            var requests = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 30; i++) {
                int id = i;
                requests.add(executor.submit(() -> {
                    try { assertEquals(id, JSONObject.parseObject(connection.execute(Map.of("id", id))).getIntValue("id")); }
                    catch (IOException e) { throw new RuntimeException(e); }
                }));
            }
            for (var request : requests) request.get(5, TimeUnit.SECONDS);
        }
    }

    public static class Server {
        public static void main(String[] args) throws Exception {
            String mode = args[0];
            if (mode.equals("slow-init")) Thread.sleep(30_000);
            System.out.println("{\"type\":\"init_complete\"}");
            System.out.flush();
            var reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = reader.readLine()) != null) {
                if ("init_check".equals(JSONObject.parseObject(line).getString("type")))
                    System.out.println("{\"status\":\"initialized\"}");
                else {
                    switch (mode) {
                        case "exit" -> { System.err.println("simulated native crash"); System.exit(7); }
                        case "hang" -> Thread.sleep(30_000);
                        case "error" -> System.out.println("{\"error\":\"failed\"}");
                        case "malformed" -> System.out.println("not-json");
                        case "null" -> System.out.println("null");
                        case "echo" -> System.out.println(line);
                        default -> System.out.println("{\"probability\":0.123456789,\"reason\":\"original\"}");
                    }
                }
                System.out.flush();
            }
        }
    }
}
