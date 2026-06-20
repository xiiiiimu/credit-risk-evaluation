package com.credit.agent.workflow;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.credit.agent.entity.AgentWorkflowTrace;
import com.credit.agent.mapper.AgentWorkflowTraceMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Service
public class AgentWorkflowTraceService {

    @Resource
    private AgentWorkflowTraceMapper agentWorkflowTraceMapper;

    public void append(String workflowId, String traceId, String nodeName,
                       String thought, String action, String observation, String decision,
                       String toolName, Object toolInput, Object toolOutput,
                       Integer latencyMs, Integer retryCount, String errorMsg) {
        AgentWorkflowTrace row = new AgentWorkflowTrace();
        row.setWorkflowId(workflowId);
        row.setTraceId(traceId);
        row.setNodeName(nodeName);
        row.setThought(thought);
        row.setAction(action);
        row.setObservation(observation);
        row.setDecision(decision);
        row.setToolName(toolName);
        row.setToolInput(toolInput != null ? JSONUtil.toJsonStr(toolInput) : null);
        row.setToolOutput(toolOutput != null ? JSONUtil.toJsonStr(toolOutput) : null);
        row.setLatencyMs(latencyMs);
        row.setRetryCount(retryCount != null ? retryCount : 0);
        row.setErrorMsg(errorMsg);
        agentWorkflowTraceMapper.insert(row);
    }

    public void appendFromMap(Map<String, Object> args) {
        append(
                str(args, "workflowId"),
                str(args, "traceId"),
                str(args, "nodeName"),
                str(args, "thought"),
                str(args, "action"),
                str(args, "observation"),
                str(args, "decision"),
                str(args, "toolName"),
                args.get("toolInput"),
                args.get("toolOutput"),
                intVal(args, "latencyMs"),
                intVal(args, "retryCount"),
                str(args, "errorMsg"));
    }

    public List<AgentWorkflowTrace> listByWorkflowId(String workflowId) {
        return agentWorkflowTraceMapper.selectList(
                new QueryWrapper<AgentWorkflowTrace>()
                        .eq("workflow_id", workflowId)
                        .orderByAsc("id"));
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v != null ? v.toString() : null;
    }

    private Integer intVal(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) {
            return null;
        }
        return Integer.valueOf(v.toString());
    }
}
