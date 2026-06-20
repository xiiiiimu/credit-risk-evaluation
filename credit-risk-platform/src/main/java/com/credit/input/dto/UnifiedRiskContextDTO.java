package com.credit.input.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class UnifiedRiskContextDTO {

    private StructuredApplicationDTO structuredApplication;
    private UserNarrativeDTO userNarrative;
    private List<OcrDocumentDTO> ocrDocuments = new ArrayList<>();
    private List<String> crossCheckHints = new ArrayList<>();
    private Map<String, Object> productContext;
    /** 兼容旧版 content 拼接摘要 */
    private String legacyContentSummary;
}
