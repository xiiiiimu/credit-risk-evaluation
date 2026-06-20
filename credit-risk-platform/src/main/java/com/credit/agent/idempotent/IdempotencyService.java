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

    @Resource
    private AgentIdempotentRecordMapper idempotentRecordMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public <T> T execute(String scope, String idempotencyKey, Supplier<T> supplier, Class<T> type) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return supplier.get();
        }
        String key = idempotencyKey.trim();
        String redisKey = REDIS_PREFIX + scope + ":" + key;
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            AgentIdempotentRecord existing = idempotentRecordMapper.selectOne(
                    new QueryWrapper<AgentIdempotentRecord>()
                            .eq("scope", scope)
                            .eq("idempotency_key", key)
                            .last("LIMIT 1"));
            if (existing != null && existing.getResponseJson() != null) {
                return JSONUtil.toBean(existing.getResponseJson(), type);
            }
        }

        AgentIdempotentRecord record = idempotentRecordMapper.selectOne(
                new QueryWrapper<AgentIdempotentRecord>()
                        .eq("scope", scope)
                        .eq("idempotency_key", key)
                        .last("LIMIT 1"));
        if (record != null && record.getResponseJson() != null) {
            return JSONUtil.toBean(record.getResponseJson(), type);
        }

        T result = supplier.get();
        String json = JSONUtil.toJsonStr(result);
        if (record == null) {
            record = new AgentIdempotentRecord();
            record.setScope(scope);
            record.setIdempotencyKey(key);
            record.setResponseJson(json);
            try {
                idempotentRecordMapper.insert(record);
            } catch (DuplicateKeyException ignored) {
                AgentIdempotentRecord dup = idempotentRecordMapper.selectOne(
                        new QueryWrapper<AgentIdempotentRecord>()
                                .eq("scope", scope)
                                .eq("idempotency_key", key)
                                .last("LIMIT 1"));
                if (dup != null && dup.getResponseJson() != null) {
                    return JSONUtil.toBean(dup.getResponseJson(), type);
                }
            }
        } else {
            record.setResponseJson(json);
            idempotentRecordMapper.updateById(record);
        }
        return result;
    }
}
