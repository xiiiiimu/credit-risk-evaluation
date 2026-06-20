package com.credit.credit.approval.dto;

import com.credit.credit.risk.dto.RiskScoreResult;
import com.credit.agent.entity.UserMemory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class CreditDecisionContext {

    private boolean verifiedDocuments;
    private UserMemory userMemory;
    private Boolean creditEligible;
    private Double minAgentConfidence;
    private List<String> agentConflicts;
    private Integer fraudScore;
    private List<String> fraudHitRules;
    private String agentSuggestion;
    private String consensusSuggestion;
    private Boolean needManualReview;
    private Boolean conflictDetected;
    private Boolean bureauUnavailable;
    /** @deprecated Agent 不再输出授信方案，终审额度由 Rule Engine 计算 */
    @Deprecated
    private BigDecimal suggestAmount;
    /** @deprecated Agent 不再输出授信方案，终审利率由 Rule Engine 计算 */
    @Deprecated
    private BigDecimal suggestRate;
    private BigDecimal applyAmount;
    private Integer applyTerm;
    private Long productId;
    private BigDecimal monthlyIncome;
    /** Agent consensus 输出的风险等级 */
    private String agentRiskLevel;

    private Integer bureauCreditScore;
    private Double incomeDebtRatio;
    private Double documentScore;
    private boolean highFrequencyApply;
    private Integer recentApplyCount7d;

    /** 预计算的统一风险分（可为空，引擎内会重算） */
    private RiskScoreResult riskScoreResult;
    private Map<String, String> agentVotes;
}
