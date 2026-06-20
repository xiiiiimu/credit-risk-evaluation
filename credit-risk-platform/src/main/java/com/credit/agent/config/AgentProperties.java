package com.credit.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "credit.agent")
public class AgentProperties {

    /**
     * Python credit-agent 服务根地址，例如 http://127.0.0.1:8090
     */
    private String baseUrl = "http://127.0.0.1:8090";

    /**
     * Spring 与 Python 互调内部接口密钥（请求头 X-Internal-Api-Key）
     */
    private String internalApiKey = "credit-agent-secret";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 60000;

    private int retryMaxAttempts = 3;

    private int retryBackoffMs = 300;

    private int circuitFailureThreshold = 5;

    private long circuitOpenMs = 30000;

    private int circuitHalfOpenMaxProbes = 1;

    private long healthCheckIntervalMs = 10000;

    /** 运营管理员用户 ID 列表（逗号分隔配置） */
    private String adminUserIds = "1";

    private int chatHistoryLimit = 5;
}
