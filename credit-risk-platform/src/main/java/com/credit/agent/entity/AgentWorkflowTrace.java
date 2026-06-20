package com.credit.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_agent_workflow_trace")
public class AgentWorkflowTrace {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String workflowId;
    private String traceId;
    private String nodeName;
    private String thought;
    private String action;
    private String observation;
    private String decision;
    private String toolName;
    private String toolInput;
    private String toolOutput;
    private Integer latencyMs;
    private Integer retryCount;
    private String errorMsg;
    private LocalDateTime createTime;
}
