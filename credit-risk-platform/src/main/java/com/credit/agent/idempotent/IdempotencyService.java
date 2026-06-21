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
        if (existing != null && existing.getResponseJson() != null) {
            validateRequestHash(existing, requestHash);
            return JSONUtil.toBean(existing.getResponseJson(), type);
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
        } else {
            validateRequestHash(record, requestHash);
            if (existing.getResponseJson() != null) {
                return JSONUtil.toBean(existing.getResponseJson(), type);
            }
            if (IdempotencyStatus.PROCESSING.equals(existing.getStatus())) {
                stringRedisTemplate.delete(redisKey);
                return waitAndReadResponse(scope, key, requestHash, type);
            }
            record.setStatus(IdempotencyStatus.PROCESSING);
            record.setErrorMsg(null);
            idempotentRecordMapper.updateById(record);
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
                if (record.getResponseJson() != null) {
                    return JSONUtil.toBean(record.getResponseJson(), type);
                }
                if (IdempotencyStatus.FAILED.equals(record.getStatus())) {
                    throw new IdempotencyProcessingException(
                            "same idempotency key failed previously, retry later or use a new key");
                }
            }
            sleepQuietly(WAIT_POLL_MS);
        }
        throw new IdempotencyProcessingException("same idempotency key is processing, retry later");
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
