package com.credit.input.dto;

import lombok.Data;

@Data
public class UserNarrativeDTO {

    private String loanPurpose;
    private String incomeDescription;
    private String occupationDescription;
    private String additionalDescription;
    private String riskExplanation;
    /** 兼容旧版单一 content 字段 */
    private String legacyContent;
}
