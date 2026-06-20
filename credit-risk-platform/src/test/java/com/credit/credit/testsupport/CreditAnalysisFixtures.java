package com.credit.credit.testsupport;

import com.credit.credit.dto.CreditAnalysisDTO;
import com.credit.credit.enums.AgentSuggestion;
import com.credit.agent.risk.enums.UserRiskLevel;

import java.math.BigDecimal;

public final class CreditAnalysisFixtures {

    private CreditAnalysisFixtures() {
    }

    public static CreditAnalysisDTO lowRiskApprovedAgentView() {
        CreditAnalysisDTO dto = baseEligible();
        dto.setRiskLevel(UserRiskLevel.LOW);
        dto.setRiskScore(22);
        dto.setFraudScore(8);
        dto.setConsensusSuggestion(AgentSuggestion.SUGGEST_APPROVE);
        dto.setAgentSuggestion(AgentSuggestion.SUGGEST_APPROVE);
        dto.setMinAgentConfidence(new BigDecimal("0.93"));
        dto.setAiSummary("资料完整，征信与收入匹配，建议通过");
        return dto;
    }

    public static CreditAnalysisDTO mediumRiskAgentView() {
        CreditAnalysisDTO dto = baseEligible();
        dto.setRiskLevel(UserRiskLevel.MEDIUM);
        dto.setRiskScore(55);
        dto.setFraudScore(25);
        dto.setIncomeDebtRatio(0.45);
        dto.setConsensusSuggestion(AgentSuggestion.SUGGEST_APPROVE);
        dto.setAgentSuggestion(AgentSuggestion.SUGGEST_APPROVE);
        dto.setMinAgentConfidence(new BigDecimal("0.88"));
        dto.setAiSummary("负债率偏高，存在还款压力");
        return dto;
    }

    public static CreditAnalysisDTO highFraudAgentView() {
        CreditAnalysisDTO dto = baseEligible();
        dto.setRiskLevel(UserRiskLevel.HIGH);
        dto.setRiskScore(70);
        dto.setFraudScore(88);
        dto.setConsensusSuggestion(AgentSuggestion.SUGGEST_REJECT);
        dto.setAgentSuggestion(AgentSuggestion.SUGGEST_REJECT);
        dto.setMinAgentConfidence(new BigDecimal("0.91"));
        dto.setAiSummary("反欺诈分数过高，存在欺诈风险");
        return dto;
    }

    public static CreditAnalysisDTO ocrLowConfidenceManualReviewView() {
        CreditAnalysisDTO dto = baseEligible();
        dto.setVerifiedDocuments(false);
        dto.setDocumentScore(0.42);
        dto.setNeedManualReview(true);
        dto.setConsensusSuggestion(AgentSuggestion.SUGGEST_MANUAL);
        dto.setAgentSuggestion(AgentSuggestion.SUGGEST_MANUAL);
        dto.setMinAgentConfidence(new BigDecimal("0.55"));
        dto.setAiSummary("OCR 置信度不足，材料需人工核验");
        return dto;
    }

    private static CreditAnalysisDTO baseEligible() {
        CreditAnalysisDTO dto = new CreditAnalysisDTO();
        dto.setVerifiedDocuments(true);
        dto.setCreditEligible(true);
        dto.setBureauUnavailable(false);
        dto.setConflictDetected(false);
        dto.setNeedManualReview(false);
        dto.setBureauCreditScore(720);
        dto.setIncomeDebtRatio(0.25);
        dto.setDocumentScore(0.92);
        dto.setRecentApplyCount7d(0);
        return dto;
    }
}
