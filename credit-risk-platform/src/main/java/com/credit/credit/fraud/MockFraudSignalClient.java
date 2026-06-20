package com.credit.credit.fraud;

import com.credit.credit.fraud.dto.FraudSignalDTO;
import org.springframework.stereotype.Component;

/**
 * Mock 安全风控服务：根据 userId / content 关键字模拟结构化反欺诈信号。
 */
@Component
public class MockFraudSignalClient implements FraudSignalClient {

    @Override
    public FraudSignalDTO fetchSignals(Long userId, Long applicationId, String contentHint) {
        String hint = contentHint != null ? contentHint.toLowerCase() : "";
        boolean highRisk = hint.contains("high_risk") || hint.contains("欺诈") || hint.contains("设备异常");
        boolean deviceAbnormal = highRisk || hint.contains("device_abnormal") || (userId != null && userId % 17 == 0);
        boolean ipAbnormal = highRisk || hint.contains("ip_abnormal");
        boolean multiAccount = highRisk || hint.contains("multi_account");
        boolean proxyIp = hint.contains("proxy");
        boolean geoVelocity = hint.contains("geo_velocity");
        boolean highFreq = hint.contains("high_frequency") || hint.contains("频繁申请");
        boolean blacklist = hint.contains("blacklist") || hint.contains("黑名单");

        String level = blacklist || highRisk ? "HIGH" : (deviceAbnormal || multiAccount ? "MEDIUM" : "LOW");
        return FraudSignalDTO.builder()
                .userId(userId)
                .applicationId(applicationId)
                .ipAbnormal(ipAbnormal)
                .deviceAbnormal(deviceAbnormal)
                .proxyIp(proxyIp)
                .multiAccountDevice(multiAccount)
                .geoVelocityAbnormal(geoVelocity)
                .highFrequencyApply(highFreq)
                .blacklistHit(blacklist)
                .riskLevel(level)
                .confidence(0.86)
                .build();
    }
}
