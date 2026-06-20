package com.credit.credit.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreditCacheServiceEvictTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private com.credit.common.util.CacheClient cacheClient;

    @InjectMocks
    private CreditCacheService creditCacheService;

    @Test
    void evictApplication_deletesApplicationAndWorkflowKeys() {
        creditCacheService.evictApplication(10L, 1L);
        creditCacheService.evictWorkflow("wf-1");
        creditCacheService.evictRiskResult("wf-1");

        verify(stringRedisTemplate, atLeastOnce()).delete(CreditCacheService.KEY_APPLICATION + "10");
        verify(stringRedisTemplate).delete(CreditCacheService.KEY_APPLY_MINE + "1");
        verify(stringRedisTemplate).delete(CreditCacheService.KEY_WORKFLOW + "wf-1");
        verify(stringRedisTemplate).delete(CreditCacheService.KEY_RISK_RESULT + "wf-1");
    }
}
