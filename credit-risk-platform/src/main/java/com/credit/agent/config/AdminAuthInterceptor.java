package com.credit.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.credit.common.Result;
import com.credit.common.dto.UserDTO;
import com.credit.common.enums.UserRoleEnum;
import com.credit.common.context.UserHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    @Resource
    private AgentProperties agentProperties;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            writeForbidden(response, "请先登录");
            return false;
        }
        if (UserRoleEnum.ADMIN.name().equals(user.getRole())) {
            return true;
        }
        Set<Long> admins = parseAdminIds();
        if (admins.contains(user.getId())) {
            return true;
        }
        writeForbidden(response, "无运营权限，需要 ADMIN 角色");
        return false;
    }

    private Set<Long> parseAdminIds() {
        String raw = agentProperties.getAdminUserIds();
        if (raw == null || raw.trim().isEmpty()) {
            return java.util.Collections.emptySet();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }

    private void writeForbidden(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(msg)));
    }
}
