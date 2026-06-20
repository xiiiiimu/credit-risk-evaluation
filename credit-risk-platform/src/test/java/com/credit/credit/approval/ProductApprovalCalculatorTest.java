package com.credit.credit.approval;

import com.credit.credit.entity.CreditProduct;
import com.credit.agent.risk.enums.UserRiskLevel;
import com.credit.credit.approval.dto.CreditDecisionContext;
import com.credit.product.service.CreditProductService;
import com.credit.product.service.ProductRuleConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductApprovalCalculatorTest {

    @Mock
    private CreditProductService creditProductService;
    @Mock
    private ProductRuleConfigService productRuleConfigService;
    @InjectMocks
    private ProductApprovalCalculator calculator;

    private CreditProduct product;

    @BeforeEach
    void setUp() {
        product = new CreditProduct();
        product.setId(1L);
        product.setMinAmount(new BigDecimal("1000"));
        product.setMaxAmount(new BigDecimal("200000"));
        product.setInterestRate(new BigDecimal("4.8"));
        product.setSupportedTermsJson("[6,12,24,36]");
        product.setStatus(CreditProductService.STATUS_ACTIVE);
        when(creditProductService.getActiveProduct(1L)).thenReturn(product);
        when(creditProductService.parseSupportedTerms(product)).thenReturn(Arrays.asList(6, 12, 24, 36));
        when(productRuleConfigService.getEnabledJson(1L)).thenReturn(new ProductRuleConfigService().defaultSafeRules());
    }

    @Test
    void calculate_mediumRisk_reducesAmount() {
        CreditDecisionContext ctx = CreditDecisionContext.builder()
                .productId(1L)
                .applyAmount(new BigDecimal("100000"))
                .applyTerm(24)
                .agentRiskLevel(UserRiskLevel.MEDIUM)
                .monthlyIncome(new BigDecimal("8000"))
                .build();
        ProductApprovalCalculator.ProductTermsResult result = calculator.calculate(ctx);
        assertTrue(result.getApprovedAmount().compareTo(new BigDecimal("100000")) < 0);
        assertEquals(Integer.valueOf(24), result.getApprovedTerm());
    }

    @Test
    void calculate_unsupportedTerm_flagsRule() {
        CreditDecisionContext ctx = CreditDecisionContext.builder()
                .productId(1L)
                .applyAmount(new BigDecimal("50000"))
                .applyTerm(48)
                .agentRiskLevel(UserRiskLevel.LOW)
                .build();
        ProductApprovalCalculator.ProductTermsResult result = calculator.calculate(ctx);
        assertTrue(result.getProductHitRules().contains("UNSUPPORTED_TERM"));
    }
}
