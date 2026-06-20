package com.credit.input.service;

import com.credit.input.dto.OcrDocumentDTO;
import com.credit.input.dto.StructuredApplicationDTO;
import com.credit.input.dto.UnifiedRiskContextDTO;
import com.credit.input.dto.UserNarrativeDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputFusionServiceTest {

    private final InputFusionService service = new InputFusionService();

    @Test
    void fuse_mergesStructuredNarrativeAndOcrWithCrossCheck() {
        StructuredApplicationDTO structured = service.fromApply(
                1L, 1L, new BigDecimal("50000"), 12, "CONSUMER",
                new BigDecimal("12000"), "软件工程师", 30, "13800000001");
        UserNarrativeDTO narrative = service.fromNarrativeFields(
                "装修", "工资稳定", null, "希望12期", null, "旧content");
        OcrDocumentDTO bank = new OcrDocumentDTO();
        bank.setDocumentType("BANK_STATEMENT");
        bank.setText("近6个月工资入账：8000, 8200, 7900, 8100, 8000, 8050\n平均约8050元");
        bank.setConfidence(0.96);
        bank.setFileMd5("abc");

        UnifiedRiskContextDTO ctx = service.fuse(structured, narrative, Collections.singletonList(bank), "旧content", null);
        assertTrue(ctx.getLegacyContentSummary().contains("装修"));
        assertFalse(ctx.getCrossCheckHints().isEmpty());
        assertTrue(ctx.getCrossCheckHints().get(0).contains("12000"));
    }
}
