package com.my.project.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LotteryCacheProperties
 *
 * <p>LLM 分析结果多级缓存（L1 内存 Caffeine + L2 本地磁盘 JSON 文件）相关配置。
 *
 * @author 刘强
 * @version 2026/08/06 11:05
 **/
@Component
@ConfigurationProperties(prefix = "lottery.cache.analysis")
@Data
public class LotteryCacheProperties {

    /** 是否启用 L2 本地磁盘缓存，默认 true */
    private boolean diskEnabled = true;

    /** L2 磁盘缓存目录，需可读写 */
    private String diskDir;

    /** L1 内存缓存最大容量，默认 1 */
    private int memorySize = 1;
}
