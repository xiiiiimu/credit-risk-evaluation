package com.credit.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class AgentStartupLogger {

    @Resource
    private AgentProperties agentProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void logAgentConfig() {
        String key = agentProperties.getInternalApiKey();
        String masked = key == null ? "null" : (key.length() <= 4 ? "****" : key.substring(0, 2) + "****");
        log.info("Agent internal-api-key loaded (prefix): {}, python base-url: {}",
                masked, agentProperties.getBaseUrl());
        log.info("Use same INTERNAL_API_KEY in Python .env; httpx trust_env=False");
    }
}
