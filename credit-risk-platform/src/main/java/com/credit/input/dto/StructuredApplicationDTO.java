package com.credit.input.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StructuredApplicationDTO {

    private Long userId;
    private Long productId;
    private BigDecimal applyAmount;
    private Integer loanTerm;
    private BigDecimal income;
    private String occupation;
    private Integer age;
    private String contactInfo;
    private String purpose;
}
