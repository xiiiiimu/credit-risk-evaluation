package com.credit.workflow.tool;

import cn.hutool.json.JSONUtil;
import com.credit.common.Result;
import com.credit.workflow.dto.WorkflowAcquireResult;
import com.credit.workflow.dto.WorkflowIdempotentResult;
import com.credit.workflow.enums.WorkflowStatus;
import com.credit.workflow.service.WorkflowExecutionService;
import com.credit.workflow.service.WorkflowIdempotencyService;
import com.credit.workflow.service.WorkflowPersistenceService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class WorkflowToolService {

    @Resource
    private WorkflowPersistenceService workflowPersistenceService;
    @Resource
    private WorkflowIdempotencyService workflowIdempotencyService;
    @Resource
    private WorkflowExecutionService workflowExecutionService;

    public Result acquireWorkflowExecution(Map<String, Object> args) {
        String workflowId = stringVal(args, "workflowId");
        WorkflowAcquireResult acquire = workflowExecutionService.acquireForExecution(
                workflowId,
                stringVal(args, "traceId"),
                longVal(args, "taskId"),
                longVal(args, "applicationId"),
                stringVal(args, "lockOwner"));
        Map<String, Object> data = new HashMap<>();
        data.put("acquired", acquire.isAcquired());
        data.put("action", acquire.getIdempotentAction());
        data.put("status", acquire.getStatus());
        data.put("currentNode", acquire.getCurrentNode());
        if (acquire.getCachedResultJson() != null) {
            data.put("result", JSONUtil.parse(acquire.getCachedResultJson()));
        }
        return Result.ok(data);
    }

    public Result releaseWorkflowLock(Map<String, Object> args) {
        workflowExecutionService.releaseLock(stringVal(args, "workflowId"));
        return Result.ok();
    }

    public Result resolveWorkflowIdempotent(Map<String, Object> args) {
        String workflowId = stringVal(args, "workflowId");
        WorkflowIdempotentResult resolved = workflowIdempotencyService.resolve(workflowId);
        Map<String, Object> data = new HashMap<>();
        data.put("action", resolved.getAction());
        data.put("status", resolved.getStatus());
        data.put("currentNode", resolved.getCurrentNode());
        if (resolved.getResultJson() != null) {
            data.put("result", JSONUtil.parse(resolved.getResultJson()));
        }
        return Result.ok(data);
    }

    public Result startWorkflow(Map<String, Object> args) {
        String workflowId = stringVal(args, "workflowId");
        workflowPersistenceService.startWorkflow(
                workflowId,
                stringVal(args, "traceId"),
                longVal(args, "taskId"),
                longVal(args, "applicationId"));
        return Result.ok();
    }

    public Result beginWorkflowNode(Map<String, Object> args) {
        workflowPersistenceService.beginNode(
                stringVal(args, "workflowId"),
                stringVal(args, "nodeName"),
                stringVal(args, "agentName"),
                stringVal(args, "traceId"),
                args.get("input"));
        return Result.ok();
    }

    public Result completeWorkflowNode(Map<String, Object> args) {
        workflowPersistenceService.completeNode(
                stringVal(args, "workflowId"),
                stringVal(args, "nodeName"),
                stringVal(args, "traceId"),
                stringVal(args, "agentName"),
                boolVal(args, "success", true),
                args.get("output"),
                stringVal(args, "errorCode"),
                stringVal(args, "errorMsg"),
                intVal(args, "retryCount", 0),
                longVal(args, "costTimeMs", 0L));
        return Result.ok();
    }

    public Result saveWorkflowCheckpoint(Map<String, Object> args) {
        workflowPersistenceService.saveCheckpoint(
                stringVal(args, "workflowId"),
                stringVal(args, "currentNode"),
                args.get("state"),
                args.get("history"),
                intVal(args, "retryCount", 0));
        return Result.ok();
    }

    public Result loadWorkflowCheckpoint(Map<String, Object> args) {
        Map<String, Object> data = workflowPersistenceService.loadCheckpoint(stringVal(args, "workflowId"));
        return Result.ok(data);
    }

    public Result finishWorkflow(Map<String, Object> args) {
        workflowPersistenceService.finishWorkflow(
                stringVal(args, "workflowId"),
                stringVal(args, "status", WorkflowStatus.SUCCESS),
                stringVal(args, "traceId"),
                args.get("result"),
                intVal(args, "retryCount", 0));
        return Result.ok();
    }

    public Result getWorkflowExecution(Map<String, Object> args) {
        Map<String, Object> data = workflowPersistenceService.getExecutionChain(stringVal(args, "workflowId"));
        if (data == null) {
            return Result.fail("workflow 不存在");
        }
        return Result.ok(data);
    }

    private String stringVal(Map<String, Object> args, String key) {
        if (args == null || args.get(key) == null) {
            return null;
        }
        return args.get(key).toString();
    }

    private String stringVal(Map<String, Object> args, String key, String defaultValue) {
        String v = stringVal(args, key);
        return v != null ? v : defaultValue;
    }

    private Long longVal(Map<String, Object> args, String key) {
        if (args == null || args.get(key) == null) {
            return null;
        }
        return Long.valueOf(args.get(key).toString());
    }

    private long longVal(Map<String, Object> args, String key, long defaultValue) {
        Long v = longVal(args, key);
        return v != null ? v : defaultValue;
    }

    private int intVal(Map<String, Object> args, String key, int defaultValue) {
        if (args == null || args.get(key) == null) {
            return defaultValue;
        }
        return Integer.parseInt(args.get(key).toString());
    }

    private boolean boolVal(Map<String, Object> args, String key, boolean defaultValue) {
        if (args == null || args.get(key) == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(args.get(key).toString());
    }
}
