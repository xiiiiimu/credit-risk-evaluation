package com.credit.agent.health;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.credit.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class AgentHealthService {

    private final AgentProperties properties;
    private final RestTemplate agentRestTemplate;

    private volatile AgentHealthStatus serviceStatus = AgentHealthStatus.UNKNOWN;
    private volatile long lastCheckMs = 0;
    private volatile Map<String, AgentHealthStatus> agentStatuses = Collections.emptyMap();
    private volatile String lastError;

    public AgentHealthService(AgentProperties properties, RestTemplate agentRestTemplate) {
        this.properties = properties;
        this.agentRestTemplate = agentRestTemplate;
    }

    public AgentHealthStatus getServiceStatus() {
        refreshIfStale();
        return serviceStatus;
    }

    public Map<String, AgentHealthStatus> getAgentStatuses() {
        refreshIfStale();
        return agentStatuses;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isCallable() {
        AgentHealthStatus status = getServiceStatus();
        return status == AgentHealthStatus.UP || status == AgentHealthStatus.DEGRADED;
    }

    public void assertCallable() {
        if (!isCallable()) {
            throw new AgentUnavailableException(
                    "Agent 服务不可用: " + (lastError != null ? lastError : serviceStatus.name()));
        }
    }

    public Map<String, Object> snapshot() {
        refreshIfStale();
        Map<String, Object> data = new HashMap<>(4);
        data.put("serviceStatus", serviceStatus.name());
        data.put("lastCheckMs", lastCheckMs);
        data.put("lastError", lastError);
        Map<String, String> agents = new HashMap<>();
        agentStatuses.forEach((k, v) -> agents.put(k, v.name()));
        data.put("agents", agents);
        return data;
    }

    public void refresh() {
        String url = properties.getBaseUrl().replaceAll("/$", "") + "/v1/agents/health";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", properties.getInternalApiKey());
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = agentRestTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            if (response.getBody() == null) {
                markDown("empty health response");
                return;
            }
            JSONObject body = JSONUtil.parseObj(response.getBody());
            String remoteStatus = body.getStr("serviceStatus", "UP");
            serviceStatus = parseStatus(remoteStatus);
            JSONObject agents = body.getJSONObject("agents");
            Map<String, AgentHealthStatus> parsed = new HashMap<>();
            if (agents != null) {
                for (String key : agents.keySet()) {
                    JSONObject item = agents.getJSONObject(key);
                    String status = item != null ? item.getStr("status", "UP") : "UP";
                    parsed.put(key, parseStatus(status));
                }
            }
            agentStatuses = Collections.unmodifiableMap(parsed);
            lastError = null;
            lastCheckMs = System.currentTimeMillis();
        } catch (Exception e) {
            markDown(e.getMessage());
            log.warn("Agent health check failed: {}", e.getMessage());
        }
    }

    private void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastCheckMs >= properties.getHealthCheckIntervalMs()) {
            refresh();
        }
    }

    private void markDown(String error) {
        serviceStatus = AgentHealthStatus.DOWN;
        agentStatuses = Collections.emptyMap();
        lastError = error;
        lastCheckMs = System.currentTimeMillis();
    }

    private AgentHealthStatus parseStatus(String raw) {
        if (raw == null) {
            return AgentHealthStatus.UNKNOWN;
        }
        try {
            return AgentHealthStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AgentHealthStatus.UNKNOWN;
        }
    }
}
