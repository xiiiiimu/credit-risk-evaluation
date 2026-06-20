package com.credit.credit.mq.trigger;

import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.service.CreditApplyAsyncProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.Executor;

/**
 * MQ 关闭时的本地触发器，保持与改造前 @Async 行为一致（用于测试或无 MQ 环境）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "credit.mq.enabled", havingValue = "false")
public class DirectCreditApprovalTaskTrigger implements CreditApprovalTaskTrigger {

    @Resource
    @Qualifier("taskExecutor")
    private Executor taskExecutor;
    @Resource
    private CreditApplyAsyncProcessor creditApplyAsyncProcessor;

    @Override
    public boolean trigger(CreditAsyncTask task) {
        taskExecutor.execute(() -> {
            try {
                creditApplyAsyncProcessor.processTask(task.getId());
            } catch (Exception e) {
                log.error("[direct-trigger] taskId={} failed", task.getId(), e);
            }
        });
        return true;
    }
}
