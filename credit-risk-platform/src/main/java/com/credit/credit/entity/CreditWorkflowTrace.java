package com.credit.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_credit_workflow_trace")
public class CreditWorkflowTrace {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String DEGRADED = "DEGRADED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private Long taskId;
    private String workflowId;
    private String traceId;
    private String nodeName;
    private Integer latencyMs;
    private String status;
    private String errorMessage;
    private String toolCallsJson;
    private Integer mcpLatencyMs;
    private LocalDateTime createTime;
}
