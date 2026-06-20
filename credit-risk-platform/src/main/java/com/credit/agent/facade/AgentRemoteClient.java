package com.credit.agent.facade;

import com.credit.agent.config.AgentProperties;
import com.credit.agent.dto.CreditAgentRequest;
import com.credit.agent.dto.CreditAnalysisResponse;
import com.credit.agent.health.AgentHealthService;
import com.credit.agent.metrics.AgentToolMetrics;
import com.credit.agent.service.AgentTaskLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class AgentRemoteClient {

    private final AgentProperties properties;
    private final RestTemplate agentRestTemplate;
    private final AgentHttpExecutor httpExecutor;
    private final AgentToolMetrics agentToolMetrics;
    private final AgentTaskLogService agentTaskLogService;
    private final AgentHealthService agentHealthService;

    public AgentRemoteClient(AgentProperties properties, RestTemplate agentRestTemplate,
                             AgentHttpExecutor httpExecutor, AgentToolMetrics agentToolMetrics,
                             AgentTaskLogService agentTaskLogService,
                             AgentHealthService agentHealthService) {
        this.properties = properties;
        this.agentRestTemplate = agentRestTemplate;
        this.httpExecutor = httpExecutor;
        this.agentToolMetrics = agentToolMetrics;
        this.agentTaskLogService = agentTaskLogService;
        this.agentHealthService = agentHealthService;
    }

    public CreditAnalysisResponse analyzeCreditApplication(CreditAgentRequest request, String traceId) {
        agentHealthService.assertCallable();
        return post("/v1/agents/credit/analyze", request, traceId, CreditAnalysisResponse.class, "credit-analyze");
    }

    private <T> T post(String path, Object request, String traceId, Class<T> type, String agentName) {
        String url = properties.getBaseUrl().replaceAll("/$", "") + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (traceId != null) {
            headers.set("X-Trace-Id", traceId);
        }
        headers.set("X-Internal-Api-Key", properties.getInternalApiKey());
        HttpEntity<Object> entity = new HttpEntity<>(request, headers);
        long start = System.currentTimeMillis();
        try {
            T body = httpExecutor.execute(agentName, () ->
                    agentRestTemplate.postForObject(url, entity, type));
            long costMs = System.currentTimeMillis() - start;
            agentToolMetrics.recordAgentCall(agentName, true, costMs);
            agentTaskLogService.logRemote(agentName, null, agentName, body, true, costMs, null);
            return body;
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            agentToolMetrics.recordAgentCall(agentName, false, costMs);
            agentTaskLogService.logRemote(agentName, null, agentName, e.getMessage(), false, costMs, e.getMessage());
            log.error("Agent remote call failed: {} {}", agentName, url, e);
            throw e;
        }
    }
}
