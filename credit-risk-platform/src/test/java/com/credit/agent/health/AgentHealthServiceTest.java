package com.credit.agent.health;

import com.credit.agent.config.AgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentHealthServiceTest {

    private AgentProperties properties;
    private RestTemplate restTemplate;
    private AgentHealthService service;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        properties.setBaseUrl("http://127.0.0.1:8090");
        properties.setHealthCheckIntervalMs(0);
        restTemplate = mock(RestTemplate.class);
        service = new AgentHealthService(properties, restTemplate);
    }

    @Test
    void refresh_parses_up_status() {
        String body = "{\"serviceStatus\":\"UP\",\"agents\":{\"DocumentReviewAgent\":{\"status\":\"UP\"}}}";
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));

        service.refresh();

        assertEquals(AgentHealthStatus.UP, service.getServiceStatus());
        assertTrue(service.isCallable());
        assertEquals(AgentHealthStatus.UP, service.getAgentStatuses().get("DocumentReviewAgent"));
    }

    @Test
    void refresh_marks_down_on_error() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("connection refused"));

        service.refresh();

        assertEquals(AgentHealthStatus.DOWN, service.getServiceStatus());
        assertFalse(service.isCallable());
    }
}
