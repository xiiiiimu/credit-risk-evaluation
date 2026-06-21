package com.credit.credit.mq.outbox;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mq.config.RocketMqApprovalProperties;
import com.credit.credit.mq.message.CreditApprovalTaskMessage;
import com.credit.credit.mq.producer.CreditApprovalTaskProducer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class MqOutboxService {

    public static final String AGGREGATE_TYPE = "CREDIT_APPROVAL_TASK";
    public static final String EVENT_TYPE = "CREDIT_APPROVAL_TASK_CREATED";

    @Resource
    private MqOutboxEventMapper mqOutboxEventMapper;
    @Resource
    private RocketMqApprovalProperties properties;
    @Resource
    private CreditApprovalTaskProducer creditApprovalTaskProducer;

    public void enqueue(CreditAsyncTask task) {
        CreditApprovalTaskMessage message = creditApprovalTaskProducer.toMessage(task);
        MqOutboxEvent event = new MqOutboxEvent();
        event.setEventKey(buildEventKey(task.getId()));
        event.setAggregateType(AGGREGATE_TYPE);
        event.setAggregateId(String.valueOf(task.getId()));
        event.setEventType(EVENT_TYPE);
        event.setDestination(properties.destination());
        event.setPayloadJson(JSONUtil.toJsonStr(message));
        event.setStatus(MqOutboxStatus.NEW);
        event.setRetryCount(0);
        try {
            mqOutboxEventMapper.insert(event);
        } catch (DuplicateKeyException ignored) {
            // 同一 task 重复写入 outbox 时忽略，由 uk_event_key 兜底
        }
    }

    public MqOutboxEvent findByTaskId(Long taskId) {
        return mqOutboxEventMapper.selectOne(
                new QueryWrapper<MqOutboxEvent>()
                        .eq("event_key", buildEventKey(taskId))
                        .last("LIMIT 1"));
    }

    public boolean resetForRedelivery(Long taskId) {
        MqOutboxEvent event = findByTaskId(taskId);
        if (event == null) {
            return false;
        }
        event.setStatus(MqOutboxStatus.NEW);
        event.setRetryCount(0);
        event.setNextRetryTime(null);
        event.setLastError(null);
        return mqOutboxEventMapper.updateById(event) > 0;
    }

    public CreditApprovalTaskMessage parsePayload(MqOutboxEvent event) {
        return JSONUtil.toBean(event.getPayloadJson(), CreditApprovalTaskMessage.class);
    }

    public static String buildEventKey(Long taskId) {
        return "credit-approval:" + taskId;
    }
}
