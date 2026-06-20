package com.credit.credit.mq.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批任务触发消息，仅携带任务索引字段；完整上下文由 Consumer 回查数据库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditApprovalTaskMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long taskId;
    private String workflowId;
    private Long applicationId;
    private Long userId;
    private Long productId;
    private String idempotencyKey;
    private String traceId;
    private LocalDateTime createdAt;
}
