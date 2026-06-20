package com.credit.credit.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreditApplyVO {

    private Long id;
    private String applicationNo;
    private Long userId;
    private Long productId;
    private String productName;
    private BigDecimal applyAmount;
    private Integer applyTerm;
    private String purpose;
    private String content;
    private Boolean verifiedDocuments;
    private String status;
    private String finalDecision;
    private String agentSuggestion;
    private BigDecimal suggestedAmount;
    private BigDecimal suggestedRate;
    private BigDecimal approvedAmount;
    private BigDecimal approvedRate;
    private String riskLevel;
    private Integer riskScore;
    private List<String> hitRules;
    private List<String> decisionReason;
    private String platformDecisionJson;
    private Integer fraudScore;
    private Boolean bureauUnavailable;
    private Boolean conflictDetected;
    private String aiSummary;
    private String workflowId;
    private LocalDateTime createTime;
}
