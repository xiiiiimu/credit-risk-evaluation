package com.credit.agent.dto;

import com.credit.credit.dto.UploadedDocumentDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class CreditAgentRequest {

    private Long userId;
    private Long productId;
    private BigDecimal applyAmount;
    private Integer applyTerm;
    private String purpose;
    private String content;
    private String sessionId;
    private String traceId;
    private String workflowId;
    private Long applicationId;
    private Long taskId;

    /** Java Consumer 持锁调用：JAVA_OWNED */
    private String lockMode;
    /** 具体锁 token，如 task-{taskId} */
    private String lockOwner;

    private BigDecimal income;
    private String occupation;
    private Integer age;
    private String contactInfo;
    private String loanPurpose;
    private String incomeDescription;
    private String occupationDescription;
    private String additionalDescription;
    private String riskExplanation;
    private List<UploadedDocumentDTO> documents;
    private Map<String, Object> structuredApplication;
    private Map<String, Object> userNarrative;
}
