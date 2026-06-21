package com.credit.agent.idempotent;

/**
 * 同一 Idempotency-Key 的首个请求仍在处理中，重复请求应稍后重试。
 */
public class IdempotencyProcessingException extends RuntimeException {

    public IdempotencyProcessingException(String message) {
        super(message);
    }
}
