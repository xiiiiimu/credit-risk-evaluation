package com.credit.credit.risk;

import cn.hutool.json.JSONObject;
import com.credit.credit.risk.dto.RiskScoreInput;
import com.credit.credit.risk.dto.RiskScoreResult;
import com.credit.agent.risk.enums.UserRiskLevel;
import com.credit.platformconfig.service.RuleConfigService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一风险评分：综合征信、欺诈、负债比、材料、Agent 置信度等因素，输出 0-100 分。
 */
@Service
public class CreditRiskScoreService {

    private static final String RULE_CODE = "RISK_SCORE_RULES";

    @Resource
    private RuleConfigService ruleConfigService;

    public RiskScoreResult calculate(RiskScoreInput input) {
        JSONObject rules = ruleConfigService.getEnabledJson(RULE_CODE);
        List<String> factors = new ArrayList<>();
        Set<String> hitRules = new LinkedHashSet<>();
        int score = 0;

        if (input == null) {
            return RiskScoreResult.builder()
                    .riskScore(100)
                    .riskLevel(UserRiskLevel.HIGH)
                    .factors(java.util.Collections.singletonList("评分输入为空"))
                    .hitRules(java.util.Collections.singletonList("NULL_INPUT"))
                    .build();
        }

        if (input.isBureauUnavailable()) {
            score += ruleConfigService.getInt(rules, "bureauUnavailableWeight", 35);
            factors.add("征信服务不可用，需人工复核");
            hitRules.add("BUREAU_UNAVAILABLE");
        }

        int bureauScore = input.getBureauCreditScore() != null ? input.getBureauCreditScore() : 650;
        if (bureauScore < 550) {
            score += ruleConfigService.getInt(rules, "bureauLow550", 30);
            factors.add("征信分数严重偏低");
            hitRules.add("LOW_CREDIT_SCORE");
        } else if (bureauScore < 600) {
            score += ruleConfigService.getInt(rules, "bureauLow600", 20);
            factors.add("征信分数偏低");
            hitRules.add("LOW_CREDIT_SCORE");
        } else if (bureauScore < 650) {
            score += ruleConfigService.getInt(rules, "bureauLow650", 10);
            factors.add("近6个月存在逾期或征信波动");
            hitRules.add("CREDIT_SCORE_MEDIUM");
        }

        double fraudFactor = ruleConfigService.getDouble(rules, "fraudWeightFactor", 0.35);
        score += Math.min(30, (int) (input.getFraudScore() * fraudFactor));
        if (input.getFraudScore() >= 50) {
            factors.add("反欺诈分数偏高");
            hitRules.add("HIGH_FRAUD_SCORE");
        }
        if (input.getFraudHitRules() != null) {
            hitRules.addAll(input.getFraudHitRules());
            if (input.getFraudHitRules().contains("DEVICE_ABNORMAL")) {
                factors.add("设备风险信号异常");
            }
            if (input.getFraudHitRules().contains("HIGH_DEBT_RATIO")) {
                factors.add("收入负债比偏高");
            }
        }

        double debtRatio = input.getIncomeDebtRatio() != null ? input.getIncomeDebtRatio() : 0.3;
        if (debtRatio > 0.6) {
            score += ruleConfigService.getInt(rules, "debtHigh", 20);
            factors.add("收入负债比偏高");
            hitRules.add("HIGH_DEBT_RATIO");
        } else if (debtRatio > 0.45) {
            score += ruleConfigService.getInt(rules, "debtMedium", 10);
            factors.add("负债比处于警戒区间");
            hitRules.add("MEDIUM_DEBT_RATIO");
        }

        double docScore = input.getDocumentScore() != null ? input.getDocumentScore() : 0.8;
        score += (int) ((1.0 - docScore) * 15);
        if (docScore < 0.6) {
            factors.add("申请材料完整度不足");
            hitRules.add("INCOMPLETE_DOCUMENTS");
        }

        double minConf = input.getMinAgentConfidence() != null ? input.getMinAgentConfidence() : 0.85;
        if (minConf < 0.85) {
            score += 12;
            factors.add("Agent 分析置信度低于阈值");
            hitRules.add("LOW_AGENT_CONFIDENCE");
        }

        if (input.isHighFrequencyApply()) {
            score += 15;
            factors.add("近7日申请频次过高");
            hitRules.add("HIGH_FREQUENCY_APPLY");
        }

        score = Math.min(100, Math.max(0, score));
        String level = score < 40 ? UserRiskLevel.LOW : (score < 70 ? UserRiskLevel.MEDIUM : UserRiskLevel.HIGH);

        return RiskScoreResult.builder()
                .riskScore(score)
                .riskLevel(level)
                .factors(factors)
                .hitRules(new ArrayList<>(hitRules))
                .build();
    }
}
