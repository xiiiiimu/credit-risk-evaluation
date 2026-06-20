package com.credit.credit.approval;

import com.credit.agent.config.AgentRiskProperties;
import com.credit.credit.approval.dto.CreditDecision;
import com.credit.credit.approval.dto.CreditDecisionContext;
import com.credit.credit.enums.AgentSuggestion;
import com.credit.credit.risk.CreditRiskScoreService;
import com.credit.credit.risk.dto.RiskScoreInput;
import com.credit.credit.risk.dto.RiskScoreResult;
import com.credit.agent.entity.UserMemory;
import com.credit.agent.risk.enums.UserRiskLevel;
import cn.hutool.json.JSONObject;
import com.credit.platformconfig.service.RuleConfigService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 信贷终审规则引擎（唯一终审权）。
 * Agent 仅输出 SUGGEST_* 与风险分析；额度/期限/利率由本引擎结合动态产品规则计算。
 */
@Component
public class CreditApprovalEngine {

    private static final String RULE_CODE = "CREDIT_APPROVAL_RULES";

    @Resource
    private AgentRiskProperties riskProperties;
    @Resource
    private CreditRiskScoreService creditRiskScoreService;
    @Resource
    private RuleConfigService ruleConfigService;
    @Resource
    private ProductApprovalCalculator productApprovalCalculator;

