package com.credit.credit.e2e;

import com.credit.agent.facade.AgentRemoteClient;
import com.credit.agent.health.AgentUnavailableException;
import com.credit.credit.entity.CreditApplication;
import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.enums.ApplicationStatus;
import com.credit.credit.mapper.CreditApplicationMapper;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.credit.service.CreditApplyAsyncProcessor;
import com.credit.credit.service.CreditApprovalTaskGuardService;
import com.credit.credit.service.CreditDraftApplicationService;
import com.credit.credit.trace.CreditWorkflowTraceService;
import com.credit.credit.workflow.CreditApplyWorkflowService;
import com.credit.workflow.dto.WorkflowAcquireResult;
import com.credit.workflow.enums.WorkflowIdempotentAction;
import com.credit.workflow.service.WorkflowExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 稳定性：Agent 不可用时的异步任务降级路径。
 */
@ExtendWith(MockitoExtension.class)
class CreditApplyAsyncDegradationStabilityTest {

    @Mock
    private CreditAsyncTaskMapper creditAsyncTaskMapper;
    @Mock
    private AgentRemoteClient agentRemoteClient;
    @Mock
    private CreditApplyWorkflowService creditApplyWorkflowService;
    @Mock
    private CreditDraftApplicationService creditDraftApplicationService;
    @Mock
    private CreditWorkflowTraceService creditWorkflowTraceService;
    @Mock
    private WorkflowExecutionService workflowExecutionService;
    @Mock
    private CreditApplicationMapper creditApplicationMapper;
    @Mock
    private CreditApprovalTaskGuardService taskGuardService;

    @InjectMocks
    private CreditApplyAsyncProcessor processor;

    private CreditAsyncTask task;

    @BeforeEach
    void setUp() {
        task = new CreditAsyncTask();
        task.setId(2L);
        task.setUserId(1001L);
        task.setProductId(1L);
        task.setApplyAmount(new BigDecimal("20000"));
        task.setApplyTerm(12);
        task.setWorkflowId("wf-agent-down");
        task.setTraceId("trace-down");

        CreditApplication draft = new CreditApplication();
        draft.setId(901L);
        draft.setStatus(ApplicationStatus.MANUAL_REVIEW);

        when(creditAsyncTaskMapper.selectById(2L)).thenReturn(task);
        when(taskGuardService.isTerminal(any())).thenReturn(false);
        when(creditDraftApplicationService.createDraft(task)).thenReturn(draft);

        WorkflowAcquireResult acquire = new WorkflowAcquireResult();
        acquire.setIdempotentAction(WorkflowIdempotentAction.RUN);
        acquire.setAcquired(true);
        when(workflowExecutionService.acquireForExecution(any(), any(), any(), any(), any())).thenReturn(acquire);
        when(agentRemoteClient.analyzeCreditApplication(any(), any()))
                .thenThrow(new AgentUnavailableException("circuit open"));
        when(creditApplyWorkflowService.commit(eq(task), any())).thenReturn(901L);
        when(creditApplicationMapper.selectById(901L)).thenReturn(draft);
    }

    @Test
    void stability_agentUnavailable_degradesToManualReviewCommit() {
        processor.run(2L);

        verify(creditApplyWorkflowService).commit(eq(task), any());
        verify(agentRemoteClient).analyzeCreditApplication(any(), any());
        assertEquals(CreditAsyncTask.MANUAL_REVIEW, task.getStatus());
    }
}
