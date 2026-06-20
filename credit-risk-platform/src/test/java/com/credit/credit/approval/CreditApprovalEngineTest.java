package com.credit.credit.approval;

import com.credit.agent.config.AgentRiskProperties;
import com.credit.credit.approval.dto.CreditDecision;
import com.credit.credit.approval.dto.CreditDecisionContext;
import com.credit.credit.enums.AgentSuggestion;
import com.credit.credit.entity.CreditProduct;
import com.credit.credit.testsupport.RuleConfigServiceStub;
import com.credit.credit.risk.CreditRiskScoreService;
import com.credit.credit.risk.dto.RiskScoreResult;
import com.credit.agent.entity.UserMemory;
import com.credit.agent.risk.enums.UserRiskLevel;
import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreditApprovalEngineTest {

    private CreditApprovalEngine engine;
    private ProductApprovalCalculator productApprovalCalculator;

    @BeforeEach
    void setUp() {
        engine = new CreditApprovalEngine();
        productApprovalCalculator = mock(ProductApprovalCalculator.class);
        AgentRiskProperties props = new AgentRiskProperties();
        props.setAutoMinConfidence(0.85);
        props.setAutoMaxComplaint7d(1);
        ReflectionTestUtils.setField(engine, "riskProperties", props);
        CreditRiskScoreService riskScoreService = new CreditRiskScoreService();
        ReflectionTestUtils.setField(riskScoreService, "ruleConfigService", new RuleConfigServiceStub());
        ReflectionTestUtils.setField(engine, "creditRiskScoreService", riskScoreService);
        ReflectionTestUtils.setField(engine, "ruleConfigService", new RuleConfigServiceStub());
        ReflectionTestUtils.setField(engine, "productApprovalCalculator", productApprovalCalculator);

        CreditProduct product = new CreditProduct();
        product.setId(1L);
        product.setMinAmount(new BigDecimal("1000"));
        product.setMaxAmount(new BigDecimal("200000"));
        product.setInterestRate(new BigDecimal("4.8"));
        lenient().when(productApprovalCalculator.requireActiveProduct(any())).thenReturn(product);
        lenient().when(productApprovalCalculator.calculate(any())).thenAnswer(inv -> {
            CreditDecisionContext ctx = inv.getArgument(0);
            if (ctx == null) {
                return new ProductApprovalCalculator.ProductTermsResult(
                        product, new JSONObject(), UserRiskLevel.LOW,
                        new BigDecimal("30000"), 12, new BigDecimal("4.8"),
                        Collections.emptyList(), Collections.emptyList());
            }
            return new ProductApprovalCalculator.ProductTermsResult(
                    product,
                    new JSONObject(),
                    UserRiskLevel.LOW,
                    ctx.getApplyAmount(),
                    ctx.getApplyTerm() != null ? ctx.getApplyTerm() : 12,
                    new BigDecimal("4.8"),
                    Collections.emptyList(),
                    Collections.emptyList());
        });
    }

    @Test
    void decide_approved_withJavaComputedTerms() {
        CreditDecisionContext ctx = baseEligibleContext()
                .productId(1L)
                .applyTerm(12)
                .riskScoreResult(lowRiskScore())
                .build();

        CreditDecision decision = engine.decide(ctx);
        assertEquals(CreditDecision.APPROVED, decision.getRoute());
        assertNotNull(decision.getApprovedAmount());
        assertNotNull(decision.getApprovedRate());
        assertEquals(Integer.valueOf(12), decision.getApprovedTerm());
    }

    @Test
    void decide_rejected_incompleteDocuments() {
        CreditDecisionContext ctx = baseEligibleContext()
                .verifiedDocuments(false)
                .build();

        assertEquals(CreditDecision.REJECTED, engine.decide(ctx).getRoute());
    }

    @Test
    void decide_rejected_highFraudScore() {
        CreditDecisionContext ctx = baseEligibleContext()
                .fraudScore(85)
                .riskScoreResult(lowRiskScore())
                .build();

        assertEquals(CreditDecision.REJECTED, engine.decide(ctx).getRoute());
        assertTrue(engine.decide(ctx).getHitRules().contains("FRAUD_REJECT"));
    }

    @Test
    void decide_manualReview_mediumRiskScore() {
        CreditDecisionContext ctx = baseEligibleContext()
                .agentRiskLevel(UserRiskLevel.MEDIUM)
                .riskScoreResult(RiskScoreResult.builder()
                        .riskScore(55)
                        .riskLevel(UserRiskLevel.MEDIUM)
                        .hitRules(Collections.singletonList("RISK_SCORE_MEDIUM"))
                        .build())
                .build();

        when(productApprovalCalculator.calculate(any())).thenAnswer(inv -> {
            CreditDecisionContext c = inv.getArgument(0);
            return new ProductApprovalCalculator.ProductTermsResult(
                    new CreditProduct(), new JSONObject(), UserRiskLevel.MEDIUM,
                    c.getApplyAmount().multiply(new BigDecimal("0.6")),
                    12, new BigDecimal("6.0"), Arrays.asList("AGENT_RISK_MEDIUM"), Arrays.asList("中风险降额"));
        });

        CreditDecision decision = engine.decide(ctx);
        assertEquals(CreditDecision.APPROVED, decision.getRoute());
        assertTrue(decision.getApprovedAmount().compareTo(ctx.getApplyAmount()) < 0);
    }

    @Test
    void decide_manualReview_agentConflict() {
        CreditDecisionContext ctx = baseEligibleContext()
                .conflictDetected(true)
                .riskScoreResult(lowRiskScore())
                .build();

        assertEquals(CreditDecision.MANUAL_REVIEW, engine.decide(ctx).getRoute());
        assertTrue(engine.decide(ctx).getHitRules().contains("AGENT_CONFLICT"));
    }

    @Test
    void decide_manualReview_mcpTimeout() {
        CreditDecisionContext ctx = baseEligibleContext()
                .bureauUnavailable(true)
                .build();

        assertEquals(CreditDecision.MANUAL_REVIEW, engine.decide(ctx).getRoute());
        assertTrue(engine.decide(ctx).getHitRules().contains("BUREAU_UNAVAILABLE"));
    }

    @Test
    void decide_manualReview_lowConfidence() {
        CreditDecisionContext ctx = baseEligibleContext()
                .minAgentConfidence(0.5)
                .riskScoreResult(lowRiskScore())
                .build();

        assertEquals(CreditDecision.MANUAL_REVIEW, engine.decide(ctx).getRoute());
        assertTrue(engine.decide(ctx).getHitRules().contains("LOW_AGENT_CONFIDENCE"));
    }

    @Test
    void decide_manualReview_whenProductUnavailable() {
        when(productApprovalCalculator.calculate(any())).thenReturn(
                new ProductApprovalCalculator.ProductTermsResult(
                        null, null, UserRiskLevel.HIGH, null, null, null,
                        Collections.singletonList("PRODUCT_UNAVAILABLE"),
                        Collections.singletonList("产品不存在")));
        CreditDecisionContext ctx = baseEligibleContext().build();
        assertEquals(CreditDecision.MANUAL_REVIEW, engine.decide(ctx).getRoute());
    }

    private CreditDecisionContext.CreditDecisionContextBuilder baseEligibleContext() {
        UserMemory user = new UserMemory();
        user.setRiskLevel(UserRiskLevel.LOW);
        user.setComplaintCount7d(0);
        return CreditDecisionContext.builder()
                .verifiedDocuments(true)
                .creditEligible(true)
                .bureauUnavailable(false)
                .minAgentConfidence(0.92)
                .consensusSuggestion(AgentSuggestion.SUGGEST_APPROVE)
                .agentRiskLevel(UserRiskLevel.LOW)
                .fraudScore(10)
                .userMemory(user)
                .recentApplyCount7d(0)
                .applyAmount(new BigDecimal("30000"))
                .productId(1L);
    }

    private RiskScoreResult lowRiskScore() {
        return RiskScoreResult.builder()
                .riskScore(25)
                .riskLevel(UserRiskLevel.LOW)
                .hitRules(Collections.singletonList("AUTO_APPROVE"))
                .build();
    }
}
