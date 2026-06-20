package com.credit.platformconfig.gray;

import cn.hutool.json.JSONObject;
import com.credit.credit.approval.CreditApprovalEngine;
import com.credit.credit.approval.ProductApprovalCalculator;
import com.credit.credit.approval.dto.CreditDecision;
import com.credit.credit.approval.dto.CreditDecisionContext;
import com.credit.credit.entity.CreditProduct;
import com.credit.credit.testsupport.RuleConfigServiceStub;
import com.credit.credit.testsupport.TestAgentRiskProperties;
import com.credit.credit.enums.AgentSuggestion;
import com.credit.credit.risk.CreditRiskScoreService;
import com.credit.credit.risk.dto.RiskScoreResult;
import com.credit.agent.entity.UserMemory;
import com.credit.agent.risk.enums.UserRiskLevel;
import com.credit.product.service.CreditProductService;
import com.credit.product.service.ProductRuleConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 灰度：全局风控规则缺失时使用安全默认值，不允许默认放行。
 */
@ExtendWith(MockitoExtension.class)
class RuleConfigSafeDefaultGrayTest {

    @Mock
    private CreditProductService creditProductService;
    @Mock
    private ProductRuleConfigService productRuleConfigService;
    @InjectMocks
    private ProductApprovalCalculator productCalculator;

    private CreditApprovalEngine approvalEngine;

    @BeforeEach
    void setUp() {
        approvalEngine = new CreditApprovalEngine();
        CreditRiskScoreService riskScoreService = new CreditRiskScoreService();
        ReflectionTestUtils.setField(riskScoreService, "ruleConfigService", new RuleConfigServiceStub());
        ReflectionTestUtils.setField(approvalEngine, "riskProperties", TestAgentRiskProperties.lowRiskAutoApprove());
        ReflectionTestUtils.setField(approvalEngine, "creditRiskScoreService", riskScoreService);
        ReflectionTestUtils.setField(approvalEngine, "ruleConfigService", new RuleConfigServiceStub());
        ReflectionTestUtils.setField(approvalEngine, "productApprovalCalculator", productCalculator);

        CreditProduct product = new CreditProduct();
        product.setId(1L);
        product.setMinAmount(new BigDecimal("1000"));
        product.setMaxAmount(new BigDecimal("200000"));
        product.setInterestRate(new BigDecimal("4.8"));
        product.setSupportedTermsJson("[12]");
        product.setStatus(CreditProductService.STATUS_ACTIVE);
        when(creditProductService.getActiveProduct(1L)).thenReturn(product);
        when(creditProductService.parseSupportedTerms(product)).thenReturn(Collections.singletonList(12));
        when(productRuleConfigService.getEnabledJson(1L)).thenReturn(new ProductRuleConfigService().defaultSafeRules());
    }

    @Test
    void gray_missingProductRuleConfig_highRiskCannotAutoApprove() {
        UserMemory user = new UserMemory();
        user.setRiskLevel(UserRiskLevel.LOW);
        user.setComplaintCount7d(0);

        CreditDecision decision = approvalEngine.decide(CreditDecisionContext.builder()
                .verifiedDocuments(true)
                .creditEligible(true)
                .userMemory(user)
                .consensusSuggestion(AgentSuggestion.SUGGEST_APPROVE)
                .agentRiskLevel(UserRiskLevel.HIGH)
                .fraudScore(10)
                .minAgentConfidence(0.9)
                .applyAmount(new BigDecimal("50000"))
                .applyTerm(12)
                .productId(1L)
                .riskScoreResult(RiskScoreResult.builder()
                        .riskScore(30)
                        .riskLevel(UserRiskLevel.LOW)
                        .hitRules(Collections.singletonList("AUTO_APPROVE"))
                        .build())
                .build());

        assertEquals(CreditDecision.MANUAL_REVIEW, decision.getRoute());
        assertTrue(decision.getHitRules().contains("USER_OR_AGENT_RISK_HIGH"));
    }
}
