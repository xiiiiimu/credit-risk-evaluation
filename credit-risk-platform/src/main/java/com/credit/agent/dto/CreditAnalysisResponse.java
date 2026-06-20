package com.credit.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class CreditAnalysisResponse {

    private String workflowId;
    private String purpose;
    private Boolean verifiedDocuments;
    private Boolean creditEligible;
    private String summary;
    private String aiAnalysisJson;
    private String agentSuggestion;
    private String consensusSuggestion;
    private Boolean needManualReview;
    private Boolean conflictDetected;
    private Boolean bureauUnavailable;
    private String riskLevel;
    private Integer riskScore;
    private BigDecimal minAgentConfidence;
    private List<String> agentConflicts;
    private List<String> fraudHitRules;
    private Integer fraudScore;
    private Integer bureauCreditScore;
    private Double incomeDebtRatio;
    private Double documentScore;
    private Map<String, String> agentVotes;
    private String consensusJson;
    private BigDecimal suggestAmount;
    private BigDecimal suggestRate;
    private Integer suggestTerm;
    private String reason;
    private String answer;
    private Boolean degraded;
    private String ticketTitle;
    private String ticketDescription;
    private Integer recentApplyCount7d;
}
