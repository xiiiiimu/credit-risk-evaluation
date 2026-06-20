package com.credit.credit.risk.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RiskScoreInput {

    /** 征信分（如 720），越低风险越高 */
    private Integer bureauCreditScore;
    private int fraudScore;
    private Double incomeDebtRatio;
    /** 材料完整度 0-1，越高越好 */
    private Double documentScore;
    private Double minAgentConfidence;
    private boolean highFrequencyApply;
    private boolean bureauUnavailable;
    private List<String> fraudHitRules;
}
