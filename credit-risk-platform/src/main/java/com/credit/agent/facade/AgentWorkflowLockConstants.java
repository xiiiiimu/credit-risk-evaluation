package com.credit.agent.facade;

/**
 * Java Consumer 持锁调用 Python Agent 时的锁语义标记（Python 侧跳过 Redis acquire / 409 WAIT）。
 */
public final class AgentWorkflowLockConstants {

    public static final String HEADER_LOCK_MODE = "X-Workflow-Lock-Mode";
    public static final String HEADER_LOCK_OWNER = "X-Workflow-Lock-Owner";
    public static final String HEADER_LOCK_OWNER_TOKEN = "X-Workflow-Lock-Owner-Token";

    public static final String MODE_JAVA_OWNED = "JAVA_OWNED";
    public static final String OWNER_JAVA_CONSUMER = "java-consumer";

    private AgentWorkflowLockConstants() {
    }
}
