package com.credit.agent.facade;

import com.credit.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Python Agent HTTP 调用：有限重试 + 熔断（含半开探测）。
 */
@Slf4j
@Component
public class AgentHttpExecutor {

    private enum CircuitState {
        CLOSED, OPEN, HALF_OPEN
    }

    private final AgentProperties properties;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenUntil = new AtomicLong(0);
    private volatile CircuitState circuitState = CircuitState.CLOSED;
    private final AtomicInteger halfOpenProbes = new AtomicInteger(0);

    public AgentHttpExecutor(AgentProperties properties) {
        this.properties = properties;
    }

    public <T> T execute(String operation, Supplier<T> supplier) {
        long now = System.currentTimeMillis();
        if (circuitState == CircuitState.OPEN) {
            if (now >= circuitOpenUntil.get()) {
                circuitState = CircuitState.HALF_OPEN;
                halfOpenProbes.set(0);
                log.info("Agent circuit half-open for op={}", operation);
            } else {
                throw new IllegalStateException("Agent 服务熔断中，请稍后重试");
            }
        }
        if (circuitState == CircuitState.HALF_OPEN
                && halfOpenProbes.get() >= properties.getCircuitHalfOpenMaxProbes()) {
            throw new IllegalStateException("Agent 服务半开探测中，请稍后重试");
        }
        if (circuitState == CircuitState.HALF_OPEN) {
            halfOpenProbes.incrementAndGet();
        }

        int max = Math.max(1, properties.getRetryMaxAttempts());
        int backoff = Math.max(50, properties.getRetryBackoffMs());
        RuntimeException last = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            try {
                T result = supplier.get();
                onSuccess();
                return result;
            } catch (RestClientException e) {
                last = new RuntimeException(e);
                log.warn("Agent call failed op={} attempt={}/{} msg={}", operation, attempt, max, e.getMessage());
                if (attempt < max && isRetryable(e)) {
                    sleep(backoff * attempt);
                }
            } catch (RuntimeException e) {
                last = e;
                break;
            }
        }
        onFailure(operation);
        throw last != null ? last : new RuntimeException("Agent call failed: " + operation);
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        circuitState = CircuitState.CLOSED;
        halfOpenProbes.set(0);
    }

    private void onFailure(String operation) {
        if (circuitState == CircuitState.HALF_OPEN) {
            circuitState = CircuitState.OPEN;
            circuitOpenUntil.set(System.currentTimeMillis() + properties.getCircuitOpenMs());
            log.error("Agent circuit re-opened during half-open probe, op={}", operation);
            return;
        }
        int fails = consecutiveFailures.incrementAndGet();
        if (fails >= properties.getCircuitFailureThreshold()) {
            circuitState = CircuitState.OPEN;
            circuitOpenUntil.set(System.currentTimeMillis() + properties.getCircuitOpenMs());
            log.error("Agent circuit opened after {} failures, op={}", fails, operation);
        }
    }

    private boolean isRetryable(RestClientException e) {
        return e instanceof ResourceAccessException
                || (e.getMessage() != null && (e.getMessage().contains("503") || e.getMessage().contains("502")));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
