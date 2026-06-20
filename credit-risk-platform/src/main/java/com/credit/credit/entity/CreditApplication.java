package com.credit.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_credit_application")
public class CreditApplication {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String applicationNo;
    private Long userId;
    private Long productId;
    private BigDecimal applyAmount;
    private Integer applyTerm;
    private String purpose;
    private String content;
    private String documentStatus;
    private Boolean verifiedDocuments;
    private String status;
    private String aiSummary;
    private String aiAnalysisJson;
    private String aiSuggestionJson;
    private String platformDecisionJson;
    private String agentSuggestion;
    private String finalDecision;
    private BigDecimal suggestedAmount;
    private BigDecimal suggestedRate;
    private BigDecimal approvedAmount;
    private BigDecimal approvedRate;
    private String riskLevel;
    private BigDecimal minAgentConfidence;
    private String agentConflictsJson;
    private Integer fraudScore;
    private Integer riskScore;
    private String hitRulesJson;
    private String decisionReasonJson;
    private String consensusJson;
    private Boolean bureauUnavailable;
    private Boolean conflictDetected;
    private BigDecimal incomeDebtRatio;
    private BigDecimal documentScore;
    private Long ticketId;
    private String sessionId;
    private String workflowId;
    private String idempotencyKey;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
