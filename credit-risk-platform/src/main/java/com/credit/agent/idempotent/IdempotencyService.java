package com.credit.agent.idempotent;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.credit.agent.idempotent.entity.AgentIdempotentRecord;
import com.credit.agent.idempotent.mapper.AgentIdempotentRecordMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * HTTP 提交幂等：以 {@code tb_agent_idempotent_record(scope, idempotency_key)} 唯一约束为准。
 * {@code tb_credit_async_task.idempotency_key} 仅为普通索引，不作为最终幂等依据。
 */
@Service
public class IdempotencyService {

    private static final String REDIS_PREFIX = "idempotent:";
    private static final long REDIS_TTL_HOURS = 24L;
    private static final long WAIT_POLL_MS = 100L;
    private static final long WAIT_MAX_MS = 3000L;

    @Resource
    private AgentIdempotentRecordMapper idempotentRecordMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public <T> T execute(String scope, String idempotencyKey, Supplier<T> supplier, Class<T> type) {
        return execute(scope, idempotencyKey, null, supplier, type);
    }

    public <T> T execute(
            String scope,
            String idempotencyKey,
            String requestHash,
            Supplier<T> supplier,
            Class<T> type) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return supplier.get();
        }
        String key = idempotencyKey.trim();
        String redisKey = REDIS_PREFIX + scope + ":" + key;

        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", REDIS_TTL_HOURS, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            return waitAndReadResponse(scope, key, requestHash, type);
        }

        AgentIdempotentRecord existing = findRecord(scope, key);
        if (existing != null) {
            validateRequestHash(existing, requestHash);
            T resolved = tryResolveCompleted(scope, key, requestHash, existing, type);
            if (resolved != null) {
                return resolved;
            }
        }

        AgentIdempotentRecord record = existing;
        if (record == null) {
            record = new AgentIdempotentRecord();
            record.setScope(scope);
            record.setIdempotencyKey(key);
            record.setRequestHash(requestHash);
            record.setStatus(IdempotencyStatus.PROCESSING);
            try {
                idempotentRecordMapper.insert(record);
            } catch (DuplicateKeyException ignored) {
                stringRedisTemplate.delete(redisKey);
                return waitAndReadResponse(scope, key, requestHash, type);
            }
        } else if (IdempotencyStatus.PROCESSING.equals(record.getStatus())) {
            stringRedisTemplate.delete(redisKey);
            return waitAndReadResponse(scope, key, requestHash, type);
        }

        try {
            T result = supplier.get();
            record.setResponseJson(JSONUtil.toJsonStr(result));
            record.setStatus(IdempotencyStatus.SUCCESS);
            record.setErrorMsg(null);
            idempotentRecordMapper.updateById(record);
            return result;
        } catch (RuntimeException ex) {
            record.setStatus(IdempotencyStatus.FAILED);
            record.setErrorMsg(ex.getMessage());
            idempotentRecordMapper.updateById(record);
            stringRedisTemplate.delete(redisKey);
            throw ex;
        }
    }

    private <T> T waitAndReadResponse(String scope, String key, String requestHash, Class<T> type) {
        long deadline = System.currentTimeMillis() + WAIT_MAX_MS;
        while (System.currentTimeMillis() < deadline) {
            AgentIdempotentRecord record = findRecord(scope, key);
            if (record != null) {
                validateRequestHash(record, requestHash);
                T resolved = tryResolveCompleted(scope, key, requestHash, record, type);
                if (resolved != null) {
                    return resolved;
                }
            }
            sleepQuietly(WAIT_POLL_MS);
        }
        throw new IdempotencyProcessingException("same idempotency key is processing, retry later");
    }

    /**
     * @return 已完成响应；PROCESSING 返回 null 以便继续等待；FAILED 抛异常
     */
    private <T> T tryResolveCompleted(
            String scope,
            String key,
            String requestHash,
            AgentIdempotentRecord record,
            Class<T> type) {
        if (record.getResponseJson() != null && !record.getResponseJson().trim().isEmpty()) {
            return JSONUtil.toBean(record.getResponseJson(), type);
        }
        if (IdempotencyStatus.FAILED.equals(record.getStatus())) {
            String message = record.getErrorMsg() != null && !record.getErrorMsg().trim().isEmpty()
                    ? record.getErrorMsg()
                    : IdempotencyFailedException.DEFAULT_MESSAGE;
            throw new IdempotencyFailedException(message);
        }
        if (IdempotencyStatus.SUCCESS.equals(record.getStatus())) {
            throw new IdempotencyProcessingException("same idempotency key is processing, retry later");
        }
        return null;
    }

    private AgentIdempotentRecord findRecord(String scope, String key) {
        return idempotentRecordMapper.selectOne(
                new QueryWrapper<AgentIdempotentRecord>()
                        .eq("scope", scope)
                        .eq("idempotency_key", key)
                        .last("LIMIT 1"));
    }

    private void validateRequestHash(AgentIdempotentRecord record, String requestHash) {
        if (requestHash == null || requestHash.trim().isEmpty()) {
            return;
        }
        if (record.getRequestHash() != null
                && !record.getRequestHash().trim().isEmpty()
                && !requestHash.equals(record.getRequestHash())) {
            throw new IdempotencyConflictException(
                    "idempotency key reused with different request body");
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IdempotencyProcessingException("same idempotency key is processing, retry later");
        }
    }
}
