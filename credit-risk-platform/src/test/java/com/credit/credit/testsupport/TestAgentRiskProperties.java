package com.credit.credit.testsupport;

import com.credit.agent.config.AgentRiskProperties;

public final class TestAgentRiskProperties {

    private TestAgentRiskProperties() {
    }

    public static AgentRiskProperties lowRiskAutoApprove() {
        AgentRiskProperties props = new AgentRiskProperties();
        props.setAutoMinConfidence(0.85);
        props.setAutoMaxComplaint7d(1);
        props.setAutoMaxCompensation30d(1);
        return props;
    }
}
