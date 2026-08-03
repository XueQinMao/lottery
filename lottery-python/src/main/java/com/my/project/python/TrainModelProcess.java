package com.my.project.python;

import com.my.project.python.support.PythonUtils;
import com.my.project.python.support.ResourcePathUtils;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TrainModelProcess 训练模型
 *
 * @author 刘强
 * @version 2025/10/24 15:50
 **/
@Component
public class TrainModelProcess {

    /**
     * 获取Python脚本目录
     */
    private static String getPythonDir() {
        return ResourcePathUtils.getPythonDir();
    }

    public void process(String scriptName, Map<String,String> params) throws IOException, InterruptedException {
        StringBuilder command = new StringBuilder();
        command.append("python ").append(ResourcePathUtils.getPythonScriptPath(scriptName));
        params.forEach((key, value) -> command.append(" --").append(key).append(" ").append(value));
        PythonUtils.executeCommand(command.toString());
    }

    /**
     * 训练模型（完整模式）
     *
     * @param mlFeaturesPath ML特征文件路径
     * @param historyPath 历史数据文件路径
     * @param modelDir 模型保存目录
     * @return 训练是否成功
     */
    public static boolean trainModels(String mlFeaturesPath, String historyPath, String modelDir) {
        Map<String, String> params = new HashMap<>();
        params.put("--ml-features", mlFeaturesPath);
        if (historyPath != null && !historyPath.isEmpty()) {
            params.put("--history", historyPath);
        }
        if (modelDir != null && !modelDir.isEmpty()) {
            params.put("--model-dir", modelDir);
        }

        return trainModels(params);
    }


    /**
     * 训练模型（指定所有参数）
     *
     * @param mlFeaturesPath ML特征文件路径
     * @param historyPath 历史数据文件路径
     * @param sequenceFeaturesPath 序列特征文件路径
     * @param markovFeaturesPath Markov特征文件路径
     * @param modelDir 模型保存目录
     * @return 训练是否成功
     */
    public static boolean trainModels(String mlFeaturesPath,
                                      String historyPath,
                                      String sequenceFeaturesPath,
                                      String markovFeaturesPath,
                                      String modelDir) {
        Map<String, String> params = new HashMap<>();
        params.put("--ml-features", mlFeaturesPath);

        if (historyPath != null && !historyPath.isEmpty()) {
            params.put("--history", historyPath);
        }
        if (sequenceFeaturesPath != null && !sequenceFeaturesPath.isEmpty()) {
            params.put("--sequence-features", sequenceFeaturesPath);
        }
        if (markovFeaturesPath != null && !markovFeaturesPath.isEmpty()) {
            params.put("--markov-features", markovFeaturesPath);
        }
        if (modelDir != null && !modelDir.isEmpty()) {
            params.put("--model-dir", modelDir);
        }

        return trainModels(params);
    }

    /**
     * 训练模型（通用方法）
     *
     * @param params 参数映射
     * @return 训练是否成功
     */
    public static boolean trainModels(Map<String, String> params) {
        try {
            // 获取Python脚本路径
            String pythonScript = ResourcePathUtils.getPythonScriptPath("train_models.py");

            // 构建命令
            List<String> command = new ArrayList<>();
            command.add("python");  // 或 "python3"
            command.add(pythonScript);

            // 添加参数
            for (Map.Entry<String, String> entry : params.entrySet()) {
                command.add(entry.getKey());
                command.add(entry.getValue());
            }

            System.out.println("[训练] 执行命令: " + String.join(" ", command));

            // 创建进程
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(getPythonDir()));
            pb.redirectErrorStream(true);  // 合并错误流和输出流

            // 启动进程
            Process process = pb.start();

            // 读取输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[Python] " + line);
                }
            }

            // 等待完成
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("[训练] 模型训练成功完成");
                return true;
            } else {
                System.err.println("[训练] 模型训练失败，退出码: " + exitCode);
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 示例：训练模型
     */
    public static void main(String[] args) {
        // 示例1: 最简模式（仅传统ML模型）
        String mlFeaturesPath = "E:/studyProject/lottery/lottery-api/src/main/resources/data/ml_features.csv";
        boolean success1 = trainModels(mlFeaturesPath, null, null);
        System.out.println("示例1 训练结果: " + (success1 ? "成功" : "失败"));

        // 示例2: 完整模式（所有模型）
        String historyPath = "E:/studyProject/lottery/lottery-api/src/main/resources/data/history.csv";
        String modelDir = "E:/studyProject/lottery/lottery-api/src/main/resources/models";
        boolean success2 = trainModels(mlFeaturesPath, historyPath, modelDir);
        System.out.println("示例2 训练结果: " + (success2 ? "成功" : "失败"));

        // 示例3: 指定所有文件
        String sequenceFeaturesPath = "E:/studyProject/lottery/lottery-api/src/main/resources/data/sequence_features.csv";
        String markovFeaturesPath = "E:/studyProject/lottery/lottery-api/src/main/resources/data/markov_features.csv";
        boolean success3 = trainModels(
                mlFeaturesPath,
                historyPath,
                sequenceFeaturesPath,
                markovFeaturesPath,
                modelDir
        );
        System.out.println("示例3 训练结果: " + (success3 ? "成功" : "失败"));
    }


}
