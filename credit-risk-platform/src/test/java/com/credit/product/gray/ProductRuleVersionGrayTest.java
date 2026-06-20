package com.credit.product.gray;

import cn.hutool.json.JSONObject;
import com.credit.credit.approval.ProductApprovalCalculator;
import com.credit.credit.approval.dto.CreditDecisionContext;
import com.credit.credit.entity.CreditProduct;
import com.credit.credit.testsupport.MutableProductRuleConfigService;
import com.credit.agent.risk.enums.UserRiskLevel;
import com.credit.product.service.CreditProductService;
import com.credit.product.service.ProductRuleConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 灰度：产品规则版本切换与回滚对终审额度/利率的影响。
 */
@ExtendWith(MockitoExtension.class)
class ProductRuleVersionGrayTest {

    @Mock
    private CreditProductService creditProductService;

    private final MutableProductRuleConfigService productRuleConfigService = new MutableProductRuleConfigService();
    private ProductApprovalCalculator calculator;
    private CreditProduct product;

    @BeforeEach
    void setUp() {
        calculator = new ProductApprovalCalculator();
        ReflectionTestUtils.setField(calculator, "creditProductService", creditProductService);
        ReflectionTestUtils.setField(calculator, "productRuleConfigService", productRuleConfigService);

        product = new CreditProduct();
        product.setId(1L);
        product.setMinAmount(new BigDecimal("1000"));
        product.setMaxAmount(new BigDecimal("200000"));
        product.setInterestRate(new BigDecimal("4.8"));
        product.setSupportedTermsJson("[12,24]");
        product.setStatus(CreditProductService.STATUS_ACTIVE);

        when(creditProductService.getActiveProduct(1L)).thenReturn(product);
        when(creditProductService.parseSupportedTerms(product)).thenReturn(Arrays.asList(12, 24));
    }

    @Test
    void gray_productRuleV1_thenV2_producesDifferentApprovedTerms() {
        CreditDecisionContext ctx = CreditDecisionContext.builder()
                .productId(1L)
                .applyAmount(new BigDecimal("150000"))
                .applyTerm(24)
                .agentRiskLevel(UserRiskLevel.MEDIUM)
                .build();

        productRuleConfigService.setEnabledJson(rulesV1());
        BigDecimal v1Amount = calculator.calculate(ctx).getApprovedAmount();
        BigDecimal v1Rate = calculator.calculate(ctx).getApprovedRate();

        productRuleConfigService.setEnabledJson(rulesV2());
        BigDecimal v2Amount = calculator.calculate(ctx).getApprovedAmount();
        BigDecimal v2Rate = calculator.calculate(ctx).getApprovedRate();

        assertTrue(v1Amount.compareTo(v2Amount) > 0, "V1 中风险额度应高于 V2");
        assertTrue(v2Rate.compareTo(v1Rate) > 0, "V2 中风险利率应高于 V1");
    }

    @Test
    void gray_rollbackToV1_restoresPreviousApprovalCalculation() {
        CreditDecisionContext ctx = CreditDecisionContext.builder()
                .productId(1L)
                .applyAmount(new BigDecimal("150000"))
                .applyTerm(12)
                .agentRiskLevel(UserRiskLevel.MEDIUM)
                .build();

        productRuleConfigService.setEnabledJson(rulesV1());
        BigDecimal baseline = calculator.calculate(ctx).getApprovedAmount();

        productRuleConfigService.setEnabledJson(rulesV2());
        assertNotEquals(baseline, calculator.calculate(ctx).getApprovedAmount());

        productRuleConfigService.setEnabledJson(rulesV1());
        assertEquals(0, baseline.compareTo(calculator.calculate(ctx).getApprovedAmount()));
    }

    @Test
    void gray_missingProductRule_usesSafeDefault_notAutoPass() {
        productRuleConfigService.setEnabledJson(new ProductRuleConfigService().defaultSafeRules());

        CreditDecisionContext highRisk = CreditDecisionContext.builder()
                .productId(1L)
                .applyAmount(new BigDecimal("50000"))
                .applyTerm(12)
                .agentRiskLevel(UserRiskLevel.HIGH)
                .build();

        ProductApprovalCalculator.ProductTermsResult result = calculator.calculate(highRisk);
        assertTrue(result.getProductHitRules().contains("AGENT_RISK_HIGH"));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                result.getApprovedAmount() != null ? result.getApprovedAmount() : BigDecimal.ZERO));
    }

    private JSONObject rulesV1() {
        JSONObject json = new JSONObject();
        json.set("riskLevelAmountRatio", cn.hutool.json.JSONUtil.parseObj("{\"LOW\":1.0,\"MEDIUM\":0.8,\"HIGH\":0.0}"));
        json.set("riskLevelRateAdjustment", cn.hutool.json.JSONUtil.parseObj("{\"LOW\":0.0,\"MEDIUM\":0.5,\"HIGH\":0.0}"));
        json.set("rejectThreshold", 80);
        json.set("manualReviewThreshold", 60);
        json.set("maxDebtIncomeRatio", 0.5);
        return json;
    }

    private JSONObject rulesV2() {
        JSONObject json = new JSONObject();
        json.set("riskLevelAmountRatio", cn.hutool.json.JSONUtil.parseObj("{\"LOW\":1.0,\"MEDIUM\":0.5,\"HIGH\":0.0}"));
        json.set("riskLevelRateAdjustment", cn.hutool.json.JSONUtil.parseObj("{\"LOW\":0.0,\"MEDIUM\":1.5,\"HIGH\":0.0}"));
        json.set("rejectThreshold", 75);
        json.set("manualReviewThreshold", 55);
        json.set("maxDebtIncomeRatio", 0.45);
        return json;
    }
}
