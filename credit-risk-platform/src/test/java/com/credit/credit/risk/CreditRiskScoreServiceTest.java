package com.credit.credit.risk;

import com.credit.credit.risk.dto.RiskScoreInput;
import com.credit.credit.risk.dto.RiskScoreResult;
import com.credit.agent.risk.enums.UserRiskLevel;
import com.credit.credit.testsupport.RuleConfigServiceStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditRiskScoreServiceTest {

    private CreditRiskScoreService service;

    @BeforeEach
    void setUp() {
        service = new CreditRiskScoreService();
        ReflectionTestUtils.setField(service, "ruleConfigService", new RuleConfigServiceStub());
    }

    @Test
    void calculate_lowRisk() {
        RiskScoreInput input = RiskScoreInput.builder()
                .bureauCreditScore(720)
                .fraudScore(10)
                .incomeDebtRatio(0.25)
                .documentScore(0.95)
                .minAgentConfidence(0.92)
                .highFrequencyApply(false)
                .bureauUnavailable(false)
                .build();

        RiskScoreResult result = service.calculate(input);

        assertTrue(result.getRiskScore() < 40, "expected low risk score");
        assertEquals(UserRiskLevel.LOW, result.getRiskLevel());
    }

    @Test
    void calculate_mediumRisk() {
        RiskScoreInput input = RiskScoreInput.builder()
                .bureauCreditScore(620)
                .fraudScore(50)
                .incomeDebtRatio(0.48)
                .documentScore(0.75)
                .minAgentConfidence(0.88)
                .highFrequencyApply(false)
                .bureauUnavailable(false)
                .build();

        RiskScoreResult result = service.calculate(input);

        assertTrue(result.getRiskScore() >= 40 && result.getRiskScore() < 70);
        assertEquals(UserRiskLevel.MEDIUM, result.getRiskLevel());
        assertTrue(result.getHitRules().contains("CREDIT_SCORE_MEDIUM")
                || result.getHitRules().contains("MEDIUM_DEBT_RATIO"));
    }

    @Test
    void calculate_highRisk() {
        RiskScoreInput input = RiskScoreInput.builder()
                .bureauCreditScore(520)
                .fraudScore(80)
                .incomeDebtRatio(0.72)
                .documentScore(0.45)
                .minAgentConfidence(0.6)
                .highFrequencyApply(true)
                .bureauUnavailable(false)
                .fraudHitRules(Arrays.asList("DEVICE_ABNORMAL", "BLACKLIST_HIT"))
                .build();

        RiskScoreResult result = service.calculate(input);

        assertTrue(result.getRiskScore() >= 70);
        assertEquals(UserRiskLevel.HIGH, result.getRiskLevel());
        assertTrue(result.getHitRules().contains("LOW_CREDIT_SCORE"));
        assertTrue(result.getHitRules().contains("HIGH_FRAUD_SCORE"));
    }

    @Test
    void calculate_lowConfidence() {
        RiskScoreInput input = RiskScoreInput.builder()
                .bureauCreditScore(700)
                .fraudScore(5)
                .incomeDebtRatio(0.2)
                .documentScore(0.9)
                .minAgentConfidence(0.7)
                .highFrequencyApply(false)
                .bureauUnavailable(false)
                .build();

        RiskScoreResult result = service.calculate(input);

        assertTrue(result.getHitRules().contains("LOW_AGENT_CONFIDENCE"));
        assertTrue(result.getRiskScore() >= 12);
    }

    @Test
    void calculate_bureauUnavailable() {
        RiskScoreInput input = RiskScoreInput.builder()
                .bureauCreditScore(700)
                .fraudScore(0)
                .incomeDebtRatio(0.2)
                .documentScore(0.9)
                .minAgentConfidence(0.9)
                .highFrequencyApply(false)
                .bureauUnavailable(true)
                .build();

        RiskScoreResult result = service.calculate(input);

        assertTrue(result.getHitRules().contains("BUREAU_UNAVAILABLE"));
        assertTrue(result.getRiskScore() >= 35);
    }

    @Test
    void calculate_nullInput() {
        RiskScoreResult result = service.calculate(null);
        assertEquals(100, result.getRiskScore());
        assertEquals(UserRiskLevel.HIGH, result.getRiskLevel());
        assertEquals(Collections.singletonList("NULL_INPUT"), result.getHitRules());
    }
}
