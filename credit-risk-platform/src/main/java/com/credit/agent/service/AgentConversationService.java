package com.credit.agent.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.credit.agent.entity.AgentMessage;
import com.credit.agent.entity.AgentSession;
import com.credit.agent.mapper.AgentMessageMapper;
import com.credit.agent.mapper.AgentSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentConversationService {

    public static final String SCENE_CREDIT = "credit";

    @Resource
    private AgentSessionMapper agentSessionMapper;
    @Resource
    private AgentMessageMapper agentMessageMapper;

    @Transactional
    public String ensureSession(String sessionId, Long userId, String scene) {
        if (StrUtil.isNotBlank(sessionId)) {
            AgentSession existing = agentSessionMapper.selectById(sessionId);
            if (existing != null) {
                return sessionId;
            }
        }
        AgentSession session = new AgentSession();
        session.setId(IdUtil.simpleUUID());
        session.setUserId(userId);
        session.setScene(scene == null ? SCENE_CREDIT : scene);
        session.setTitle("信贷咨询");
        agentSessionMapper.insert(session);
        return session.getId();
    }

    public void saveMessage(String sessionId, String role, String content, List<Long> shopIds) {
        AgentMessage msg = new AgentMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        if (shopIds != null && !shopIds.isEmpty()) {
            msg.setShopIds(shopIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        agentMessageMapper.insert(msg);
    }

    public List<AgentSession> listSessions(Long userId, String scene) {
        return agentSessionMapper.selectList(new QueryWrapper<AgentSession>()
                .eq(userId != null, "user_id", userId)
                .eq(StrUtil.isNotBlank(scene), "scene", scene)
                .orderByDesc("update_time")
                .last("LIMIT 50"));
    }

    public List<AgentMessage> listMessages(String sessionId) {
        return agentMessageMapper.selectList(new QueryWrapper<AgentMessage>()
                .eq("session_id", sessionId)
                .orderByAsc("id"));
    }

    /**
     * 最近 N 条消息（含当前轮次已写入的 user 消息），按时间正序返回供 LLM 上下文。
     */
    public List<AgentMessage> listRecentMessages(String sessionId, int limit) {
        List<AgentMessage> desc = agentMessageMapper.selectList(new QueryWrapper<AgentMessage>()
                .eq("session_id", sessionId)
                .orderByDesc("id")
                .last("LIMIT " + Math.max(1, limit)));
        java.util.Collections.reverse(desc);
        return desc;
    }
}
