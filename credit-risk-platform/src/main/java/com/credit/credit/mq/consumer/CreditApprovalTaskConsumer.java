package com.credit.credit.mq.consumer;

import com.credit.agent.config.TraceIdInterceptor;
import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.credit.mq.audit.CreditApprovalMqAuditService;
import com.credit.credit.mq.exception.CreditApprovalTaskRetryableException;
import com.credit.credit.mq.message.CreditApprovalTaskMessage;
import com.credit.credit.service.CreditApplyAsyncProcessor;
import com.credit.credit.service.CreditApprovalTaskGuardService;
import com.credit.workflow.dto.WorkflowIdempotentResult;
import com.credit.workflow.enums.WorkflowIdempotentAction;
import com.credit.workflow.enums.WorkflowStatus;
import com.credit.workflow.service.WorkflowIdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
@ConditionalOnProperty(name = "credit.mq.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${credit.mq.topic}",
        consumerGroup = "${credit.mq.consumer-group}",
        selectorExpression = "${credit.mq.tag}",
        consumeThreadMax = 16,
        maxReconsumeTimes = 16
)
public class CreditApprovalTaskConsumer implements RocketMQListener<CreditApprovalTaskMessage> {

    @Resource
    private CreditAsyncTaskMapper creditAsyncTaskMapper;
    @Resource
    private CreditApplyAsyncProcessor creditApplyAsyncProcessor;
    @Resource
    private CreditApprovalMqAuditService mqAuditService;
    @Resource
    private CreditApprovalTaskGuardService taskGuardService;
    @Resource
    private WorkflowIdempotencyService workflowIdempotencyService;

    @Override
    public void onMessage(CreditApprovalTaskMessage message) {
        long start = System.currentTimeMillis();
        if (message == null || message.getTaskId() == null) {
            log.warn("[mq-consumer] ignore empty message");
            return;
        }
        if (message.getTraceId() != null) {
            MDC.put(TraceIdInterceptor.MDC_KEY, message.getTraceId());
        }
        mqAuditService.record(
                CreditApprovalMqAuditService.EVENT_MQ_CONSUME_START,
                message,
                true,
                0,
                null,
                null);
        try {
            CreditAsyncTask task = creditAsyncTaskMapper.selectById(message.getTaskId());
            if (task == null) {
                log.warn("[mq-consumer] task not found taskId={}", message.getTaskId());
                mqAuditService.record(
                        CreditApprovalMqAuditService.EVENT_MQ_CONSUME_FAILED,
                        message,
                        false,
                        System.currentTimeMillis() - start,
                        "task not found",
                        null);
                return;
            }
            if (taskGuardService.isTerminal(task.getStatus())) {
                mqAuditService.record(
                        CreditApprovalMqAuditService.EVENT_MQ_CONSUME_SKIP,
                        message,
                        true,
                        System.currentTimeMillis() - start,
                        "terminal task status=" + task.getStatus(),
                        null);
                log.info("[mq-consumer] skip terminal taskId={} status={}", task.getId(), task.getStatus());
                return;
            }
            if (shouldSkipInFlightDuplicate(message, task)) {
                throw new CreditApprovalTaskRetryableException(
                        "workflow in progress, wait for retry taskId=" + task.getId());
            }
            creditApplyAsyncProcessor.processTask(task.getId());
            mqAuditService.record(
                    CreditApprovalMqAuditService.EVENT_MQ_CONSUME_SUCCESS,
                    message,
                    true,
                    System.currentTimeMillis() - start,
                    null,
                    null);
        } catch (CreditApprovalTaskRetryableException e) {
            mqAuditService.recordRetry(message, estimateReconsumeTimes(message), e.getMessage());
            mqAuditService.record(
                    CreditApprovalMqAuditService.EVENT_MQ_CONSUME_FAILED,
                    message,
                    false,
                    System.currentTimeMillis() - start,
                    e.getMessage(),
                    null);
            throw e;
        } catch (Exception e) {
            mqAuditService.record(
                    CreditApprovalMqAuditService.EVENT_MQ_CONSUME_FAILED,
                    message,
                    false,
                    System.currentTimeMillis() - start,
                    e.getMessage(),
                    null);
            if (isRetryable(e)) {
                throw new CreditApprovalTaskRetryableException(e.getMessage(), e);
            }
            log.error("[mq-consumer] non-retryable failure taskId={}", message.getTaskId(), e);
        } finally {
            MDC.remove(TraceIdInterceptor.MDC_KEY);
        }
    }

    private boolean shouldSkipInFlightDuplicate(CreditApprovalTaskMessage message, CreditAsyncTask task) {
        if (!CreditAsyncTask.RUNNING.equals(task.getStatus())) {
            return false;
        }
        String workflowId = task.getWorkflowId() != null ? task.getWorkflowId() : message.getWorkflowId();
        WorkflowIdempotentResult idem = workflowIdempotencyService.resolve(workflowId);
        if (WorkflowIdempotentAction.RETURN_RESULT.equals(idem.getAction())) {
            return false;
        }
        if (WorkflowIdempotentAction.WAIT.equals(idem.getAction())
                || WorkflowStatus.RUNNING.equals(idem.getStatus())) {
            log.info("[mq-consumer] duplicate delivery while running taskId={} workflowId={}",
                    task.getId(), workflowId);
            return true;
        }
        return false;
    }

    private boolean isRetryable(Exception e) {
        return e instanceof CreditApprovalTaskRetryableException
                || e instanceof IllegalStateException;
    }

    private int estimateReconsumeTimes(CreditApprovalTaskMessage message) {
        return message.getTaskId() != null ? message.getTaskId().intValue() % 16 : 0;
    }
}
