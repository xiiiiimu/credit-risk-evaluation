package com.credit.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "credit.agent.cache")
public class AgentCacheProperties {

    private boolean enabled = true;
    private long llmTtlHours = 24;
    private long ocrTtlHours = 72;
}
