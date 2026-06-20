package com.credit.credit.mq.trigger;

import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.credit.mq.producer.CreditApprovalTaskProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
@ConditionalOnProperty(name = "credit.mq.enabled", havingValue = "true", matchIfMissing = true)
public class MqCreditApprovalTaskTrigger implements CreditApprovalTaskTrigger {

    @Resource
    private CreditApprovalTaskProducer creditApprovalTaskProducer;
    @Resource
    private CreditAsyncTaskMapper creditAsyncTaskMapper;

    @Override
    public boolean trigger(CreditAsyncTask task) {
        boolean sent = creditApprovalTaskProducer.send(task);
        if (sent) {
            task.setStatus(CreditAsyncTask.MQ_SENT);
            task.setErrorMsg(null);
            creditAsyncTaskMapper.updateById(task);
            return true;
        }
        task.setStatus(CreditAsyncTask.MQ_SEND_FAILED);
        task.setErrorMsg("RocketMQ 消息发送失败");
        creditAsyncTaskMapper.updateById(task);
        return false;
    }
}
