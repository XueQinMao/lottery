package com.my.project.python.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 资源路径工具类
 * 用于获取classpath下的资源文件路径，支持开发环境和JAR打包环境
 *
 * @author 刘强
 * @version 2025/10/24
 */
public class ResourcePathUtils {

    private static final Logger logger = LoggerFactory.getLogger(ResourcePathUtils.class);

    /**
     * 获取Python脚本的绝对路径
     * 
     * @param scriptName Python脚本文件名（如 "train_models.py"）
     * @return Python脚本的绝对路径
     */
    public static String getPythonScriptPath(String scriptName) {
        try {
            // 方案1: 尝试从classpath直接获取（开发环境）
            String path = getPythonScriptPathFromClassPath(scriptName);
            if (path != null) {
                logger.info("从ClassPath获取Python脚本路径: {}", path);
                return path;
            }

            // 方案2: 从JAR中提取到临时目录（生产环境）
            path = extractPythonScriptFromJar(scriptName);
            if (path != null) {
                logger.info("从JAR提取Python脚本到临时目录: {}", path);
                return path;
            }

            throw new RuntimeException("无法获取Python脚本路径: " + scriptName);

        } catch (Exception e) {
            logger.error("获取Python脚本路径失败: {}", scriptName, e);
            throw new RuntimeException("获取Python脚本路径失败: " + scriptName, e);
        }
    }

    /**
     * 获取Python脚本目录的绝对路径
     * 
     * @return Python脚本目录的绝对路径
     */
    public static String getPythonDir() {
        try {
            // 方案1: 尝试从classpath直接获取（开发环境）
            String path = getPythonDirFromClassPath();
            if (path != null) {
                logger.info("从ClassPath获取Python目录: {}", path);
                return path;
            }

            // 方案2: 从JAR中提取到临时目录（生产环境）
            path = extractPythonDirFromJar();
            if (path != null) {
                logger.info("从JAR提取Python目录到临时目录: {}", path);
                return path;
            }

            throw new RuntimeException("无法获取Python目录路径");

        } catch (Exception e) {
            logger.error("获取Python目录路径失败", e);
            throw new RuntimeException("获取Python目录路径失败", e);
        }
    }

    /**
     * 方案1: 从ClassPath获取Python脚本路径（开发环境）
     */
    private static String getPythonScriptPathFromClassPath(String scriptName) {
        try {
            // 尝试方法1: 使用ClassPathResource
            Resource resource = new ClassPathResource("python/" + scriptName);
            if (resource.exists() && resource.isFile()) {
                return resource.getFile().getAbsolutePath();
            }

            // 尝试方法2: 使用ClassLoader.getResource
            URL url = ResourcePathUtils.class.getClassLoader().getResource("python/" + scriptName);
            if (url != null && "file".equals(url.getProtocol())) {
                File file = new File(url.toURI());
                if (file.exists() && file.isFile()) {
                    return file.getAbsolutePath();
                }
            }

            return null;
        } catch (Exception e) {
            logger.debug("从ClassPath获取失败，尝试其他方案", e);
            return null;
        }
    }

    /**
     * 方案1: 从ClassPath获取Python目录路径（开发环境）
     */
    private static String getPythonDirFromClassPath() {
        try {
            // 尝试方法1: 使用ClassPathResource
            Resource resource = new ClassPathResource("python/");
            if (resource.exists()) {
                File file = resource.getFile();
                if (file.exists() && file.isDirectory()) {
                    return file.getAbsolutePath();
                }
            }

            // 尝试方法2: 使用ClassLoader.getResource
            URL url = ResourcePathUtils.class.getClassLoader().getResource("python");
            if (url != null && "file".equals(url.getProtocol())) {
                File file = new File(url.toURI());
                if (file.exists() && file.isDirectory()) {
                    return file.getAbsolutePath();
                }
            }

            return null;
        } catch (Exception e) {
            logger.debug("从ClassPath获取Python目录失败，尝试其他方案", e);
            return null;
        }
    }

