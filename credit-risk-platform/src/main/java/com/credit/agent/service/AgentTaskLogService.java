package com.credit.agent.service;

import cn.hutool.json.JSONUtil;
import com.credit.agent.entity.AgentTaskLog;
import com.credit.agent.mapper.AgentTaskLogMapper;
import com.credit.agent.tool.AgentToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

@Slf4j
@Service
public class AgentTaskLogService {

    public static final String CALL_TOOL = "TOOL";
    public static final String CALL_REMOTE = "REMOTE";
    public static final String CALL_GRANT = "GRANT";
    public static final String CALL_WORKFLOW = "WORKFLOW";

    @Resource
    private AgentTaskLogMapper agentTaskLogMapper;

    @Async
    public void log(String tool, Map<String, Object> args, Object response, boolean success, long costMs) {
        log(tool, CALL_TOOL, null, null, args, response, success, costMs, null);
    }

    @Async
    public void logRemote(String agentName, Long userId, String tool, Object response,
                          boolean success, long costMs, String errorMsg) {
        log(tool, CALL_REMOTE, agentName, userId, null, response, success, costMs, errorMsg);
    }

    @Async
    public void log(String toolName, String callType, String agentName, Long userId,
                    Map<String, Object> args, Object response, boolean success, long costMs, String errorMsg) {
        AgentTaskLog taskLog = new AgentTaskLog();
        AgentToolContext.Context ctx = AgentToolContext.get();
        if (ctx != null) {
            taskLog.setSessionId(ctx.getSessionId());
            taskLog.setTraceId(ctx.getTraceId());
            if (userId == null) {
                userId = ctx.getUserId();
            }
            if (agentName == null) {
                agentName = ctx.getAgentName();
            }
        }
        taskLog.setToolName(toolName);
        taskLog.setCallType(callType);
        taskLog.setAgentName(agentName);
        taskLog.setUserId(userId);
        taskLog.setRequestJson(args != null ? JSONUtil.toJsonStr(args) : null);
        taskLog.setResponseJson(JSONUtil.toJsonStr(response));
        taskLog.setSuccess(success);
        taskLog.setCostMs((int) costMs);
        taskLog.setErrorMsg(errorMsg);
        try {
            agentTaskLogMapper.insert(taskLog);
        } catch (Exception e) {
            log.warn("Agent task log insert failed: {}", e.getMessage());
        }
    }
}
