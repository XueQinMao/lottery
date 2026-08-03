package com.my.project.python.pool;

import com.alibaba.fastjson.JSONObject;
import com.my.project.python.support.PythonUtils;
import com.my.project.python.support.ResourcePathUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * PythonConnection
 *
 * @author 刘强
 * @version 2025/10/28 19:31
 **/
public class PythonConnection {

    private static final Logger logger = LoggerFactory.getLogger(PythonConnection.class);

    private final Process process;
    private final BufferedWriter writer;
    private final BufferedReader reader;

    public PythonConnection(String scriptName, String modelDir) throws IOException {
        Instant start = Instant.now();
        Triple<Process, BufferedWriter, BufferedReader> pythonObject = PythonUtils.startPythonProcess(buildCommand(scriptName, modelDir));
        process = pythonObject.getLeft();
        writer = pythonObject.getMiddle();
        reader = pythonObject.getRight();

        Supplier<Void> checkProcess = () -> {
            try {
                // 1. 验证Python进程是否正常
                writer.write(JSONObject.toJSONString(Map.of("type", "init_check")));
                writer.newLine();
                writer.flush();
                String checkResp = reader.readLine();
                JSONObject check = JSONObject.parseObject(checkResp);
                Assert.isTrue("initialized".equals(check.getString("status")), "Python进程初始化检查异常失败" + checkResp);
            } catch (Exception e) {
                throw new RuntimeException("Python进程初始化检查异常", e);
            }
            return null;
        };
        initializePythonProcess(checkProcess);
        logger.info("✓ Python初始化耗时{}s", ChronoUnit.SECONDS.between(start, Instant.now()));
    }

    public synchronized String execute(Map<String, Object> params) throws IOException {
        String requestJson = JSONObject.toJSONString(params);
        writer.write(requestJson + "\n");
        writer.flush();

        String responseLine = reader.readLine();
        if (responseLine == null) {
            throw new IOException("Python服务连接已断开");
        }
        return responseLine;
    }

    public void close() {
        try {
            writer.close();
            reader.close();
        } catch (IOException ignored) {}

        if (process != null && process.isAlive()) {
            process.destroy(); // 先尝试软终止
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly(); // 强制杀掉
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String buildCommand(String scriptName, String modelDir) {
        StringBuilder command = new StringBuilder();
        command.append("python ").append(ResourcePathUtils.getPythonScriptPath(scriptName));

        // 如果指定了模型目录，添加 --model-dir 参数
        command.append(" --model-dir \"").append(modelDir.trim()).append("\"");
        return command.toString();
    }

    private void initializePythonProcess(Supplier<Void> checkProcess) throws IOException {
        // 等待初始化完成信号
        String initSignal = reader.readLine(); // 30秒超时
        Assert.hasText(initSignal, "python 初始化失败，疑似超时");

        JSONObject signal = JSONObject.parseObject(initSignal);
        if (!"init_complete".equals(signal.getString("type"))) {
            throw new IOException("Python进程初始化失败: " + initSignal);
        }
        logger.info("✓ Python初始化完成，加载{}个模型", signal.getInteger("models_loaded"));
        checkProcess.get();
    }
}
