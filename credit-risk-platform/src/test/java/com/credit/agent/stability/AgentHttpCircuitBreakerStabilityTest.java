package com.credit.agent.stability;

import com.credit.agent.config.AgentProperties;
import com.credit.agent.facade.AgentHttpExecutor;
import com.credit.agent.health.AgentHealthService;
import com.credit.agent.health.AgentHealthStatus;
import com.credit.agent.health.AgentUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentHttpCircuitBreakerStabilityTest {

    @Test
    void stability_agentCircuitOpen_blocksFurtherRemoteCalls() {
        AgentProperties properties = new AgentProperties();
        properties.setRetryMaxAttempts(1);
        properties.setCircuitFailureThreshold(2);
        properties.setCircuitOpenMs(60_000);
        properties.setCircuitHalfOpenMaxProbes(1);
        AgentHttpExecutor executor = new AgentHttpExecutor(properties);

        AtomicInteger calls = new AtomicInteger();
        Runnable fail = () -> {
            calls.incrementAndGet();
            throw new RuntimeException("agent down");
        };

        assertThrows(RuntimeException.class, () -> executor.execute("analyze", () -> {
            fail.run();
            return null;
        }));
        assertThrows(RuntimeException.class, () -> executor.execute("analyze", () -> {
            fail.run();
            return null;
        }));
        assertThrows(IllegalStateException.class, () -> executor.execute("analyze", () -> "ok"));
        assertEquals(2, calls.get(), "熔断后不应继续调用远端");
    }

    @Test
    void stability_agentHealthDown_assertCallableThrows() {
        AgentProperties properties = new AgentProperties();
        properties.setHealthCheckIntervalMs(0L);
        AgentHealthService healthService = new AgentHealthService(properties, new RestTemplate());
        ReflectionTestUtils.setField(healthService, "serviceStatus", AgentHealthStatus.DOWN);
        ReflectionTestUtils.setField(healthService, "lastError", "connection refused");

        assertThrows(AgentUnavailableException.class, healthService::assertCallable);
    }
}
