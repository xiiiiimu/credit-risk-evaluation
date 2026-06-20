package com.credit.workflow.service;

import com.credit.workflow.dto.WorkflowAcquireResult;
import com.credit.workflow.dto.WorkflowIdempotentResult;
import com.credit.workflow.enums.WorkflowIdempotentAction;
import com.credit.workflow.lock.WorkflowLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class WorkflowExecutionService {

    @Resource
    private WorkflowIdempotencyService workflowIdempotencyService;
    @Resource
    private WorkflowPersistenceService workflowPersistenceService;
    @Resource
    private WorkflowLockService workflowLockService;

    public WorkflowAcquireResult acquireForExecution(String workflowId, String traceId,
                                                     Long taskId, Long applicationId, String lockOwner) {
        WorkflowAcquireResult result = new WorkflowAcquireResult();
        WorkflowIdempotentResult idem = workflowIdempotencyService.resolve(workflowId);
        result.setIdempotentAction(idem.getAction());
        result.setStatus(idem.getStatus());
        result.setCurrentNode(idem.getCurrentNode());
        result.setCachedResultJson(idem.getResultJson());

        if (WorkflowIdempotentAction.RETURN_RESULT.equals(idem.getAction())) {
            result.setAcquired(false);
            return result;
        }
        if (WorkflowIdempotentAction.WAIT.equals(idem.getAction())) {
            result.setAcquired(false);
            return result;
        }

        workflowPersistenceService.initWorkflowIfAbsent(workflowId, traceId, taskId, applicationId);

        if (!workflowLockService.tryLock(workflowId, lockOwner)) {
            result.setIdempotentAction(WorkflowIdempotentAction.WAIT);
            result.setAcquired(false);
            return result;
        }

        // 不在 Java 侧 CAS 为 RUNNING：由 Python Agent 在 graph_runner 内 acquire/start，
        // 避免 resolve 返回 WAIT 后 credit-analyze 仍收到 409。
        result.setAcquired(true);
        result.setIdempotentAction(WorkflowIdempotentAction.RUN);
        return result;
    }

    public void releaseLock(String workflowId) {
        workflowLockService.unlock(workflowId);
    }

    public boolean isLockHeldBy(String workflowId, String lockOwner) {
        return workflowLockService.isHeldBy(workflowId, lockOwner);
    }
}
