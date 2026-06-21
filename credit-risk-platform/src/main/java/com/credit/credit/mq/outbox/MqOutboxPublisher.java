package com.credit.credit.mq.outbox;

import cn.hutool.json.JSONUtil;
import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.credit.mq.audit.CreditApprovalMqAuditService;
import com.credit.credit.mq.config.RocketMqApprovalProperties;
import com.credit.credit.mq.message.CreditApprovalTaskMessage;
import com.credit.credit.mq.producer.CreditApprovalTaskProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "credit.mq.enabled", havingValue = "true", matchIfMissing = true)
/**
 * MySQL Polling Outbox 发送器。
 * <p>
 * SENDING 超时恢复：若 Publisher 在 CAS 为 SENDING 之后、更新 SENT/FAILED 之前宕机，
 * 事件会卡在 SENDING；扫描时会重新捞起超过 2 分钟未更新的 SENDING 事件。
 * 重复发送仍可能发生，因此 Consumer 端终态检查、Workflow 状态检查、Redis Lock 与 MySQL CAS 必须保留。
 */
public class MqOutboxPublisher {

    private static final int BATCH_SIZE = 20;
    private static final int MAX_RETRY = 5;
    private static final List<Long> BACKOFF_SECONDS = Arrays.asList(5L, 30L, 120L, 300L, 300L);

    @Resource
    private MqOutboxEventMapper mqOutboxEventMapper;
    @Resource
    private CreditAsyncTaskMapper creditAsyncTaskMapper;
    @Resource
    private CreditApprovalTaskProducer creditApprovalTaskProducer;
    @Resource
    private CreditApprovalMqAuditService mqAuditService;
    @Resource
    private MqOutboxService mqOutboxService;
    @Resource
    private RocketMqApprovalProperties properties;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        List<MqOutboxEvent> pending = mqOutboxEventMapper.selectPending(BATCH_SIZE);
        for (MqOutboxEvent event : pending) {
            publishOne(event);
        }
    }

    void publishOne(MqOutboxEvent event) {
        if (event == null || event.getId() == null) {
            return;
        }
        int claimed = mqOutboxEventMapper.claimSending(event.getId());
        if (claimed <= 0) {
            return;
        }

        CreditApprovalTaskMessage message = mqOutboxService.parsePayload(event);
        CreditAsyncTask task = creditAsyncTaskMapper.selectById(message.getTaskId());
        if (task == null) {
            markFailed(event, "task not found: " + message.getTaskId(), true);
            return;
        }

        long sendStart = System.nanoTime();
        boolean sent = creditApprovalTaskProducer.send(task);
        long sendCostMs = (System.nanoTime() - sendStart) / 1_000_000L;
        if (sent) {
            event.setStatus(MqOutboxStatus.SENT);
            event.setLastError(null);
            mqOutboxEventMapper.updateById(event);

            task.setStatus(CreditAsyncTask.MQ_SENT);
            task.setErrorMsg(null);
            creditAsyncTaskMapper.updateById(task);
            log.info("[outbox-publisher] sent eventId={} taskId={} cost={}ms",
                    event.getId(), task.getId(), sendCostMs);
            return;
        }

        int retryCount = event.getRetryCount() != null ? event.getRetryCount() : 0;
        retryCount++;
        event.setRetryCount(retryCount);
        event.setLastError("RocketMQ send failed");
        if (retryCount >= MAX_RETRY) {
            event.setStatus(MqOutboxStatus.FAILED);
            event.setNextRetryTime(null);
            mqOutboxEventMapper.updateById(event);

            task.setStatus(CreditAsyncTask.MQ_SEND_FAILED);
            task.setErrorMsg("RocketMQ outbox send exceeded max retries");
            creditAsyncTaskMapper.updateById(task);
            mqAuditService.record(
                    CreditApprovalMqAuditService.EVENT_MQ_SEND_FAILED,
                    message,
                    false,
                    sendCostMs,
                    event.getLastError(),
                    buildExtra(event, retryCount));
            log.warn("[outbox-publisher] max retry exceeded eventId={} taskId={}",
                    event.getId(), task.getId());
            return;
        }

        event.setStatus(MqOutboxStatus.FAILED);
        event.setNextRetryTime(LocalDateTime.now().plusSeconds(backoffSeconds(retryCount)));
        mqOutboxEventMapper.updateById(event);
        mqAuditService.record(
                CreditApprovalMqAuditService.EVENT_MQ_SEND_FAILED,
                message,
                false,
                sendCostMs,
                event.getLastError(),
                buildExtra(event, retryCount));
        log.warn("[outbox-publisher] send failed eventId={} taskId={} retryCount={}",
                event.getId(), task.getId(), retryCount);
    }

    private void markFailed(MqOutboxEvent event, String error, boolean terminal) {
        event.setStatus(MqOutboxStatus.FAILED);
        event.setLastError(error);
        if (terminal) {
            event.setNextRetryTime(null);
        }
        mqOutboxEventMapper.updateById(event);
    }

    private long backoffSeconds(int retryCount) {
        int index = Math.min(Math.max(retryCount - 1, 0), BACKOFF_SECONDS.size() - 1);
        return BACKOFF_SECONDS.get(index);
    }

    private java.util.Map<String, Object> buildExtra(MqOutboxEvent event, int retryCount) {
        java.util.Map<String, Object> extra = new java.util.HashMap<>();
        extra.put("outboxEventId", event.getId());
        extra.put("retryCount", retryCount);
        extra.put("destination", properties.destination());
        return extra;
    }
}
