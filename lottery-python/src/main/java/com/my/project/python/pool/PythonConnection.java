package com.my.project.python.pool;

import com.my.project.python.support.ResourcePathUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 一次借用一个请求；超时终止实际进程，迟到响应不能进入后续请求。 */
public class PythonConnection implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(PythonConnection.class);
    private final Process process;
    private final PythonLineProtocol protocol;
    private final Duration requestTimeout;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ArrayDeque<String> stderrTail = new ArrayDeque<>();
    private final ExecutorService io;

    public PythonConnection(String scriptName, String modelDir) throws IOException {
        this(start(scriptName, modelDir), Duration.ofMillis(Long.getLong("lottery.python.init-timeout-ms", 300_000L)),
                Duration.ofMillis(Long.getLong("lottery.python.request-timeout-ms", 120_000L)));
    }

    PythonConnection(Process process, Duration initTimeout, Duration requestTimeout) throws IOException {
        this.process = process;
        this.requestTimeout = requestTimeout;
        var writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        protocol = new PythonLineProtocol(reader, writer);
        io = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("python-io-" + process.pid()).factory());
        Thread.ofPlatform().daemon().name("python-stderr-" + process.pid()).start(this::consumeStderr);
        process.onExit().thenAccept(p -> {
            if (closed.get()) log.info("Python进程退出 pid={} exit={} stderr={}", p.pid(), p.exitValue(), stderr());
            else log.error("Python进程异常退出 pid={} exit={} stderr={}", p.pid(), p.exitValue(), stderr());
        });
        try {
            timed(() -> {
                protocol.handshake();
                return null;
            }, initTimeout);
            log.info("Python连接就绪 pid={}", process.pid());
        } catch (IOException e) { close(); throw e; }
    }

    private static Process start(String scriptName, String modelDir) throws IOException {
        // 直接管理Python而非cmd/bash包装进程，保证超时回收的是实际工作进程。
        ProcessBuilder builder = new ProcessBuilder(List.of("python", "-u",
                ResourcePathUtils.getPythonScriptPath(scriptName), "--model-dir", modelDir.trim()));
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        builder.environment().put("PYTHONUNBUFFERED", "1");
        builder.environment().put("PYTHONFAULTHANDLER", "1");
        builder.environment().put("TF_CPP_MIN_LOG_LEVEL", "3");
        return builder.start();
    }

    public synchronized String execute(Map<String, Object> request) throws IOException {
        if (!isHealthy()) throw new IOException("Python连接不可用 pid=" + process.pid() + " stderr=" + stderr());
        try {
            return timed(() -> protocol.request(request), requestTimeout);
        } catch (IOException e) { close(); throw e; }
    }

    private <T> T timed(Callable<T> task, Duration timeout) throws IOException {
        Future<T> future;
        try { future = io.submit(task); }
        catch (RejectedExecutionException e) { throw new IOException("Python连接已关闭", e); }
        try { return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) {
            future.cancel(true); close(); Thread.currentThread().interrupt();
            throw new IOException("Python通信被中断", e);
        } catch (TimeoutException e) {
            future.cancel(true); close();
            throw new IOException("Python通信超时 pid=" + process.pid() + " timeout=" + timeout + " stderr=" + stderr(), e);
        } catch (ExecutionException e) {
            throw new IOException("Python通信失败 pid=" + process.pid() + " stderr=" + stderr(), e.getCause());
        }
    }

    public boolean isHealthy() { return !closed.get() && process.isAlive(); }

    private String stderr() {
        synchronized (stderrTail) { return String.join(" | ", stderrTail); }
    }

    private void consumeStderr() {
        try (BufferedReader errors = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = errors.readLine()) != null) {
                synchronized (stderrTail) {
                    if (stderrTail.size() == 30) stderrTail.removeFirst();
                    stderrTail.addLast(StringUtils.left(line, 2048));
                }
                if (line.startsWith("[资源]")) log.info("Python pid={} {}", process.pid(), line);
                else log.debug("Python pid={} stderr: {}", process.pid(), line);
            }
        } catch (IOException e) { log.debug("Python stderr结束 pid={}: {}", process.pid(), e.getMessage()); }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        // 不先关闭持有readLine锁的BufferedReader；先杀进程解除读写阻塞。
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        io.shutdownNow();
        try { process.waitFor(2, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        try { process.getOutputStream().close(); } catch (IOException ignored) { }
        try { process.getInputStream().close(); } catch (IOException ignored) { }
        try { process.getErrorStream().close(); } catch (IOException ignored) { }
    }
}
