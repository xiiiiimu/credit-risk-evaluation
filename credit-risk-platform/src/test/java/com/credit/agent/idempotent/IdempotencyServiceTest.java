package com.credit.agent.idempotent;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.credit.agent.idempotent.entity.AgentIdempotentRecord;
import com.credit.agent.idempotent.mapper.AgentIdempotentRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotencyServiceTest {

    @Mock
    private AgentIdempotentRecordMapper idempotentRecordMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void execute_withoutKey_runsSupplierDirectly() {
        Map<String, Object> result = idempotencyService.execute(
                "credit.apply.submit",
                null,
                () -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("taskId", 1L);
                    return m;
                },
                Map.class);
        assertEquals(1L, result.get("taskId"));
        verifyNoInteractions(idempotentRecordMapper);
    }

    @Test
    void execute_sameKeyTwice_returnsSameTaskId() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(24L), any())).thenReturn(true, false);
        when(idempotentRecordMapper.selectOne(any(QueryWrapper.class))).thenReturn(null, successRecord(99L));
        when(idempotentRecordMapper.insert(any(AgentIdempotentRecord.class))).thenReturn(1);
        when(idempotentRecordMapper.updateById(any(AgentIdempotentRecord.class))).thenReturn(1);

        Map<String, Object> first = idempotencyService.execute(
                "credit.apply.submit",
                "idem-1",
                "hash-1",
                () -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("taskId", 99L);
                    return m;
                },
                Map.class);
        Map<String, Object> second = idempotencyService.execute(
                "credit.apply.submit",
                "idem-1",
                "hash-1",
                () -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("taskId", 100L);
                    return m;
                },
                Map.class);

        assertEquals(99L, ((Number) first.get("taskId")).longValue());
        assertEquals(99L, ((Number) second.get("taskId")).longValue());
    }

    @Test
    void execute_differentRequestHash_throwsConflict() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(24L), any())).thenReturn(false);
        when(idempotentRecordMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(successRecordWithHash(1L, "hash-a"));

        assertThrows(IdempotencyConflictException.class, () -> idempotencyService.execute(
                "credit.apply.submit",
                "idem-2",
                "hash-b",
                () -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("taskId", 2L);
                    return m;
                },
                Map.class));
    }

    @Test
    void execute_duplicateInsert_waitsForExistingResponse() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(24L), any())).thenReturn(true);
        AtomicInteger selectCount = new AtomicInteger();
        when(idempotentRecordMapper.selectOne(any(QueryWrapper.class))).thenAnswer(inv -> {
            if (selectCount.getAndIncrement() == 0) {
                return null;
            }
            return successRecordWithHash(77L, "hash-3");
        });
        when(idempotentRecordMapper.insert(any(AgentIdempotentRecord.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        Map<String, Object> result = idempotencyService.execute(
                "credit.apply.submit",
                "idem-3",
                "hash-3",
                () -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("taskId", 88L);
                    return m;
                },
                Map.class);

        assertEquals(77L, ((Number) result.get("taskId")).longValue());
        verify(stringRedisTemplate).delete("idempotent:credit.apply.submit:idem-3");
    }

    @Test
    void execute_failedRecord_doesNotReRunSupplier() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(24L), any())).thenReturn(true);
        AgentIdempotentRecord failed = new AgentIdempotentRecord();
        failed.setScope("credit.apply.submit");
        failed.setIdempotencyKey("idem-failed");
        failed.setRequestHash("hash-f");
        failed.setStatus(IdempotencyStatus.FAILED);
        failed.setErrorMsg("submit tx failed");
        when(idempotentRecordMapper.selectOne(any(QueryWrapper.class))).thenReturn(failed);

        assertThrows(IdempotencyFailedException.class, () -> idempotencyService.execute(
                "credit.apply.submit",
                "idem-failed",
                "hash-f",
                () -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("taskId", 999L);
                    return m;
                },
                Map.class));
        verify(idempotentRecordMapper, never()).updateById(argThat(r ->
                IdempotencyStatus.PROCESSING.equals(((AgentIdempotentRecord) r).getStatus())));
    }

    @Test
    void execute_failedRecordOnWaitPath_throwsFailed() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(24L), any())).thenReturn(false);
        AgentIdempotentRecord failed = new AgentIdempotentRecord();
        failed.setScope("credit.apply.submit");
        failed.setIdempotencyKey("idem-f2");
        failed.setRequestHash("hash-f2");
        failed.setStatus(IdempotencyStatus.FAILED);
        when(idempotentRecordMapper.selectOne(any(QueryWrapper.class))).thenReturn(failed);

        assertThrows(IdempotencyFailedException.class, () -> idempotencyService.execute(
                "credit.apply.submit",
                "idem-f2",
                "hash-f2",
                () -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("taskId", 888L);
                    return m;
                },
                Map.class));
    }

    private AgentIdempotentRecord successRecord(long taskId) {
        return successRecordWithHash(taskId, "hash-1");
    }

    private AgentIdempotentRecord successRecordWithHash(long taskId, String hash) {
        AgentIdempotentRecord record = new AgentIdempotentRecord();
        record.setScope("credit.apply.submit");
        record.setIdempotencyKey("k");
        record.setRequestHash(hash);
        record.setStatus(IdempotencyStatus.SUCCESS);
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        record.setResponseJson(JSONUtil.toJsonStr(payload));
        return record;
    }
}
