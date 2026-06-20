package com.credit.credit.mq.producer;

import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mq.audit.CreditApprovalMqAuditService;
import com.credit.credit.mq.config.RocketMqApprovalProperties;
import com.credit.credit.mq.message.CreditApprovalTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Slf4j
@Service
@ConditionalOnProperty(name = "credit.mq.enabled", havingValue = "true", matchIfMissing = true)
public class CreditApprovalTaskProducer {

    @Resource
    private RocketMQTemplate rocketMQTemplate;
    @Resource
    private RocketMqApprovalProperties properties;
    @Resource
    private CreditApprovalMqAuditService mqAuditService;

    public boolean send(CreditAsyncTask task) {
        long totalStart = System.nanoTime();
        Long taskId = task != null ? task.getId() : null;
        String workflowId = task != null ? task.getWorkflowId() : null;
        try {
            if (task == null || task.getId() == null) {
                return false;
            }
            long buildStart = System.nanoTime();
            CreditApprovalTaskMessage message = toMessage(task);
            log.info("[PERF][submit] taskId={} workflowId={} stage=messageBuild cost={}ms",
                    taskId, workflowId, elapsedMs(buildStart));

            long syncSendStart = System.nanoTime();
            try {
                SendResult result = rocketMQTemplate.syncSend(
                        properties.destination(),
                        MessageBuilder.withPayload(message).build(),
                        properties.getSendTimeoutMs(),
                        properties.getProducerRetryTimes());
                long syncSendCost = elapsedMs(syncSendStart);
                logSendResult(taskId, workflowId, result, syncSendCost);

                mqAuditService.record(
                        CreditApprovalMqAuditService.EVENT_MQ_SEND_SUCCESS,
                        message,
                        true,
                        syncSendCost,
                        null,
                        buildSendExtra(result));
                log.info("[mq-producer] sent taskId={} workflowId={} msgId={}",
                        task.getId(), task.getWorkflowId(), result.getMsgId());
                return true;
            } catch (Exception e) {
                long syncSendCost = elapsedMs(syncSendStart);
                log.info("[PERF][submit] taskId={} workflowId={} stage=syncSend cost={}ms success=false",
                        taskId, workflowId, syncSendCost);
                mqAuditService.record(
                        CreditApprovalMqAuditService.EVENT_MQ_SEND_FAILED,
                        message,
                        false,
                        syncSendCost,
                        e.getMessage(),
                        null);
                log.error("MQ send failed, taskId={}, workflowId={}, topic={}, tag={}",
                        task.getId(), task.getWorkflowId(), properties.getTopic(), properties.getTag(), e);
                return false;
            }
        } finally {
            log.info("[PERF][submit] taskId={} workflowId={} stage=producer.send.total cost={}ms",
                    taskId, workflowId, elapsedMs(totalStart));
        }
    }

    private void logSendResult(Long taskId, String workflowId, SendResult result, long syncSendCost) {
        String sendStatus = result != null && result.getSendStatus() != null
                ? result.getSendStatus().name() : null;
        String msgId = result != null ? result.getMsgId() : null;
        String brokerName = null;
        Integer queueId = null;
        if (result != null && result.getMessageQueue() != null) {
            MessageQueue queue = result.getMessageQueue();
            brokerName = queue.getBrokerName();
            queueId = queue.getQueueId();
        }
        log.info("[PERF][submit] taskId={} workflowId={} stage=syncSend cost={}ms sendStatus={} msgId={} broker={} queueId={}",
                taskId, workflowId, syncSendCost, sendStatus, msgId, brokerName, queueId);
    }

    public CreditApprovalTaskMessage toMessage(CreditAsyncTask task) {
        return CreditApprovalTaskMessage.builder()
                .taskId(task.getId())
                .workflowId(task.getWorkflowId())
                .applicationId(task.getApplicationId())
                .userId(task.getUserId())
                .productId(task.getProductId())
                .idempotencyKey(task.getIdempotencyKey())
                .traceId(task.getTraceId())
                .createdAt(task.getCreateTime() != null ? task.getCreateTime() : LocalDateTime.now())
                .build();
    }

    private java.util.Map<String, Object> buildSendExtra(SendResult result) {
        java.util.Map<String, Object> extra = new java.util.HashMap<>();
        if (result != null) {
            extra.put("msgId", result.getMsgId());
            extra.put("sendStatus", result.getSendStatus() != null ? result.getSendStatus().name() : null);
            if (result.getMessageQueue() != null) {
                extra.put("brokerName", result.getMessageQueue().getBrokerName());
                extra.put("queueId", result.getMessageQueue().getQueueId());
            }
        }
        extra.put("destination", properties.destination());
        return extra;
    }

    private static long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }
}
