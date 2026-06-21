package com.credit.agent.idempotent;

/**
 * 同一 Idempotency-Key 此前已失败，不允许自动重试创建新 task。
 */
public class IdempotencyFailedException extends RuntimeException {

    public static final String DEFAULT_MESSAGE =
            "Same Idempotency-Key has failed before, please retry with a new Idempotency-Key or contact support.";

    public IdempotencyFailedException() {
        super(DEFAULT_MESSAGE);
    }

    public IdempotencyFailedException(String message) {
        super(message);
    }
}
