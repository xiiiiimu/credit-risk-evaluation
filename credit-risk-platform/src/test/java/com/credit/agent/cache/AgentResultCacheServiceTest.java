package com.credit.agent.cache;

import com.credit.agent.config.AgentCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentResultCacheServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AgentResultCacheService cacheService;

    @BeforeEach
    void setUp() {
        AgentCacheProperties properties = new AgentCacheProperties();
        properties.setEnabled(true);
        properties.setLlmTtlHours(24);
        properties.setOcrTtlHours(72);
        cacheService = new AgentResultCacheService(stringRedisTemplate, properties);
    }

    @Test
    void getLlmResult_readsRedisKey() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("llm:result:2:abc123")).thenReturn("cached-json");

        String result = cacheService.getLlmResult(2, "abc123");

        assertEquals("cached-json", result);
    }

    @Test
    void setLlmResult_writesRedisKeyWithTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService.setLlmResult(1, "hash", "{\"ok\":true}", null);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), eq("{\"ok\":true}"), eq(24L), eq(TimeUnit.HOURS));
        assertEquals("llm:result:1:hash", keyCaptor.getValue());
    }

    @Test
    void getOcrResult_disabledWhenCacheOff() {
        AgentCacheProperties properties = new AgentCacheProperties();
        properties.setEnabled(false);
        AgentResultCacheService disabled = new AgentResultCacheService(stringRedisTemplate, properties);

        assertNull(disabled.getOcrResult("md5"));
    }
}