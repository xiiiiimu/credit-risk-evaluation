package com.credit.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_credit_async_task")
public class CreditAsyncTask {

    public static final String PENDING = "PENDING";
    public static final String MQ_SENT = "MQ_SENT";
    public static final String MQ_SEND_FAILED = "MQ_SEND_FAILED";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String MANUAL_REVIEW = "MANUAL_REVIEW";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long productId;
    private BigDecimal applyAmount;
    private Integer applyTerm;
    private String purpose;
    private String content;
    private String structuredApplicationJson;
    private String userNarrativeJson;
    private String uploadedDocumentsJson;
    private String sessionId;
    private String traceId;
    private String workflowId;
    private String idempotencyKey;
    private String status;
    private Long applicationId;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
