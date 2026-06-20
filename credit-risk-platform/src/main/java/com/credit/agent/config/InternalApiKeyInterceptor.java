package com.credit.agent.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 校验 /internal/** 请求的 X-Internal-Api-Key。
 */
@Component
public class InternalApiKeyInterceptor implements HandlerInterceptor {

    @Resource
    private AgentProperties agentProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String key = request.getHeader("X-Internal-Api-Key");
        String expected = agentProperties.getInternalApiKey();
        if (expected != null) {
            expected = expected.trim();
        }
        if (key != null) {
            key = key.trim();
        }
        if (key == null || expected == null || !key.equals(expected)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"errorMsg\":\"invalid internal api key\"}");
            return false;
        }
        return true;
    }
}
