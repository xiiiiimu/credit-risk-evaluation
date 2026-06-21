package com.credit.agent.idempotent;

/**
 * 同一 Idempotency-Key 携带了不同的请求体。
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
