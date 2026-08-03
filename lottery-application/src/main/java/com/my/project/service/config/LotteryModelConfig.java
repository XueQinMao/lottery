package com.my.project.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * LotteryModelConfig
 *
 * @author 刘强
 * @version 2025/10/23 17:07
 **/
@Component
@ConfigurationProperties(prefix = "lottery.model")
@Data
public class LotteryModelConfig {
    private String path;
    private Map<String, String> csv = new HashMap<>();
}