    /**
     * 方案2: 从JAR中提取Python脚本到临时目录（生产环境）
     */
    private static String extractPythonScriptFromJar(String scriptName) {
        try {
            // 读取资源流
            InputStream inputStream = ResourcePathUtils.class.getClassLoader()
                    .getResourceAsStream("python/" + scriptName);
            
            if (inputStream == null) {
                logger.error("在JAR中找不到资源: python/{}", scriptName);
                return null;
            }

            // 创建临时目录
            Path tempDir = Files.createTempDirectory("lottery-python-");
            File tempFile = new File(tempDir.toFile(), scriptName);

            // 提取文件到临时目录
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            logger.info("已从JAR提取文件: {} -> {}", scriptName, tempFile.getAbsolutePath());
            
            // 设置JVM退出时删除临时文件
            tempFile.deleteOnExit();
            tempDir.toFile().deleteOnExit();

            return tempFile.getAbsolutePath();

        } catch (Exception e) {
            logger.error("从JAR提取Python脚本失败: {}", scriptName, e);
            return null;
        }
    }

    /**
     * 方案2: 从JAR中提取整个Python目录到临时目录（生产环境）
     */
    private static String extractPythonDirFromJar() {
        try {
            // 创建临时目录
            Path tempDir = Files.createTempDirectory("lottery-python-");
            File pythonDir = new File(tempDir.toFile(), "python");
            pythonDir.mkdirs();

            // 获取python目录下的所有资源
            String[] pythonFiles = {
                "train_models.py",
                "predict.py",
                "requirements.txt",
                "train_models_usage.md",
                "ModelTrainingUtils.java.example"
                // 如果有更多文件，继续添加
            };

            boolean extractedAtLeastOne = false;

            for (String fileName : pythonFiles) {
                InputStream inputStream = ResourcePathUtils.class.getClassLoader()
                        .getResourceAsStream("python/" + fileName);
                
                if (inputStream != null) {
                    File targetFile = new File(pythonDir, fileName);
                    
                    try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    targetFile.deleteOnExit();
                    extractedAtLeastOne = true;
                    logger.debug("已提取文件: {}", fileName);
                }
            }

            if (extractedAtLeastOne) {
                pythonDir.deleteOnExit();
                tempDir.toFile().deleteOnExit();
                return pythonDir.getAbsolutePath();
            }

            return null;

        } catch (Exception e) {
            logger.error("从JAR提取Python目录失败", e);
            return null;
        }
    }

    /**
     * 检查资源是否存在于ClassPath中
     * 
     * @param resourcePath 资源路径（相对于classpath）
     * @return 是否存在
     */
    public static boolean resourceExists(String resourcePath) {
        try {
            Resource resource = new ClassPathResource(resourcePath);
            return resource.exists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取资源的输入流
     * 
     * @param resourcePath 资源路径（相对于classpath）
     * @return 输入流
     */
    public static InputStream getResourceAsStream(String resourcePath) {
        try {
            Resource resource = new ClassPathResource(resourcePath);
            if (resource.exists()) {
                return resource.getInputStream();
            }
            return null;
        } catch (Exception e) {
            logger.error("获取资源流失败: {}", resourcePath, e);
            return null;
        }
    }

    /**
     * 测试方法
     */
    public static void main(String[] args) {
        System.out.println("=== 测试资源路径工具类 ===");
        
        // 测试1: 获取Python脚本路径
        try {
            String scriptPath = getPythonScriptPath("train_models.py");
            System.out.println("Python脚本路径: " + scriptPath);
            System.out.println("文件存在: " + new File(scriptPath).exists());
        } catch (Exception e) {
            System.err.println("获取Python脚本路径失败: " + e.getMessage());
        }

        // 测试2: 获取Python目录
        try {
            String pythonDir = getPythonDir();
            System.out.println("Python目录: " + pythonDir);
            System.out.println("目录存在: " + new File(pythonDir).exists());
        } catch (Exception e) {
            System.err.println("获取Python目录失败: " + e.getMessage());
        }

        // 测试3: 检查资源是否存在
        System.out.println("资源存在检查:");
        System.out.println("  python/train_models.py: " + resourceExists("python/train_models.py"));
        System.out.println("  python/predict.py: " + resourceExists("python/predict.py"));
        System.out.println("  python/not_exist.py: " + resourceExists("python/not_exist.py"));
    }
}

