package com.credit.credit.mq.service;

import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.credit.mq.producer.CreditApprovalTaskProducer;
import com.credit.credit.mq.trigger.MqCreditApprovalTaskTrigger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * MQ 消息补偿重投递：用于 MQ_SEND_FAILED / FAILED 任务的人工补偿。
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
    private CreditApprovalTaskProducer creditApprovalTaskProducer;

    public boolean redeliver(Long taskId) {
        CreditAsyncTask task = creditAsyncTaskMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        if (!REDELIVERABLE.contains(task.getStatus())) {
            log.warn("[mq-redelivery] task status not redeliverable taskId={} status={}", taskId, task.getStatus());
            return false;
        }
        boolean sent = creditApprovalTaskProducer.send(task);
        if (sent) {
            task.setStatus(CreditAsyncTask.MQ_SENT);
            task.setErrorMsg(null);
            creditAsyncTaskMapper.updateById(task);
            log.info("[mq-redelivery] success taskId={} workflowId={}", taskId, task.getWorkflowId());
            return true;
        }
        task.setStatus(CreditAsyncTask.MQ_SEND_FAILED);
        task.setErrorMsg("RocketMQ 补偿投递失败");
        creditAsyncTaskMapper.updateById(task);
        return false;
    }
}
