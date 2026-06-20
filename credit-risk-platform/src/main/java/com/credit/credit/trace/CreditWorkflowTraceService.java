package com.credit.credit.trace;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.credit.credit.entity.CreditWorkflowTrace;
import com.credit.credit.mapper.CreditWorkflowTraceMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Service
public class CreditWorkflowTraceService {

    @Resource
    private CreditWorkflowTraceMapper creditWorkflowTraceMapper;

    public void saveNode(Long applicationId, Long taskId, String workflowId, String traceId,
                         String nodeName, Integer latencyMs, String status,
                         String errorMessage, Object toolCalls, Integer mcpLatencyMs) {
        CreditWorkflowTrace row = new CreditWorkflowTrace();
        row.setApplicationId(applicationId);
        row.setTaskId(taskId);
        row.setWorkflowId(workflowId);
        row.setTraceId(traceId);
        row.setNodeName(nodeName);
        row.setLatencyMs(latencyMs);
        row.setStatus(status != null ? status : CreditWorkflowTrace.SUCCESS);
        row.setErrorMessage(errorMessage);
        if (toolCalls != null) {
            row.setToolCallsJson(JSONUtil.toJsonStr(toolCalls));
        }
        row.setMcpLatencyMs(mcpLatencyMs);
        creditWorkflowTraceMapper.insert(row);
    }

    public void saveFromMap(Map<String, Object> args) {
        saveNode(
                parseLong(args.get("applicationId")),
                parseLong(args.get("taskId")),
                args.get("workflowId") != null ? args.get("workflowId").toString() : null,
                args.get("traceId") != null ? args.get("traceId").toString() : null,
                args.get("nodeName") != null ? args.get("nodeName").toString() : "unknown",
                args.get("latencyMs") != null ? Integer.valueOf(args.get("latencyMs").toString()) : null,
                args.get("status") != null ? args.get("status").toString() : CreditWorkflowTrace.SUCCESS,
                args.get("errorMessage") != null ? args.get("errorMessage").toString() : null,
                args.get("toolCalls"),
                args.get("mcpLatencyMs") != null ? Integer.valueOf(args.get("mcpLatencyMs").toString()) : null
        );
    }

    /** 兜底：将同一 workflow 下缺失 application_id 的记录回写 */
    public void backfillApplicationId(String workflowId, Long applicationId, Long taskId) {
        if (workflowId == null || applicationId == null) {
            return;
        }
        UpdateWrapper<CreditWorkflowTrace> uw = new UpdateWrapper<CreditWorkflowTrace>()
                .eq("workflow_id", workflowId)
                .and(w -> w.isNull("application_id").or().eq("application_id", applicationId));
        uw.set("application_id", applicationId);
        if (taskId != null) {
            uw.set("task_id", taskId);
        }
        creditWorkflowTraceMapper.update(null, uw);
    }

    public List<CreditWorkflowTrace> listByApplicationId(Long applicationId) {
        return creditWorkflowTraceMapper.selectList(
                new QueryWrapper<CreditWorkflowTrace>()
                        .eq("application_id", applicationId)
                        .orderByAsc("id"));
    }

    public List<CreditWorkflowTrace> listByWorkflowId(String workflowId) {
        return creditWorkflowTraceMapper.selectList(
                new QueryWrapper<CreditWorkflowTrace>()
                        .eq("workflow_id", workflowId)
                        .orderByAsc("id"));
    }

    private Long parseLong(Object v) {
        return v != null ? Long.valueOf(v.toString()) : null;
    }
}
