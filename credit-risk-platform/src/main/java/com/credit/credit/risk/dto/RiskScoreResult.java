package com.credit.credit.risk.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 统一风险评分结果（0-100，分数越高风险越大）。
 */
@Data
@Builder
public class RiskScoreResult {

    private int riskScore;
    private String riskLevel;
    private List<String> factors;
    private List<String> hitRules;
}
