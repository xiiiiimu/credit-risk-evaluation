package com.credit.agent.risk.enums;

public final class UserRiskLevel {
    public static final String LOW = "LOW";
    public static final String MEDIUM = "MEDIUM";
    public static final String HIGH = "HIGH";

    private UserRiskLevel() {
    }

    public static int ordinal(String level) {
        if (HIGH.equals(level)) {
            return 3;
        }
        if (MEDIUM.equals(level)) {
            return 2;
        }
        return 1;
    }
}
