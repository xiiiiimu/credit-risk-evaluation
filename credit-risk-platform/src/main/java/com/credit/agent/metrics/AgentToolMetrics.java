package com.credit.agent.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class AgentToolMetrics {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    public AgentToolMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordTool(String tool, boolean success, long costMs) {
        meterRegistry.counter("agent.tool.invoke", "tool", tool, "success", String.valueOf(success)).increment();
        timer(tool).record(costMs, TimeUnit.MILLISECONDS);
    }

    public void recordAgentCall(String agent, boolean success, long costMs) {
        meterRegistry.counter("agent.remote.call", "agent", agent, "success", String.valueOf(success)).increment();
        timer("remote." + agent).record(costMs, TimeUnit.MILLISECONDS);
    }

    private Timer timer(String name) {
        return timers.computeIfAbsent(name, n ->
                Timer.builder("agent.tool.duration")
                        .tag("name", n)
                        .register(meterRegistry));
    }
}
