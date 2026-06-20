package com.credit.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_agent_task_log")
public class AgentTaskLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private String traceId;
    private String agentName;
    private Long userId;
    private String callType;
    private String toolName;
    private String requestJson;
    private String responseJson;
    private Boolean success;
    private String errorMsg;
    private Integer costMs;
    private LocalDateTime createTime;
}