    public CreditDecision decide(CreditDecisionContext ctx) {
        JSONObject rules = ruleConfigService.getEnabledJson(RULE_CODE);
        int fraudRejectThreshold = ruleConfigService.getInt(rules, "fraudRejectThreshold", 80);
        int riskScoreReject = ruleConfigService.getInt(rules, "riskScoreReject", 85);
        int riskScoreManualHigh = ruleConfigService.getInt(rules, "riskScoreManualHigh", 70);
        int riskScoreManualMedium = ruleConfigService.getInt(rules, "riskScoreManualMedium", 40);
        Map<String, Object> audit = new LinkedHashMap<>();
        Set<String> hitRules = new LinkedHashSet<>();
        List<String> decisionReasons = new ArrayList<>();

        if (ctx == null) {
            return buildDecision(CreditDecision.REJECTED, null, null, null,
                    "上下文为空", audit, 100, UserRiskLevel.HIGH,
                    hitRules("NULL_CONTEXT"), list("上下文为空"), false);
        }

        ProductApprovalCalculator.ProductTermsResult productTerms = productApprovalCalculator.calculate(ctx);
        if (productTerms.getProduct() == null) {
            hitRules.addAll(productTerms.getProductHitRules());
            decisionReasons.addAll(productTerms.getProductReasons());
            audit.put("blockedBy", "productUnavailable");
            return buildDecision(CreditDecision.MANUAL_REVIEW, null, null, null,
                    "产品不可用", audit, null, UserRiskLevel.HIGH, hitRules, decisionReasons, true);
        }
        hitRules.addAll(productTerms.getProductHitRules());
        decisionReasons.addAll(productTerms.getProductReasons());

        if (!ctx.isVerifiedDocuments()) {
            hitRules.add("INCOMPLETE_DOCUMENTS");
            decisionReasons.add("申请材料不完整，无法自动审批");
            audit.put("blockedBy", "verifiedDocuments");
            return buildDecision(CreditDecision.REJECTED, null, null, null,
                    "资料不完整", audit, 85, UserRiskLevel.HIGH, hitRules, decisionReasons, false);
        }

        if (Boolean.TRUE.equals(ctx.getBureauUnavailable())) {
            hitRules.add("BUREAU_UNAVAILABLE");
            decisionReasons.add("征信服务不可用，需人工复核");
            audit.put("blockedBy", "bureauUnavailable");
            return buildDecision(CreditDecision.MANUAL_REVIEW, null, null, null,
                    "征信服务不可用", audit, null, null, hitRules, decisionReasons, true);
        }

        if (!Boolean.TRUE.equals(ctx.getCreditEligible())) {
            hitRules.add("NOT_CREDIT_ELIGIBLE");
            decisionReasons.add("不满足信贷准入条件");
            audit.put("blockedBy", "creditEligible");
            return buildDecision(CreditDecision.REJECTED, null, null, null,
                    "不满足信贷准入", audit, 80, UserRiskLevel.HIGH, hitRules, decisionReasons, false);
        }

        RiskScoreResult risk = ctx.getRiskScoreResult();
        if (risk == null) {
            risk = creditRiskScoreService.calculate(buildRiskInput(ctx));
        }
        if (risk.getHitRules() != null) {
            hitRules.addAll(risk.getHitRules());
        }
        if (risk.getFactors() != null) {
            decisionReasons.addAll(risk.getFactors());
        }

        UserMemory user = ctx.getUserMemory();
        String userRisk = user != null && user.getRiskLevel() != null ? user.getRiskLevel() : UserRiskLevel.MEDIUM;
        String agentRisk = productTerms.getAgentRiskLevel();

        if (AgentSuggestion.SUGGEST_REJECT.equals(ctx.getConsensusSuggestion())
                || AgentSuggestion.SUGGEST_REJECT.equals(ctx.getAgentSuggestion())) {
            hitRules.add("AGENT_SUGGEST_REJECT");
            decisionReasons.add("Agent 共识建议拒绝");
            audit.put("blockedBy", "agentSuggestReject");
            return buildDecision(CreditDecision.REJECTED, null, null, null,
                    "Agent建议拒绝", audit, risk.getRiskScore(), agentRisk, hitRules, decisionReasons, false);
        }

        if (Boolean.TRUE.equals(ctx.getConflictDetected())
                || (ctx.getAgentConflicts() != null && !ctx.getAgentConflicts().isEmpty())) {
            hitRules.add("AGENT_CONFLICT");
            decisionReasons.add("Multi-Agent 意见冲突，转人工复核");
            audit.put("blockedBy", "agentConflict");
            return buildDecision(CreditDecision.MANUAL_REVIEW, null, null, null,
                    "Agent冲突", audit, risk.getRiskScore(), agentRisk, hitRules, decisionReasons, true);
        }

        double minConf = ctx.getMinAgentConfidence() != null ? ctx.getMinAgentConfidence() : 0;
        if (minConf < riskProperties.getAutoMinConfidence()) {
            hitRules.add("LOW_AGENT_CONFIDENCE");
            decisionReasons.add("Agent 分析置信度低于阈值");
            audit.put("blockedBy", "lowConfidence");
            return buildDecision(CreditDecision.MANUAL_REVIEW, null, null, null,
                    "置信度过低", audit, risk.getRiskScore(), agentRisk, hitRules, decisionReasons, true);
        }

        if (UserRiskLevel.HIGH.equals(userRisk) || UserRiskLevel.HIGH.equals(agentRisk)) {
            hitRules.add("USER_OR_AGENT_RISK_HIGH");
            decisionReasons.add("借款人或 Agent 风险等级为 HIGH");
            audit.put("blockedBy", "riskHigh");
            return buildDecision(CreditDecision.MANUAL_REVIEW, null, null, null,
                    "高风险", audit, risk.getRiskScore(), agentRisk, hitRules, decisionReasons, true);
        }

        if (ctx.getFraudScore() != null && ctx.getFraudScore() >= fraudRejectThreshold) {
            hitRules.add("FRAUD_REJECT");
            decisionReasons.add("反欺诈分数达到拒绝阈值");
            audit.put("blockedBy", "fraudReject");
            return buildDecision(CreditDecision.REJECTED, null, null, null,
                    "反欺诈命中拒绝阈值", audit, risk.getRiskScore(), agentRisk, hitRules, decisionReasons, false);
        }

        if (Boolean.TRUE.equals(ctx.getNeedManualReview())
                || AgentSuggestion.SUGGEST_MANUAL.equals(ctx.getConsensusSuggestion())) {
            hitRules.add("AGENT_SUGGEST_MANUAL");
            decisionReasons.add("Agent 建议人工复核");
            audit.put("blockedBy", "agentNeedManual");
            return buildDecision(CreditDecision.MANUAL_REVIEW, null, null, null,
                    "Agent建议人工", audit, risk.getRiskScore(), agentRisk, hitRules, decisionReasons, true);
        }

        int riskScore = risk.getRiskScore();
        audit.put("riskScore", riskScore);
        audit.put("riskLevel", risk.getRiskLevel());
        audit.put("agentRiskLevel", agentRisk);
        audit.put("productId", ctx.getProductId());

        JSONObject productRules = productTerms.getRules();
        int manualThreshold = productRules != null ? productRules.getInt("manualReviewThreshold", 60) : 60;
        int rejectThreshold = productRules != null ? productRules.getInt("rejectThreshold", 80) : 80;

        if (riskScore >= rejectThreshold || riskScore >= riskScoreReject) {
            hitRules.add("RISK_SCORE_REJECT");
            decisionReasons.add("综合风险分过高，系统自动拒绝");
            audit.put("blockedBy", "riskScoreReject");
            return buildDecision(CreditDecision.REJECTED, null, null, null,
                    "风险分过高", audit, riskScore, agentRisk, hitRules, decisionReasons, false);
        }

        if (riskScore >= manualThreshold || riskScore >= riskScoreManualHigh) {
            hitRules.add("RISK_SCORE_HIGH");
            decisionReasons.add("综合风险分偏高，建议人工复核");
            audit.put("blockedBy", "riskScoreManual");
            return buildDecision(CreditDecision.MANUAL_REVIEW, null, null, null,
                    "风险分偏高", audit, riskScore, agentRisk, hitRules, decisionReasons, true);
        }

        if (riskScore >= riskScoreManualMedium || UserRiskLevel.MEDIUM.equals(agentRisk)) {
            hitRules.add("RISK_SCORE_MEDIUM");
            decisionReasons.add("中风险：按产品规则降额/缩期/利率上浮后审批");
            audit.put("blockedBy", "riskScoreMedium");
            return buildDecision(CreditDecision.APPROVED,
                    productTerms.getApprovedAmount(),
                    productTerms.getApprovedRate(),
                    productTerms.getApprovedTerm(),
                    "中风险降额审批", audit, riskScore, agentRisk, hitRules, decisionReasons, false);
        }

        String autoBlocker = evaluateAutoApproveBlocker(ctx, user);
        if (autoBlocker != null) {
            hitRules.add(autoBlocker.toUpperCase());
            decisionReasons.add("未满足自动审批条件：" + autoBlocker);
            audit.put("blockedBy", autoBlocker);
            return buildDecision(CreditDecision.MANUAL_REVIEW, null, null, null,
                    "规则未满足自动审批", audit, riskScore, agentRisk, hitRules, decisionReasons, true);
        }

        if (hitRules.contains("APPLY_AMOUNT_ABOVE_MAX") || hitRules.contains("UNSUPPORTED_TERM")) {
            return buildDecision(CreditDecision.MANUAL_REVIEW,
                    productTerms.getApprovedAmount(),
                    productTerms.getApprovedRate(),
                    productTerms.getApprovedTerm(),
                    "申请参数与产品规则不匹配", audit, riskScore, agentRisk, hitRules, decisionReasons, true);
        }

        decisionReasons.add("综合风险分较低，满足自动审批条件");
        hitRules.add("AUTO_APPROVE");
        audit.put("blockedBy", null);
        return buildDecision(CreditDecision.APPROVED,
                productTerms.getApprovedAmount(),
                productTerms.getApprovedRate(),
                productTerms.getApprovedTerm(),
                "AUTO_LOW_RISK", audit, riskScore, agentRisk, hitRules, decisionReasons, false);
    }

