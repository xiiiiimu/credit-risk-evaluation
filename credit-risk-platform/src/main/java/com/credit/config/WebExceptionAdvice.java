package com.credit.config;

import com.credit.agent.idempotent.IdempotencyConflictException;
import com.credit.agent.idempotent.IdempotencyProcessingException;
import com.credit.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(IdempotencyProcessingException.class)
    public Result handleIdempotencyProcessing(IdempotencyProcessingException e) {
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public Result handleIdempotencyConflict(IdempotencyConflictException e) {
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }
}
