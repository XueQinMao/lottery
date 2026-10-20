package com.my.project.llm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "lottery.llm.zone-trend")
public class ZoneTrendModelProperties {
    private String apiKey = "";
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode";
    private String model = "qwen3-vl-plus";
    private Duration readTimeout = Duration.ofSeconds(120);
    private int maxTokens = 4096;
}
