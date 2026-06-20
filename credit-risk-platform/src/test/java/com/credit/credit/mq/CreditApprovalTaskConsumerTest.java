package com.credit.credit.mq;

import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.credit.mq.audit.CreditApprovalMqAuditService;
import com.credit.credit.mq.consumer.CreditApprovalTaskConsumer;
import com.credit.credit.mq.message.CreditApprovalTaskMessage;
import com.credit.credit.service.CreditApplyAsyncProcessor;
import com.credit.credit.service.CreditApprovalTaskGuardService;
import com.credit.workflow.dto.WorkflowIdempotentResult;
import com.credit.workflow.enums.WorkflowIdempotentAction;
import com.credit.workflow.service.WorkflowIdempotencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditApprovalTaskConsumerTest {

    @Mock
    private CreditAsyncTaskMapper creditAsyncTaskMapper;
    @Mock
    private CreditApplyAsyncProcessor creditApplyAsyncProcessor;
    @Mock
    private CreditApprovalMqAuditService mqAuditService;
    @Mock
    private CreditApprovalTaskGuardService taskGuardService;
    @Mock
    private WorkflowIdempotencyService workflowIdempotencyService;

    @InjectMocks
    private CreditApprovalTaskConsumer consumer;

    @Test
    void onMessage_skipTerminalTask() {
        CreditApprovalTaskMessage message = CreditApprovalTaskMessage.builder()
                .taskId(1L)
                .workflowId("wf-1")
                .traceId("t-1")
                .build();
        CreditAsyncTask task = new CreditAsyncTask();
        task.setId(1L);
        task.setStatus(CreditAsyncTask.SUCCESS);
        when(creditAsyncTaskMapper.selectById(1L)).thenReturn(task);
        when(taskGuardService.isTerminal(CreditAsyncTask.SUCCESS)).thenReturn(true);

        consumer.onMessage(message);

        verify(creditApplyAsyncProcessor, never()).processTask(anyLong());
        verify(mqAuditService).record(
                eq(CreditApprovalMqAuditService.EVENT_MQ_CONSUME_SKIP),
                eq(message), eq(true), anyLong(), anyString(), any());
    }

    @Test
    void onMessage_processTask() {
        CreditApprovalTaskMessage message = CreditApprovalTaskMessage.builder()
                .taskId(2L)
                .workflowId("wf-2")
                .traceId("t-2")
                .build();
        CreditAsyncTask task = new CreditAsyncTask();
        task.setId(2L);
        task.setWorkflowId("wf-2");
        task.setStatus(CreditAsyncTask.MQ_SENT);
        when(creditAsyncTaskMapper.selectById(2L)).thenReturn(task);
        when(taskGuardService.isTerminal(CreditAsyncTask.MQ_SENT)).thenReturn(false);
        when(creditApplyAsyncProcessor.processTask(2L)).thenReturn(true);

        consumer.onMessage(message);

        verify(creditApplyAsyncProcessor).processTask(2L);
        verify(mqAuditService).record(
                eq(CreditApprovalMqAuditService.EVENT_MQ_CONSUME_SUCCESS),
                eq(message), eq(true), anyLong(), any(), any());
    }

    @Test
    void onMessage_skipInFlightDuplicate() {
        CreditApprovalTaskMessage message = CreditApprovalTaskMessage.builder()
                .taskId(3L)
                .workflowId("wf-3")
                .traceId("t-3")
                .build();
        CreditAsyncTask task = new CreditAsyncTask();
        task.setId(3L);
        task.setWorkflowId("wf-3");
        task.setStatus(CreditAsyncTask.RUNNING);
        when(creditAsyncTaskMapper.selectById(3L)).thenReturn(task);
        when(taskGuardService.isTerminal(CreditAsyncTask.RUNNING)).thenReturn(false);

        WorkflowIdempotentResult idem = new WorkflowIdempotentResult();
        idem.setAction(WorkflowIdempotentAction.WAIT);
        when(workflowIdempotencyService.resolve("wf-3")).thenReturn(idem);

        consumer.onMessage(message);

        verify(creditApplyAsyncProcessor, never()).processTask(anyLong());
        verify(mqAuditService).record(
                eq(CreditApprovalMqAuditService.EVENT_MQ_CONSUME_SKIP),
                eq(message), eq(true), anyLong(),
                eq("workflow in progress duplicate taskId=3"), any());
        verify(mqAuditService, never()).recordRetry(any(), any(Integer.class), anyString());
    }

    @Test
    void onMessage_skipProcessorInProgressDuplicate() {
        CreditApprovalTaskMessage message = CreditApprovalTaskMessage.builder()
                .taskId(4L)
                .workflowId("wf-4")
                .traceId("t-4")
                .build();
        CreditAsyncTask task = new CreditAsyncTask();
        task.setId(4L);
        task.setWorkflowId("wf-4");
        task.setStatus(CreditAsyncTask.MQ_SENT);
        when(creditAsyncTaskMapper.selectById(4L)).thenReturn(task);
        when(taskGuardService.isTerminal(CreditAsyncTask.MQ_SENT)).thenReturn(false);
        when(creditApplyAsyncProcessor.processTask(4L)).thenReturn(false);

        consumer.onMessage(message);

        verify(creditApplyAsyncProcessor).processTask(4L);
        verify(mqAuditService).record(
                eq(CreditApprovalMqAuditService.EVENT_MQ_CONSUME_SKIP),
                eq(message), eq(true), anyLong(),
                eq("workflow in progress duplicate during process taskId=4"), any());
        verify(mqAuditService, never()).record(
                eq(CreditApprovalMqAuditService.EVENT_MQ_CONSUME_SUCCESS),
                eq(message), eq(true), anyLong(), isNull(), isNull());
    }
}