    private RiskScoreInput buildRiskInput(CreditDecisionContext ctx) {
        return RiskScoreInput.builder()
                .bureauCreditScore(ctx.getBureauCreditScore())
                .fraudScore(ctx.getFraudScore() != null ? ctx.getFraudScore() : 0)
                .incomeDebtRatio(ctx.getIncomeDebtRatio())
                .documentScore(ctx.getDocumentScore())
                .minAgentConfidence(ctx.getMinAgentConfidence())
                .highFrequencyApply(ctx.isHighFrequencyApply())
                .bureauUnavailable(Boolean.TRUE.equals(ctx.getBureauUnavailable()))
                .fraudHitRules(ctx.getFraudHitRules())
                .build();
    }

    private String evaluateAutoApproveBlocker(CreditDecisionContext ctx, UserMemory user) {
        if (user == null || !UserRiskLevel.LOW.equals(user.getRiskLevel())) {
            return "userRiskNotLow";
        }
        int apply7d = ctx.getRecentApplyCount7d() != null ? ctx.getRecentApplyCount7d()
                : (user.getComplaintCount7d() != null ? user.getComplaintCount7d() : 0);
        if (apply7d > riskProperties.getAutoMaxComplaint7d()) {
            return "applyCount7dExceeded";
        }
        String suggestion = ctx.getConsensusSuggestion() != null ? ctx.getConsensusSuggestion() : ctx.getAgentSuggestion();
        if (!AgentSuggestion.SUGGEST_APPROVE.equals(suggestion)) {
            return "consensusNotSuggestApprove";
        }
        return null;
    }

    private CreditDecision buildDecision(String route, BigDecimal amount, BigDecimal rate, Integer term,
                                         String reason, Map<String, Object> audit,
                                         Integer riskScore, String riskLevel,
                                         Set<String> hitRules, List<String> decisionReasons,
                                         boolean needManual) {
        List<String> rules = new ArrayList<>(hitRules);
        return CreditDecision.builder()
                .route(route)
                .approvedAmount(amount)
                .approvedRate(rate)
                .approvedTerm(term)
                .needManualReview(needManual)
                .reason(reason)
                .audit(audit)
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .hitRules(rules)
                .decisionReason(decisionReasons)
                .decisionReasonSummary(reason)
                .build();
    }

    private Set<String> hitRules(String... rules) {
        Set<String> set = new LinkedHashSet<>();
        for (String r : rules) {
            set.add(r);
        }
        return set;
    }

    private List<String> list(String... items) {
        List<String> list = new ArrayList<>();
        for (String i : items) {
            list.add(i);
        }
        return list;
    }
}
