package com.my.project.python.support;

import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * PythonUtils
 *
 * @author 刘强
 * @version 2025/07/23 17:10
 **/
public class PythonUtils {

    private static Logger logger = LoggerFactory.getLogger(PythonUtils.class);



    public static void executeCommand(String command)
        throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();


        // 设置环境变量，解决中文乱码问题
        Map<String, String> env = processBuilder.environment();
        
        // 设置TensorFlow环境变量，消除oneDNN警告
        env.put("TF_ENABLE_ONEDNN_OPTS", "0");
        env.put("TF_CPP_MIN_LOG_LEVEL", "2");
        env.put("CUDA_VISIBLE_DEVICES", "-1");  // 禁用GPU，使用CPU
        
        // 设置Python环境变量
        env.put("PYTHONIOENCODING", "utf-8");
        env.put("PYTHONUNBUFFERED", "1");
        

        // 设置工作目录
        File workingDir = new File(System.getProperty("user.dir"));
        processBuilder.directory(workingDir);

        // 在Windows上使用cmd执行
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            processBuilder.command("cmd", "/c", command);
        } else {
            processBuilder.command("bash", "-c", command);
        }

        processBuilder.redirectErrorStream(true);
        // 6. 启动进程
        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("[训练] {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode == 0) {
            logger.info("✓ 模型训练完成");
        } else {
            logger.error("[警告] 训练返回非零退出码: {}", exitCode);
        }
    }

    public static Triple<Process, BufferedWriter, BufferedReader> startPythonProcess(String command) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        
        // 设置环境变量，解决中文乱码问题
        Map<String, String> env = processBuilder.environment();
        env.put("PYTHONIOENCODING", "utf-8");
        env.put("PYTHONUNBUFFERED", "1");
        env.put("TF_CPP_MIN_LOG_LEVEL", "3");
        
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            processBuilder.command("cmd", "/c", command);
        } else {
            processBuilder.command("bash", "-c", command);
        }
        // 启动进程
        File workingDir = new File(System.getProperty("user.dir"));
        processBuilder.directory(workingDir);
        Process process = processBuilder.start();
        
        // 使用UTF-8编码
        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        startStderrConsumer(process);
        return Triple.of(process, writer, reader);
    }

    /**
     * 启动后台线程消费stderr输出，防止缓冲区满导致Python进程阻塞
     * @param process Python进程
     */
    private  static void startStderrConsumer(Process process) {
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader stderrReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = stderrReader.readLine()) != null) {
                    // 可以选择记录到日志或忽略
                    // logger.debug("[Python stderr] {}", line);
                    // 或者完全忽略：什么都不做
                }
            } catch (IOException e) {
                // 进程关闭时会抛出异常，这是正常的
                logger.debug("stderr读取线程结束: {}", e.getMessage());
            }
        }, "python-stderr-consumer");
        stderrThread.setDaemon(true); // 设置为守护线程，不会阻止JVM关闭
        stderrThread.start();
    }
}
