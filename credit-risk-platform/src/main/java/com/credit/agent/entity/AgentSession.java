package com.credit.agent.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_agent_session")
public class AgentSession {

    @TableId
    private String id;
    private Long userId;
    private String scene;
    private String title;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
