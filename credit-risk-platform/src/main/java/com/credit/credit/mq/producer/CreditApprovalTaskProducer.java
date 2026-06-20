package com.credit.credit.mq.producer;

import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mq.audit.CreditApprovalMqAuditService;
import com.credit.credit.mq.config.RocketMqApprovalProperties;
import com.credit.credit.mq.message.CreditApprovalTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
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
        if (task == null || task.getId() == null) {
            return false;
        }
        CreditApprovalTaskMessage message = toMessage(task);
        long start = System.currentTimeMillis();
        try {
            SendResult result = rocketMQTemplate.syncSend(
                    properties.destination(),
                    MessageBuilder.withPayload(message).build(),
                    properties.getSendTimeoutMs(),
                    properties.getProducerRetryTimes());
            long cost = System.currentTimeMillis() - start;
            mqAuditService.record(
                    CreditApprovalMqAuditService.EVENT_MQ_SEND_SUCCESS,
                    message,
                    true,
                    cost,
                    null,
                    buildSendExtra(result));
            log.info("[mq-producer] sent taskId={} workflowId={} msgId={}",
                    task.getId(), task.getWorkflowId(), result.getMsgId());
            return true;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            mqAuditService.record(
                    CreditApprovalMqAuditService.EVENT_MQ_SEND_FAILED,
                    message,
                    false,
                    cost,
                    e.getMessage(),
                    null);
            log.error("MQ send failed, taskId={}, workflowId={}, topic={}, tag={}",
                    task.getId(), task.getWorkflowId(), properties.getTopic(), properties.getTag(), e);
            return false;
        }
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
        }
        extra.put("destination", properties.destination());
        return extra;
    }
}
