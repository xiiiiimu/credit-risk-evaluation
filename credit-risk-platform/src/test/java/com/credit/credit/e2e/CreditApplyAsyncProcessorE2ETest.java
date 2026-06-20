package com.credit.credit.e2e;

import cn.hutool.json.JSONUtil;
import com.credit.agent.dto.CreditAnalysisResponse;
import com.credit.agent.facade.AgentRemoteClient;
import com.credit.credit.entity.CreditApplication;
import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.enums.ApplicationStatus;
import com.credit.credit.mapper.CreditApplicationMapper;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.credit.service.CreditDraftApplicationService;
import com.credit.credit.service.CreditApprovalTaskGuardService;
import com.credit.credit.service.CreditApplyAsyncProcessor;
import com.credit.credit.testsupport.CreditAnalysisFixtures;
import com.credit.credit.trace.CreditWorkflowTraceService;
import com.credit.credit.workflow.CreditApplyWorkflowService;
import com.credit.workflow.dto.WorkflowAcquireResult;
import com.credit.workflow.dto.WorkflowIdempotentResult;
import com.credit.workflow.enums.WorkflowIdempotentAction;
import com.credit.workflow.enums.WorkflowStatus;
import com.credit.workflow.service.WorkflowExecutionService;
import com.credit.workflow.service.WorkflowIdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E2E：异步任务链路（Mock Agent / Workflow），验证幂等与终审提交。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreditApplyAsyncProcessorE2ETest {

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
    private WorkflowIdempotencyService workflowIdempotencyService;
    @Mock
    private CreditApplicationMapper creditApplicationMapper;
    @Mock
    private CreditApprovalTaskGuardService taskGuardService;

    @InjectMocks
    private CreditApplyAsyncProcessor processor;

    private CreditAsyncTask task;
    private CreditApplication draft;

    @BeforeEach
    void setUp() {
        task = new CreditAsyncTask();
        task.setId(1L);
        task.setUserId(1001L);
        task.setProductId(1L);
        task.setApplyAmount(new BigDecimal("30000"));
        task.setApplyTerm(12);
        task.setPurpose("消费");
        task.setContent("幂等测试");
        task.setWorkflowId("wf-idempotent-001");
        task.setTraceId("trace-001");

        draft = new CreditApplication();
        draft.setId(900L);
        draft.setWorkflowId(task.getWorkflowId());

        when(creditAsyncTaskMapper.selectById(1L)).thenReturn(task);
        when(taskGuardService.isTerminal(any())).thenReturn(false);
        when(creditDraftApplicationService.createDraft(task)).thenReturn(draft);
        when(creditApplyWorkflowService.commit(eq(task), any())).thenReturn(900L);
        when(creditApplicationMapper.selectById(900L)).thenReturn(draft);

        WorkflowIdempotentResult freshIdem = new WorkflowIdempotentResult();
        freshIdem.setAction(WorkflowIdempotentAction.RUN);
        freshIdem.setStatus(WorkflowStatus.INIT);
        when(workflowIdempotencyService.resolve(task.getWorkflowId())).thenReturn(freshIdem);
    }

    @Test
    void e2e_duplicateWorkflowId_returnsCachedAgentResultWithoutReAnalyzing() throws Exception {
        CreditAnalysisResponse cached = new CreditAnalysisResponse();
        cached.setWorkflowId(task.getWorkflowId());
        cached.setAgentSuggestion("SUGGEST_APPROVE");
        cached.setConsensusSuggestion("SUGGEST_APPROVE");
        cached.setVerifiedDocuments(true);
        cached.setCreditEligible(true);
        cached.setSummary("cached agent result");

        WorkflowAcquireResult acquire = new WorkflowAcquireResult();
        acquire.setIdempotentAction(WorkflowIdempotentAction.RETURN_RESULT);
        acquire.setCachedResultJson(JSONUtil.toJsonStr(cached));
        acquire.setAcquired(false);
        when(workflowExecutionService.acquireForExecution(
                eq(task.getWorkflowId()), eq(task.getTraceId()), eq(task.getId()), isNull(), any()))
                .thenReturn(acquire);

        processor.run(1L);

        verify(agentRemoteClient, never()).analyzeCreditApplication(any(), any());
        verify(creditApplyWorkflowService).commit(eq(task), any());
        verify(creditDraftApplicationService).createDraft(task);
        assertEquals(CreditAsyncTask.SUCCESS, task.getStatus());
        assertEquals(Long.valueOf(900L), task.getApplicationId());
    }

    @Test
    void e2e_runningNotOwned_skipsAgentBeforeHttpCall() {
        WorkflowIdempotentResult freshIdem = new WorkflowIdempotentResult();
        freshIdem.setAction(WorkflowIdempotentAction.RUN);
        freshIdem.setStatus(WorkflowStatus.INIT);
        WorkflowIdempotentResult runningIdem = new WorkflowIdempotentResult();
        runningIdem.setAction(WorkflowIdempotentAction.RUN);
        runningIdem.setStatus(WorkflowStatus.RUNNING);
        when(workflowIdempotencyService.resolve(task.getWorkflowId()))
                .thenReturn(freshIdem, runningIdem);

        WorkflowAcquireResult acquire = new WorkflowAcquireResult();
        acquire.setIdempotentAction(WorkflowIdempotentAction.RUN);
        acquire.setAcquired(true);
        acquire.setStatus(WorkflowStatus.INIT);
        when(workflowExecutionService.acquireForExecution(
                eq(task.getWorkflowId()), eq(task.getTraceId()), eq(task.getId()), isNull(), any()))
                .thenReturn(acquire);
        when(workflowExecutionService.isLockHeldBy(task.getWorkflowId(), "task-1")).thenReturn(false);

        assertFalse(processor.processTask(1L));

        verify(agentRemoteClient, never()).analyzeCreditApplication(any(), any());
    }

    @Test
    void e2e_waitRunning_skipsAgentWithoutHttpCall() {
        WorkflowIdempotentResult waitIdem = new WorkflowIdempotentResult();
        waitIdem.setAction(WorkflowIdempotentAction.WAIT);
        waitIdem.setStatus(WorkflowStatus.RUNNING);
        when(workflowIdempotencyService.resolve(task.getWorkflowId())).thenReturn(waitIdem);

        boolean processed = processor.processTask(1L);

        assertFalse(processed);
        verify(agentRemoteClient, never()).analyzeCreditApplication(any(), any());
        verify(workflowExecutionService, never()).acquireForExecution(any(), any(), any(), any(), any());
    }

    @Test
    void e2e_freshWorkflow_invokesMockAgentThenCommitsPlatformDecision() throws Exception {
        CreditAnalysisResponse agentResp = new CreditAnalysisResponse();
        agentResp.setWorkflowId(task.getWorkflowId());
        agentResp.setVerifiedDocuments(true);
        agentResp.setCreditEligible(true);
        agentResp.setAgentSuggestion("SUGGEST_APPROVE");
        agentResp.setConsensusSuggestion("SUGGEST_APPROVE");
        agentResp.setRiskLevel("LOW");
        agentResp.setFraudScore(10);
        agentResp.setMinAgentConfidence(new BigDecimal("0.92"));
        agentResp.setSummary(CreditAnalysisFixtures.lowRiskApprovedAgentView().getAiSummary());

        WorkflowAcquireResult acquire = new WorkflowAcquireResult();
        acquire.setIdempotentAction(WorkflowIdempotentAction.RUN);
        acquire.setAcquired(true);
        when(workflowExecutionService.acquireForExecution(
                eq(task.getWorkflowId()), eq(task.getTraceId()), eq(task.getId()), isNull(), any()))
                .thenReturn(acquire);
        when(agentRemoteClient.analyzeCreditApplication(any(), eq(task.getTraceId()))).thenReturn(agentResp);

        processor.run(1L);

        verify(agentRemoteClient).analyzeCreditApplication(any(), eq(task.getTraceId()));
        verify(creditApplyWorkflowService).commit(eq(task), any());
        verify(workflowExecutionService).releaseLock(task.getWorkflowId());
        assertEquals(CreditAsyncTask.SUCCESS, task.getStatus());
    }
}
