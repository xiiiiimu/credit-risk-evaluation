package com.credit.credit.fraud.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FraudRuleResult {

    private int fraudScore;
    private String fraudLevel;
    private List<String> hitRules;
}
