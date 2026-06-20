package com.credit.credit.fraud;

import com.credit.credit.fraud.dto.FraudRuleResult;
import com.credit.credit.fraud.dto.FraudSignalDTO;
import com.credit.agent.risk.enums.UserRiskLevel;
import com.credit.credit.testsupport.RuleConfigServiceStub;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FraudRuleEngineTest {

    private FraudRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FraudRuleEngine();
        ReflectionTestUtils.setField(engine, "ruleConfigService", new RuleConfigServiceStub());
    }

    @Test
    void evaluate_ipAbnormal() {
        FraudSignalDTO signal = FraudSignalDTO.builder().ipAbnormal(true).build();
        FraudRuleResult result = engine.evaluate(signal);
        assertEquals(15, result.getFraudScore());
        assertTrue(result.getHitRules().contains("IP_ABNORMAL"));
    }

    @Test
    void evaluate_deviceAbnormal() {
        FraudSignalDTO signal = FraudSignalDTO.builder().deviceAbnormal(true).build();
        FraudRuleResult result = engine.evaluate(signal);
        assertEquals(20, result.getFraudScore());
        assertTrue(result.getHitRules().contains("DEVICE_ABNORMAL"));
    }

    @Test
    void evaluate_proxyIp() {
        FraudSignalDTO signal = FraudSignalDTO.builder().proxyIp(true).build();
        FraudRuleResult result = engine.evaluate(signal);
        assertEquals(20, result.getFraudScore());
        assertTrue(result.getHitRules().contains("PROXY_IP"));
    }

    @Test
    void evaluate_multiAccountDevice() {
        FraudSignalDTO signal = FraudSignalDTO.builder().multiAccountDevice(true).build();
        FraudRuleResult result = engine.evaluate(signal);
        assertEquals(25, result.getFraudScore());
        assertTrue(result.getHitRules().contains("MULTI_ACCOUNT_DEVICE"));
    }

    @Test
    void evaluate_blacklistHit() {
        FraudSignalDTO signal = FraudSignalDTO.builder().blacklistHit(true).build();
        FraudRuleResult result = engine.evaluate(signal);
        assertEquals(50, result.getFraudScore());
        assertTrue(result.getHitRules().contains("BLACKLIST_HIT"));
    }

    @Test
    void evaluate_stackedSignals() {
        FraudSignalDTO signal = FraudSignalDTO.builder()
                .ipAbnormal(true)
                .deviceAbnormal(true)
                .proxyIp(true)
                .multiAccountDevice(true)
                .blacklistHit(true)
                .build();

        FraudRuleResult result = engine.evaluate(signal);

        assertEquals(100, result.getFraudScore());
        assertEquals(UserRiskLevel.HIGH, result.getFraudLevel());
        assertTrue(result.getHitRules().contains("IP_ABNORMAL"));
        assertTrue(result.getHitRules().contains("DEVICE_ABNORMAL"));
        assertTrue(result.getHitRules().contains("PROXY_IP"));
        assertTrue(result.getHitRules().contains("MULTI_ACCOUNT_DEVICE"));
        assertTrue(result.getHitRules().contains("BLACKLIST_HIT"));
    }
}
