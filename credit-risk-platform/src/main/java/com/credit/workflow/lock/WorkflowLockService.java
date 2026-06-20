package com.credit.workflow.lock;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.UUID;

/**
 * 分布式 Workflow 执行锁：Redis Lua 原子加锁，避免多实例重复消费。
 */
@Service
public class WorkflowLockService {

    private static final String LOCK_SCRIPT =
            "if redis.call('exists', KEYS[1]) == 0 then "
                    + "redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2]); "
                    + "return 1 "
                    + "else return 0 end";

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> lockScript;

    public WorkflowLockService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.lockScript = new DefaultRedisScript<>();
        this.lockScript.setScriptText(LOCK_SCRIPT);
        this.lockScript.setResultType(Long.class);
    }

    public boolean tryLock(String workflowId, String ownerToken) {
        if (!StringUtils.hasText(workflowId)) {
            return false;
        }
        String token = StringUtils.hasText(ownerToken) ? ownerToken : UUID.randomUUID().toString();
        Long result = stringRedisTemplate.execute(
                lockScript,
                Collections.singletonList(WorkflowLockConstants.LOCK_PREFIX + workflowId),
                token,
                String.valueOf(WorkflowLockConstants.LOCK_TTL_SECONDS));
        return result != null && result == 1L;
    }

    public void unlock(String workflowId) {
        if (!StringUtils.hasText(workflowId)) {
            return;
        }
        stringRedisTemplate.delete(WorkflowLockConstants.LOCK_PREFIX + workflowId);
    }

    public boolean isHeldBy(String workflowId, String ownerToken) {
        if (!StringUtils.hasText(workflowId) || !StringUtils.hasText(ownerToken)) {
            return false;
        }
        String current = stringRedisTemplate.opsForValue().get(WorkflowLockConstants.LOCK_PREFIX + workflowId);
        return ownerToken.equals(current);
    }
}
