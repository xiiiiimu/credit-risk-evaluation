package com.credit.credit.mq.exception;

/**
 * 可重试的审批任务执行异常，Consumer 抛出后由 RocketMQ 触发重试。
 */
public class CreditApprovalTaskRetryableException extends RuntimeException {

    public CreditApprovalTaskRetryableException(String message) {
        super(message);
    }

    public CreditApprovalTaskRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
