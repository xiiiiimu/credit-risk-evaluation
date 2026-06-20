package com.credit.agent.exception;

/**
 * Python Agent 返回 409（workflow 仍在执行）时抛出，应触发 MQ 重试而非熔断或 FAILED。
 */
public class WorkflowRunningException extends RuntimeException {

    public WorkflowRunningException(String message) {
        super(message);
    }

    public WorkflowRunningException(String message, Throwable cause) {
        super(message, cause);
    }
}
