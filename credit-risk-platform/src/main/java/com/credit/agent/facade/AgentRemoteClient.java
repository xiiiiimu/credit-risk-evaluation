package com.credit.agent.facade;

import cn.hutool.json.JSONUtil;
import com.credit.agent.config.AgentProperties;
import com.credit.agent.dto.CreditAgentRequest;
import com.credit.agent.dto.CreditAnalysisResponse;
import com.credit.agent.exception.WorkflowRunningException;
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
        HttpHeaders headers = buildHeaders(request, traceId);
        HttpEntity<Object> entity = new HttpEntity<>(request, headers);

        Long taskId = extractTaskId(request);
        String workflowId = extractWorkflowId(request);
        long totalStart = System.currentTimeMillis();
        try {
            long httpSendStart = System.nanoTime();
            logAgentHttpPhase(taskId, workflowId, "agentCall.httpSend", httpSendStart);

            String rawBody = httpExecutor.execute(agentName, () ->
                    agentRestTemplate.postForObject(url, entity, String.class));

            logAgentHttpPhase(taskId, workflowId, "agentCall.httpReceive", httpSendStart);

            long jsonParseStart = System.nanoTime();
            T body = JSONUtil.toBean(rawBody, type);
            logAgentHttpPhase(taskId, workflowId, "agentCall.jsonParse", jsonParseStart);

            long costMs = System.currentTimeMillis() - totalStart;
            agentToolMetrics.recordAgentCall(agentName, true, costMs);
            agentTaskLogService.logRemote(agentName, null, agentName, body, true, costMs, null);
            return body;
        } catch (WorkflowRunningException e) {
            long costMs = System.currentTimeMillis() - totalStart;
            agentToolMetrics.recordAgentCall(agentName, false, costMs);
            agentTaskLogService.logRemote(agentName, null, agentName, "workflow running", false, costMs, e.getMessage());
            log.info("Agent workflow still running, skip retry/circuit op={} workflow conflict", agentName);
            throw e;
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - totalStart;
            agentToolMetrics.recordAgentCall(agentName, false, costMs);
            agentTaskLogService.logRemote(agentName, null, agentName, e.getMessage(), false, costMs, e.getMessage());
            log.error("Agent remote call failed: {} {}", agentName, url, e);
            throw e;
        }
    }

    private HttpHeaders buildHeaders(Object request, String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", properties.getInternalApiKey());
        if (traceId != null) {
            headers.set("X-Trace-Id", traceId);
        }
        if (request instanceof CreditAgentRequest) {
            CreditAgentRequest agentReq = (CreditAgentRequest) request;
            if (AgentWorkflowLockConstants.MODE_JAVA_OWNED.equals(agentReq.getLockMode())) {
                headers.set(AgentWorkflowLockConstants.HEADER_LOCK_MODE,
                        AgentWorkflowLockConstants.MODE_JAVA_OWNED);
                headers.set(AgentWorkflowLockConstants.HEADER_LOCK_OWNER,
                        AgentWorkflowLockConstants.OWNER_JAVA_CONSUMER);
                if (agentReq.getLockOwner() != null) {
                    headers.set(AgentWorkflowLockConstants.HEADER_LOCK_OWNER_TOKEN,
                            agentReq.getLockOwner());
                }
            }
        }
        return headers;
    }

    private static Long extractTaskId(Object request) {
        if (request instanceof CreditAgentRequest) {
            return ((CreditAgentRequest) request).getTaskId();
        }
        return null;
    }

    private static String extractWorkflowId(Object request) {
        if (request instanceof CreditAgentRequest) {
            return ((CreditAgentRequest) request).getWorkflowId();
        }
        return null;
    }

    private static void logAgentHttpPhase(Long taskId, String workflowId, String stage, long startNano) {
        log.info("[PERF][consume] taskId={} workflowId={} stage={} cost={}ms",
                taskId, workflowId, stage, (System.nanoTime() - startNano) / 1_000_000L);
    }
}
