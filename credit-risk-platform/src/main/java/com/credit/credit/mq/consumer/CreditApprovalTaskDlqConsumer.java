package com.credit.credit.mq.consumer;

import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.credit.mq.audit.CreditApprovalMqAuditService;
import com.credit.credit.mq.message.CreditApprovalTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 死信队列消费者：超过最大重试后标记任务失败/人工复核，并记录审计。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "credit.mq.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${credit.mq.dlq-topic:%DLQ%credit-approval-task-consumer}",
        consumerGroup = "${credit.mq.dlq-consumer-group:credit-approval-task-dlq-consumer}"
)
public class CreditApprovalTaskDlqConsumer implements RocketMQListener<CreditApprovalTaskMessage> {

    @Resource
    private CreditAsyncTaskMapper creditAsyncTaskMapper;
    @Resource
    private CreditApprovalMqAuditService mqAuditService;

    @Override
    public void onMessage(CreditApprovalTaskMessage message) {
        if (message == null || message.getTaskId() == null) {
            return;
        }
        Map<String, Object> extra = new HashMap<>();
        extra.put("dlq", true);
        mqAuditService.record(
                CreditApprovalMqAuditService.EVENT_MQ_DEAD_LETTER,
                message,
                false,
                0,
                "exceeded max reconsume times",
                extra);

        CreditAsyncTask task = creditAsyncTaskMapper.selectById(message.getTaskId());
        if (task == null) {
            log.warn("[mq-dlq] task not found taskId={}", message.getTaskId());
            return;
        }
        if (CreditAsyncTask.SUCCESS.equals(task.getStatus())
                || CreditAsyncTask.MANUAL_REVIEW.equals(task.getStatus())) {
            log.info("[mq-dlq] skip terminal taskId={} status={}", task.getId(), task.getStatus());
            return;
        }
        task.setStatus(CreditAsyncTask.MANUAL_REVIEW);
        task.setErrorMsg("MQ 消费超过最大重试次数，转人工复核");
        creditAsyncTaskMapper.updateById(task);
        log.error("[mq-dlq] task moved to MANUAL_REVIEW taskId={} workflowId={}",
                task.getId(), task.getWorkflowId());
    }
}
