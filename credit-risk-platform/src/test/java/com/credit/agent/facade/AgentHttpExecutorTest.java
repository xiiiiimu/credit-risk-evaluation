package com.credit.agent.facade;

import com.credit.agent.config.AgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentHttpExecutorTest {

    private AgentHttpExecutor executor;

    @BeforeEach
    void setUp() {
        AgentProperties properties = new AgentProperties();
        properties.setRetryMaxAttempts(1);
        properties.setCircuitFailureThreshold(2);
        properties.setCircuitOpenMs(1000);
        properties.setCircuitHalfOpenMaxProbes(1);
        executor = new AgentHttpExecutor(properties);
    }

    @Test
    void opens_circuit_after_failures() {
        AtomicInteger calls = new AtomicInteger();
        Runnable failing = () -> {
            calls.incrementAndGet();
            throw new RuntimeException("boom");
        };

        assertThrows(RuntimeException.class, () -> executor.execute("test", () -> {
            failing.run();
            return null;
        }));
        assertThrows(RuntimeException.class, () -> executor.execute("test", () -> {
            failing.run();
            return null;
        }));
        assertThrows(IllegalStateException.class, () -> executor.execute("test", () -> "ok"));
        assertEquals(2, calls.get());
    }
}
