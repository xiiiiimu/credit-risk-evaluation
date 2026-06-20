package com.credit.workflow.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowLockServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private WorkflowLockService workflowLockService;

    @Test
    void tryLock_onlyOneWorkerSucceeds() {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(), any()))
                .thenReturn(1L, 0L);

        assertTrue(workflowLockService.tryLock("wf-1", "worker-a"));
        assertFalse(workflowLockService.tryLock("wf-1", "worker-b"));
    }

    @Test
    void lockExpired_anotherWorkerCanAcquireAfterUnlock() {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(), any()))
                .thenReturn(1L, 0L, 1L);

        assertTrue(workflowLockService.tryLock("wf-expire", "worker-a"));
        assertFalse(workflowLockService.tryLock("wf-expire", "worker-b"));
        workflowLockService.unlock("wf-expire");
        assertTrue(workflowLockService.tryLock("wf-expire", "worker-b"));
    }

    @Test
    void unlock_deletesKey() {
        workflowLockService.unlock("wf-2");
        verify(stringRedisTemplate).delete(WorkflowLockConstants.LOCK_PREFIX + "wf-2");
    }
}
