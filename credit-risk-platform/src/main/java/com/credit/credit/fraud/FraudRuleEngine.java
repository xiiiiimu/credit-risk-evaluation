package com.credit.credit.fraud;

import cn.hutool.json.JSONObject;
import com.credit.credit.fraud.dto.FraudRuleResult;
import com.credit.credit.fraud.dto.FraudSignalDTO;
import com.credit.agent.risk.enums.UserRiskLevel;
import com.credit.platformconfig.service.RuleConfigService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Component
public class FraudRuleEngine {

    private static final String RULE_CODE = "FRAUD_SIGNAL_WEIGHTS";

    @Resource
    private RuleConfigService ruleConfigService;

    public FraudRuleResult evaluate(FraudSignalDTO signal) {
        JSONObject rules = ruleConfigService.getEnabledJson(RULE_CODE);
        int score = 0;
        List<String> hitRules = new ArrayList<>();
        if (signal == null) {
            return FraudRuleResult.builder().fraudScore(0).fraudLevel(UserRiskLevel.LOW)
                    .hitRules(hitRules).build();
        }
        if (signal.isIpAbnormal()) {
            score += weight(rules, "ipAbnormal", 15);
            hitRules.add("IP_ABNORMAL");
        }
        if (signal.isDeviceAbnormal()) {
            score += weight(rules, "deviceAbnormal", 20);
            hitRules.add("DEVICE_ABNORMAL");
        }
        if (signal.isProxyIp()) {
            score += weight(rules, "proxyIp", 20);
            hitRules.add("PROXY_IP");
        }
        if (signal.isMultiAccountDevice()) {
            score += weight(rules, "multiAccountDevice", 25);
            hitRules.add("MULTI_ACCOUNT_DEVICE");
        }
        if (signal.isGeoVelocityAbnormal()) {
            score += weight(rules, "geoVelocityAbnormal", 20);
            hitRules.add("GEO_VELOCITY_ABNORMAL");
        }
        if (signal.isHighFrequencyApply()) {
            score += weight(rules, "highFrequencyApply", 20);
            hitRules.add("HIGH_FREQUENCY_APPLY");
        }
        if (signal.isBlacklistHit()) {
            score += weight(rules, "blacklistHit", 50);
            hitRules.add("BLACKLIST_HIT");
        }
        score = Math.min(100, score);
        int highThreshold = ruleConfigService.getInt(rules, "highThreshold", 70);
        int mediumThreshold = ruleConfigService.getInt(rules, "mediumThreshold", 40);
        String level = score >= highThreshold ? UserRiskLevel.HIGH
                : (score >= mediumThreshold ? UserRiskLevel.MEDIUM : UserRiskLevel.LOW);
        return FraudRuleResult.builder()
                .fraudScore(score)
                .fraudLevel(level)
                .hitRules(hitRules)
                .build();
    }

    private int weight(JSONObject rules, String key, int defaultValue) {
        return ruleConfigService.getInt(rules, key, defaultValue);
    }
}
