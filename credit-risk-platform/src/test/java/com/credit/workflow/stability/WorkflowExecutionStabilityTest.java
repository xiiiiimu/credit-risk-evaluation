package com.credit.workflow.stability;

import com.credit.workflow.dto.WorkflowAcquireResult;
import com.credit.workflow.dto.WorkflowIdempotentResult;
import com.credit.workflow.enums.WorkflowIdempotentAction;
import com.credit.workflow.enums.WorkflowStatus;
import com.credit.workflow.entity.WorkflowRecord;
import com.credit.workflow.lock.WorkflowLockService;
import com.credit.workflow.service.WorkflowExecutionService;
import com.credit.workflow.service.WorkflowIdempotencyService;
import com.credit.workflow.service.WorkflowPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 稳定性：Workflow 幂等、分布式锁与 CAS 抢占组合行为。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowExecutionStabilityTest {

    @Mock
    private WorkflowIdempotencyService workflowIdempotencyService;
    @Mock
    private WorkflowPersistenceService workflowPersistenceService;
    @Mock
    private WorkflowLockService workflowLockService;

    @InjectMocks
    private WorkflowExecutionService workflowExecutionService;

    @BeforeEach
    void stubInitWorkflow() {
        WorkflowRecord record = new WorkflowRecord();
        lenient().when(workflowPersistenceService.initWorkflowIfAbsent(any(), any(), any(), any()))
                .thenReturn(record);
    }

    @Test
    void stability_duplicateWorkflowId_returnsCachedResultWithoutLock() {
        WorkflowIdempotentResult idem = new WorkflowIdempotentResult();
        idem.setAction(WorkflowIdempotentAction.RETURN_RESULT);
        idem.setResultJson("{\"agentSuggestion\":\"SUGGEST_APPROVE\"}");
        when(workflowIdempotencyService.resolve("wf-dup")).thenReturn(idem);

        WorkflowAcquireResult result = workflowExecutionService.acquireForExecution(
                "wf-dup", "trace-1", 1L, 10L, "worker-a");

        assertEquals(WorkflowIdempotentAction.RETURN_RESULT, result.getIdempotentAction());
        assertFalse(result.isAcquired());
        verify(workflowLockService, never()).tryLock(any(), any());
    }

    @Test
    void stability_lockExpired_secondWorkerCanAcquireAfterFirstReleased() {
        WorkflowIdempotentResult run = new WorkflowIdempotentResult();
        run.setAction(WorkflowIdempotentAction.RUN);
        when(workflowIdempotencyService.resolve("wf-lock")).thenReturn(run);
        when(workflowPersistenceService.tryAcquireRunning("wf-lock")).thenReturn(true);
        when(workflowLockService.tryLock(eq("wf-lock"), eq("worker-a"))).thenReturn(true);

        WorkflowAcquireResult first = workflowExecutionService.acquireForExecution(
                "wf-lock", "trace-1", 1L, 10L, "worker-a");
        assertTrue(first.isAcquired());

        when(workflowLockService.tryLock(eq("wf-lock"), eq("worker-b"))).thenReturn(false);
        WorkflowAcquireResult second = workflowExecutionService.acquireForExecution(
                "wf-lock", "trace-1", 2L, 10L, "worker-b");
        assertFalse(second.isAcquired());
        assertEquals(WorkflowIdempotentAction.WAIT, second.getIdempotentAction());

        when(workflowLockService.tryLock(eq("wf-lock"), eq("worker-b"))).thenReturn(true);
        when(workflowPersistenceService.tryAcquireRunning("wf-lock")).thenReturn(true);
        WorkflowAcquireResult third = workflowExecutionService.acquireForExecution(
                "wf-lock", "trace-1", 2L, 10L, "worker-b");
        assertTrue(third.isAcquired());
    }

    @Test
    void stability_casFailed_releasesLockAndReturnsWaitOrCached() {
        WorkflowIdempotentResult run = new WorkflowIdempotentResult();
        run.setAction(WorkflowIdempotentAction.RUN);
        WorkflowIdempotentResult running = new WorkflowIdempotentResult();
        running.setAction(WorkflowIdempotentAction.WAIT);
        running.setCurrentNode("credit_assessment");
        running.setStatus(WorkflowStatus.RUNNING);

        when(workflowIdempotencyService.resolve("wf-cas")).thenReturn(run, running);
        when(workflowLockService.tryLock(eq("wf-cas"), any())).thenReturn(true);
        when(workflowPersistenceService.tryAcquireRunning("wf-cas")).thenReturn(false);

        WorkflowAcquireResult result = workflowExecutionService.acquireForExecution(
                "wf-cas", "trace-1", 1L, 10L, "worker-a");

        assertFalse(result.isAcquired());
        assertEquals(WorkflowIdempotentAction.WAIT, result.getIdempotentAction());
        verify(workflowLockService).unlock("wf-cas");
    }
}
