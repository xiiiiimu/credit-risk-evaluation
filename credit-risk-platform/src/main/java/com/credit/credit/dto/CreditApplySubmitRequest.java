package com.credit.credit.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreditApplySubmitRequest {

    private Long productId;
    private BigDecimal applyAmount;
    private Integer applyTerm;
    private String purpose;
    /** 兼容旧版：用户填写的综合说明文本 */
    private String content;
    private String sessionId;
    private String idempotencyKey;

    /** Phase 6：结构化字段 */
    private BigDecimal income;
    private String occupation;
    private Integer age;
    private String contactInfo;

    /** Phase 6：自然语言字段 */
    private String loanPurpose;
    private String incomeDescription;
    private String occupationDescription;
    private String additionalDescription;
    private String riskExplanation;

    /** Phase 6：上传材料元数据（OCR 在 Agent 侧处理） */
    private List<UploadedDocumentDTO> documents;
}
