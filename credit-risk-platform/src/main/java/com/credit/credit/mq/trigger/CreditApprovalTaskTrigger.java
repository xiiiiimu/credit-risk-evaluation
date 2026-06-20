package com.credit.credit.mq.trigger;

import com.credit.credit.entity.CreditAsyncTask;

/**
 * 审批任务触发器：MQ 模式发消息，直连模式走本地线程池。
 */
public interface CreditApprovalTaskTrigger {

    /**
     * @return true 表示触发成功（MQ 发送成功或已提交本地执行）
     */
    boolean trigger(CreditAsyncTask task);
}
