package com.credit.credit.controller.admin;

import com.credit.agent.health.AgentHealthService;
import com.credit.common.Result;
import com.credit.common.context.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/agent")
public class AdminAgentHealthController {

    @Resource
    private AgentHealthService agentHealthService;

    @GetMapping("/health")
    public Result health() {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        Map<String, Object> snapshot = agentHealthService.snapshot();
        return Result.ok(snapshot);
    }
}
