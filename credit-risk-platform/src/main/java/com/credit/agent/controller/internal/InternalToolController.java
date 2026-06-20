package com.credit.agent.controller.internal;

import com.credit.agent.dto.ToolInvokeRequest;
import com.credit.agent.tool.AgentToolContext;
import com.credit.agent.tool.ToolInvokeService;
import com.credit.agent.tool.ToolRegistry;
import com.credit.agent.tool.ToolSchemaRegistry;
import com.credit.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/internal")
public class InternalToolController {

    @Resource
    private ToolInvokeService toolInvokeService;
    @Resource
    private ToolRegistry toolRegistry;
    @Resource
    private ToolSchemaRegistry toolSchemaRegistry;

    @GetMapping("/health")
    public Result health() {
        Map<String, Object> data = new HashMap<>(2);
        data.put("service", "credit-risk-platform");
        data.put("agentMode", "python-sidecar-http");
        return Result.ok(data);
    }

    @GetMapping("/tools/schema")
    public Result schema() {
        Map<String, Object> data = new HashMap<>(2);
        data.put("tools", toolRegistry.listTools());
        data.put("schemas", toolSchemaRegistry.listSchemas());
        return Result.ok(data);
    }

    @PostMapping("/tools/invoke")
    public Result invoke(
            @RequestBody ToolInvokeRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        if (request == null || request.getTool() == null) {
            return Result.fail("tool 不能为空");
        }
        try {
            AgentToolContext.set(sessionId, traceId);
            log.debug("Tool invoke: tool={}, traceId={}, sessionId={}", request.getTool(), traceId, sessionId);
            return toolInvokeService.invoke(request.getTool(), request.getArgs());
        } finally {
            AgentToolContext.clear();
        }
    }
}
