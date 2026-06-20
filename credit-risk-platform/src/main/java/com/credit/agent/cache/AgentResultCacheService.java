package com.credit.agent.cache;

import com.credit.agent.config.AgentCacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
public class AgentResultCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final AgentCacheProperties cacheProperties;

    public AgentResultCacheService(StringRedisTemplate stringRedisTemplate,
                                   AgentCacheProperties cacheProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheProperties = cacheProperties;
    }

    public String getLlmResult(int promptVersion, String inputHash) {
        if (!cacheProperties.isEnabled() || !StringUtils.hasText(inputHash)) {
            return null;
        }
        return stringRedisTemplate.opsForValue().get(llmKey(promptVersion, inputHash));
    }

    public void setLlmResult(int promptVersion, String inputHash, String content, Long ttlHours) {
        if (!cacheProperties.isEnabled() || !StringUtils.hasText(inputHash) || content == null) {
            return;
        }
        long ttl = ttlHours != null ? ttlHours : cacheProperties.getLlmTtlHours();
        stringRedisTemplate.opsForValue().set(
                llmKey(promptVersion, inputHash),
                content,
                ttl,
                TimeUnit.HOURS);
    }

    public String getOcrResult(String fileMd5) {
        if (!cacheProperties.isEnabled() || !StringUtils.hasText(fileMd5)) {
            return null;
        }
        return stringRedisTemplate.opsForValue().get(ocrKey(fileMd5));
    }

    public void setOcrResult(String fileMd5, String content, Long ttlHours) {
        if (!cacheProperties.isEnabled() || !StringUtils.hasText(fileMd5) || content == null) {
            return;
        }
        long ttl = ttlHours != null ? ttlHours : cacheProperties.getOcrTtlHours();
        stringRedisTemplate.opsForValue().set(
                ocrKey(fileMd5),
                content,
                ttl,
                TimeUnit.HOURS);
    }

    static String llmKey(int promptVersion, String inputHash) {
        return AgentCacheConstants.LLM_CACHE_PREFIX + promptVersion + ":" + inputHash;
    }

    static String ocrKey(String fileMd5) {
        return AgentCacheConstants.OCR_CACHE_PREFIX + fileMd5;
    }
}
