package com.credit.credit.mq.service;

import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.credit.mq.outbox.MqOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * MQ 消息补偿重投递：重置 outbox 事件，由 {@link com.credit.credit.mq.outbox.MqOutboxPublisher} 统一发送。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "credit.mq.enabled", havingValue = "true", matchIfMissing = true)
public class CreditApprovalTaskRedeliveryService {

    private static final Set<String> REDELIVERABLE = new HashSet<>(Arrays.asList(
            CreditAsyncTask.MQ_SEND_FAILED,
            CreditAsyncTask.FAILED,
            CreditAsyncTask.MQ_SENT,
            CreditAsyncTask.PENDING
    ));

    @Resource
    private CreditAsyncTaskMapper creditAsyncTaskMapper;
    @Resource
    private MqOutboxService mqOutboxService;

    public boolean redeliver(Long taskId) {
        CreditAsyncTask task = creditAsyncTaskMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        if (!REDELIVERABLE.contains(task.getStatus())) {
            log.warn("[mq-redelivery] task status not redeliverable taskId={} status={}", taskId, task.getStatus());
            return false;
        }
        boolean reset = mqOutboxService.resetForRedelivery(taskId);
        if (!reset) {
            log.warn("[mq-redelivery] outbox event not found taskId={}", taskId);
            return false;
        }
        task.setStatus(CreditAsyncTask.PENDING);
        task.setErrorMsg(null);
        creditAsyncTaskMapper.updateById(task);
        log.info("[mq-redelivery] outbox reset taskId={} workflowId={}", taskId, task.getWorkflowId());
        return true;
    }
}
