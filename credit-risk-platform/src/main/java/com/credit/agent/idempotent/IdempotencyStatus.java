package com.credit.agent.idempotent;

public final class IdempotencyStatus {

    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private IdempotencyStatus() {
    }
}
