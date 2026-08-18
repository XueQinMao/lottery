package com.my.project.service.llm.cache;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.service.config.LotteryCacheProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;

/**
 * LotteryAnalysisMultiLevelCache
 *
 * <p>LLM 分析结果多级缓存：
 * <ol>
 *     <li>L1 内存：Caffeine，命中直接返回</li>
 *     <li>L2 本地磁盘：JSON 文件，命中后回填 L1</li>
 *     <li>L3 兜底：调用方传入的 loader（通常是调用 LLM 重新计算），
 *         计算结果同时写入 L2 与 L1</li>
 * </ol>
 *
 * <p>Key 由调用方自行设计（建议包含期号等业务版本字段以实现自然失效）。
 * 磁盘文件名 = MD5(key) 前 16 位 + ".json"，避免特殊字符问题。
 * 磁盘写入采用「临时文件 + Files.move ATOMIC_MOVE」保证原子性，避免读到半截文件。
 * 任何磁盘读写异常只 warn 不抛，自动降级到下一层。
 *
 * @author 刘强
 * @version 2026/08/06 11:05
 **/
@Slf4j
@Component
public class LotteryAnalysisMultiLevelCache {

    private final LotteryCacheProperties properties;

    /** L1 内存缓存 */
    private Cache<String, LotteryAnalysisRespBo> memory;

    /** L2 磁盘缓存目录 */
    private Path diskDir;

    /** 是否启用 L2 */
    private boolean diskEnabled;

    public LotteryAnalysisMultiLevelCache(LotteryCacheProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        this.diskEnabled = properties.isDiskEnabled();
        this.memory = Caffeine.newBuilder()
            .maximumSize(Math.max(properties.getMemorySize(), 1))
            .evictionListener((String key, LotteryAnalysisRespBo value, RemovalCause cause) ->
                log.warn("L1 Caffeine evicted: key={}, cause={}", key, cause))
            .recordStats()
            .build();
        if (diskEnabled) {
            String dir = properties.getDiskDir();
            if (dir == null || dir.isBlank()) {
                log.warn("lottery.cache.analysis.disk-dir 未配置，自动降级关闭 L2 磁盘缓存");
                this.diskEnabled = false;
                this.diskDir = null;
            } else {
                try {
                    this.diskDir = Paths.get(dir);
                    Files.createDirectories(diskDir);
                    log.info("L2 磁盘缓存目录初始化完成: {}", diskDir.toAbsolutePath());
                } catch (Exception e) {
                    log.warn("L2 磁盘目录创建失败，自动降级关闭 L2: dir={}, err={}", dir, e.getMessage());
                    this.diskEnabled = false;
                    this.diskDir = null;
                }
            }
        }
        log.info("LotteryAnalysisMultiLevelCache initialized: diskEnabled={}, memorySize={}",
            this.diskEnabled, properties.getMemorySize());
    }

    /**
     * 多级缓存读取。
     *
     * @param key    缓存 Key（建议含业务版本字段，如期号）
     * @param loader L3 兜底加载函数（当 L1、L2 均未命中时调用，返回值将写入 L1 与 L2）
     * @return 缓存或加载得到的结果，可能为 null
     */
    public LotteryAnalysisRespBo get(String key, Function<String, LotteryAnalysisRespBo> loader) {
        return memory.get(key, k -> {
            // L2 磁盘
            LotteryAnalysisRespBo diskVal = readFromDisk(k);
            if (diskVal != null) {
                log.info("L2 disk hit: key={}", k);
                return diskVal;
            }
            // L3 loader
            log.info("L3 loader compute: key={}", k);
            LotteryAnalysisRespBo result = loader.apply(k);
            if (result != null) {
                writeToDisk(k, result);
            }
            return result;
        });
    }

    /**
     * 回写 L1 / L2（用于补全缺失字段后覆盖旧缓存）。
     */
    public void put(String key, LotteryAnalysisRespBo value) {
        if (key == null || value == null) {
            return;
        }
        memory.put(key, value);
        writeToDisk(key, value);
    }

    /**
     * 主动失效指定 Key（同时清理 L1 与 L2）。
     */
    public void invalidate(String key) {
        memory.invalidate(key);
        if (diskEnabled) {
            try {
                Path p = filePath(key);
                Files.deleteIfExists(p);
            } catch (Exception e) {
                log.warn("L2 invalidate failed: key={}, err={}", key, e.getMessage());
            }
        }
    }

    /**
     * 清空全部缓存（L1 + L2）。
     */
    public void invalidateAll() {
        memory.invalidateAll();
        if (diskEnabled && diskDir != null) {
            try (var stream = Files.list(diskDir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception e) {
                            log.warn("L2 invalidateAll delete failed: file={}, err={}", p, e.getMessage());
                        }
                    });
            } catch (Exception e) {
                log.warn("L2 invalidateAll failed: dir={}, err={}", diskDir, e.getMessage());
            }
        }
    }

    public String getStats() {
        return memory.stats().toString();
    }

    // ==================== 内部方法 ====================

    private Path filePath(String key) {
        String name = key+ ".json";
        return diskDir.resolve(name);
    }

    private LotteryAnalysisRespBo readFromDisk(String key) {
        if (!diskEnabled || diskDir == null) {
            return null;
        }
        try {
            Path p = filePath(key);
            if (!Files.exists(p)) {
                return null;
            }
            String json = Files.readString(p, StandardCharsets.UTF_8);
            return JSON.parseObject(json, LotteryAnalysisRespBo.class);
        } catch (Exception e) {
            log.warn("L2 read failed: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    private void writeToDisk(String key, LotteryAnalysisRespBo value) {
        if (!diskEnabled || diskDir == null) {
            return;
        }
        Path target = filePath(key);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, JSON.toJSONString(value), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.warn("L2 write failed: key={}, err={}", key, e.getMessage());
            // 清理可能残留的临时文件
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
                // no-op
            }
        }
    }
}
