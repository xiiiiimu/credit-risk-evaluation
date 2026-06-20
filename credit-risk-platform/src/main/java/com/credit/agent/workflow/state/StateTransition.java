package com.credit.agent.workflow.state;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class StateTransition {

    private static final Map<String, Set<String>> RULES = new HashMap<>();

    static {
        put("credit_application", "SUBMITTED", "ANALYZING", "PENDING_DECISION", "CANCELLED");
        put("credit_application", "PENDING_DECISION", "APPROVED", "REJECTED", "MANUAL_REVIEW");
        put("credit_application", "MANUAL_REVIEW", "APPROVED", "REJECTED");
        put("credit_application", "APPROVED", "DISBURSED", "CLOSED");
        put("manual_review", "OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");
    }

    private static void put(String domain, String from, String... to) {
        String key = domain + ":" + from;
        Set<String> set = new HashSet<>();
        Collections.addAll(set, to);
        RULES.put(key, set);
    }

    public static void check(String domain, String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        Set<String> allowed = RULES.get(domain + ":" + from);
        if (allowed == null || !allowed.contains(to)) {
            throw new IllegalStateException(
                    "非法状态流转 " + domain + ": " + from + " -> " + to);
        }
    }

    private StateTransition() {
    }
}
