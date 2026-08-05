package com.my.project.service.support;

import cn.hutool.core.io.FileUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * FileUtils
 *
 * @author 刘强
 * @version 2026/01/04 17:09
 **/
public class FileUtils {

    private static Logger logger = LoggerFactory.getLogger(FileUtils.class);

    /**
     * lotteryModelConfig.getPath()+"/"+predictResult.getOpenDate() + "_预测结果.txt"
     * String content = String.join("@", StringUtils.join(predictResult.getRedBalls(), ","),
     *             String.valueOf(predictResult.getBlueBall()),
     *             predictResult.getExplanation(), predictResult.getTotalScore().toString());
     * @param filePath
     * @param content
     */
    public static void append(String filePath, String content){
        if(StringUtils.isAnyBlank(filePath, content)){
            return ;
        }
        synchronized (filePath.intern()){
            File file = new File(filePath);
            FileUtil.appendString(content+System.lineSeparator(),  file, StandardCharsets.UTF_8);
        }
    }

    public static void readLine(String filePath, Consumer<String> contentConsumer){
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            logger.error("文件 {} 不存在", filePath);
            return;
        }
        try (Stream<String> lines = Files.lines(path)) {
            lines.parallel().forEach(contentConsumer);
        } catch (IOException e) {
            logger.error("文件 {} 行解析异常", filePath, e);
        }
    }

    public static void deleteFile(String filePath){
        File file = new File(filePath);
        if(file.exists()){
            file.delete();
        }
    }
}
