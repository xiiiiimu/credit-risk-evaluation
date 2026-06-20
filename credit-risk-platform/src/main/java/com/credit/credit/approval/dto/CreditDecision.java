package com.credit.credit.approval.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Java 终审结果，含可解释审批字段。
 */
@Data
@Builder
public class CreditDecision {

    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String MANUAL_REVIEW = "MANUAL_REVIEW";

    /** 与 route 相同，便于 API 输出 decision 字段 */
    private String route;
    private BigDecimal approvedAmount;
    private BigDecimal approvedRate;
    /** 终审期限（月），仅 Java Rule Engine 生成 */
    private Integer approvedTerm;
    private boolean needManualReview;
    private String reason;
    private Map<String, Object> audit;

    private Integer riskScore;
    private String riskLevel;
    private List<String> hitRules;
    private List<String> decisionReason;
    private String decisionReasonSummary;
}
