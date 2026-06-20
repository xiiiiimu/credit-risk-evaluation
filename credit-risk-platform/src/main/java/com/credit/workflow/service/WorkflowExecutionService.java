package com.credit.workflow.service;

import com.credit.workflow.dto.WorkflowAcquireResult;
import com.credit.workflow.dto.WorkflowIdempotentResult;
import com.credit.workflow.enums.WorkflowIdempotentAction;
import com.credit.workflow.enums.WorkflowStatus;
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

        boolean casOk = workflowPersistenceService.tryAcquireRunning(workflowId);
        if (!casOk) {
            workflowLockService.unlock(workflowId);
            WorkflowIdempotentResult retry = workflowIdempotencyService.resolve(workflowId);
            result.setIdempotentAction(retry.getAction());
            result.setStatus(retry.getStatus());
            result.setCurrentNode(retry.getCurrentNode());
            result.setCachedResultJson(retry.getResultJson());
            result.setAcquired(false);
            return result;
        }

        result.setAcquired(true);
        result.setIdempotentAction(WorkflowIdempotentAction.RUN);
        result.setStatus(WorkflowStatus.RUNNING);
        return result;
    }

    public void releaseLock(String workflowId) {
        workflowLockService.unlock(workflowId);
    }
}
