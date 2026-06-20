package com.credit.credit.approval;

import cn.hutool.json.JSONObject;
import com.credit.credit.approval.dto.CreditDecisionContext;
import com.credit.credit.entity.CreditProduct;
import com.credit.agent.risk.enums.UserRiskLevel;
import com.credit.product.service.CreditProductService;
import com.credit.product.service.ProductRuleConfigService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProductApprovalCalculator {

    @Resource
    private CreditProductService creditProductService;
    @Resource
    private ProductRuleConfigService productRuleConfigService;

    public static class ProductTermsResult {
        private final CreditProduct product;
        private final JSONObject rules;
        private final String agentRiskLevel;
        private final BigDecimal approvedAmount;
        private final Integer approvedTerm;
        private final BigDecimal approvedRate;
        private final List<String> productHitRules;
        private final List<String> productReasons;

        public ProductTermsResult(CreditProduct product, JSONObject rules, String agentRiskLevel,
                                BigDecimal approvedAmount, Integer approvedTerm, BigDecimal approvedRate,
                                List<String> productHitRules, List<String> productReasons) {
            this.product = product;
            this.rules = rules;
            this.agentRiskLevel = agentRiskLevel;
            this.approvedAmount = approvedAmount;
            this.approvedTerm = approvedTerm;
            this.approvedRate = approvedRate;
            this.productHitRules = productHitRules;
            this.productReasons = productReasons;
        }

        public CreditProduct getProduct() { return product; }
        public JSONObject getRules() { return rules; }
        public String getAgentRiskLevel() { return agentRiskLevel; }
        public BigDecimal getApprovedAmount() { return approvedAmount; }
        public Integer getApprovedTerm() { return approvedTerm; }
        public BigDecimal getApprovedRate() { return approvedRate; }
        public List<String> getProductHitRules() { return productHitRules; }
        public List<String> getProductReasons() { return productReasons; }
    }

    public CreditProduct requireActiveProduct(Long productId) {
        return creditProductService.getActiveProduct(productId);
    }

    public ProductTermsResult calculate(CreditDecisionContext ctx) {
        List<String> hitRules = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        CreditProduct product = requireActiveProduct(ctx.getProductId());
        if (product == null) {
            hitRules.add("PRODUCT_UNAVAILABLE");
            reasons.add("产品不存在或已下线");
            return new ProductTermsResult(null, null, UserRiskLevel.HIGH, null, null, null, hitRules, reasons);
        }

        JSONObject rules = productRuleConfigService.getEnabledJson(product.getId());
        String riskLevel = resolveAgentRiskLevel(ctx);
        BigDecimal applyAmount = ctx.getApplyAmount() != null ? ctx.getApplyAmount() : BigDecimal.ZERO;
        Integer applyTerm = ctx.getApplyTerm() != null ? ctx.getApplyTerm() : product.getTermMonths();

        if (applyAmount.compareTo(product.getMinAmount()) < 0) {
            hitRules.add("APPLY_AMOUNT_BELOW_MIN");
            reasons.add("申请金额低于产品最小额度");
        }
        if (applyAmount.compareTo(product.getMaxAmount()) > 0) {
            hitRules.add("APPLY_AMOUNT_ABOVE_MAX");
            reasons.add("申请金额超过产品最大额度");
        }

        List<Integer> supported = creditProductService.parseSupportedTerms(product);
        if (applyTerm != null && !supported.contains(applyTerm)) {
            hitRules.add("UNSUPPORTED_TERM");
            reasons.add("申请期限不在产品支持范围内");
        }

        double amountRatio = productRuleConfigService.getRatio(rules, riskLevel, 0.0);
        double rateAdj = productRuleConfigService.getRateAdjustment(rules, riskLevel, 0.0);
        double maxDebtRatio = rules != null ? rules.getDouble("maxDebtIncomeRatio", 0.5) : 0.5;

        BigDecimal capByProduct = product.getMaxAmount().multiply(BigDecimal.valueOf(amountRatio));
        BigDecimal capByApply = applyAmount;
        BigDecimal capByIncome = capByIncome(ctx.getMonthlyIncome(), maxDebtRatio, applyTerm);
        BigDecimal approvedAmount = min(capByApply, capByProduct, capByIncome);
        if (approvedAmount.compareTo(product.getMinAmount()) < 0 && amountRatio > 0) {
            approvedAmount = product.getMinAmount().min(capByApply);
        }

        Integer approvedTerm = normalizeTerm(applyTerm, supported, riskLevel);
        BigDecimal approvedRate = product.getInterestRate().add(BigDecimal.valueOf(rateAdj))
                .setScale(4, RoundingMode.HALF_UP);

        if (UserRiskLevel.HIGH.equals(riskLevel)) {
            hitRules.add("AGENT_RISK_HIGH");
            reasons.add("Agent 风险等级为 HIGH");
        } else if (UserRiskLevel.MEDIUM.equals(riskLevel)) {
            hitRules.add("AGENT_RISK_MEDIUM");
            reasons.add("中风险用户按产品规则降额/期限/利率调整");
        }

        return new ProductTermsResult(product, rules, riskLevel, approvedAmount, approvedTerm, approvedRate, hitRules, reasons);
    }

    private String resolveAgentRiskLevel(CreditDecisionContext ctx) {
        if (ctx.getAgentRiskLevel() != null) {
            return ctx.getAgentRiskLevel();
        }
        if (ctx.getRiskScoreResult() != null && ctx.getRiskScoreResult().getRiskLevel() != null) {
            return ctx.getRiskScoreResult().getRiskLevel();
        }
        return UserRiskLevel.MEDIUM;
    }

    private BigDecimal capByIncome(BigDecimal monthlyIncome, double maxDebtRatio, Integer termMonths) {
        if (monthlyIncome == null || termMonths == null || termMonths <= 0) {
            return null;
        }
        double monthlyPaymentCapacity = monthlyIncome.doubleValue() * maxDebtRatio;
        double total = monthlyPaymentCapacity * termMonths;
        return BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP);
    }

    private Integer normalizeTerm(Integer applyTerm, List<Integer> supported, String riskLevel) {
        if (supported == null || supported.isEmpty()) {
            return applyTerm != null ? applyTerm : 12;
        }
        if (applyTerm != null && supported.contains(applyTerm)) {
            if (UserRiskLevel.MEDIUM.equals(riskLevel)) {
                return supported.stream().filter(t -> t <= applyTerm).max(Integer::compareTo).orElse(applyTerm);
            }
            return applyTerm;
        }
        return supported.get(0);
    }

    private BigDecimal min(BigDecimal... values) {
        BigDecimal result = null;
        for (BigDecimal v : values) {
            if (v == null) {
                continue;
            }
            result = result == null ? v : result.min(v);
        }
        return result;
    }
}
