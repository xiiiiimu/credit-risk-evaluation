package com.credit.input.service;

import com.credit.credit.dto.UploadedDocumentDTO;
import com.credit.input.dto.OcrDocumentDTO;
import com.credit.input.dto.StructuredApplicationDTO;
import com.credit.input.dto.UnifiedRiskContextDTO;
import com.credit.input.dto.UserNarrativeDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InputFusionService {

    private static final Pattern INCOME_PATTERN = Pattern.compile("(\\d{3,6})");

    public UnifiedRiskContextDTO fuse(StructuredApplicationDTO structured,
                                      UserNarrativeDTO narrative,
                                      List<OcrDocumentDTO> ocrDocuments,
                                      String legacyContent,
                                      Map<String, Object> productContext) {
        UnifiedRiskContextDTO ctx = new UnifiedRiskContextDTO();
        ctx.setStructuredApplication(structured != null ? structured : new StructuredApplicationDTO());
        ctx.setUserNarrative(enrichNarrative(narrative, legacyContent));
        ctx.setOcrDocuments(ocrDocuments != null ? ocrDocuments : new ArrayList<>());
        ctx.setProductContext(productContext);
        ctx.setCrossCheckHints(buildCrossCheckHints(ctx));
        ctx.setLegacyContentSummary(buildLegacySummary(ctx, legacyContent));
        return ctx;
    }

    public StructuredApplicationDTO fromApply(Long userId, Long productId, BigDecimal applyAmount,
                                              Integer loanTerm, String purpose, BigDecimal income,
                                              String occupation, Integer age, String contactInfo) {
        StructuredApplicationDTO dto = new StructuredApplicationDTO();
        dto.setUserId(userId);
        dto.setProductId(productId);
        dto.setApplyAmount(applyAmount);
        dto.setLoanTerm(loanTerm);
        dto.setPurpose(purpose);
        dto.setIncome(income);
        dto.setOccupation(occupation);
        dto.setAge(age);
        dto.setContactInfo(contactInfo);
        return dto;
    }

    public UserNarrativeDTO fromNarrativeFields(String loanPurpose, String incomeDescription,
                                                String occupationDescription, String additionalDescription,
                                                String riskExplanation, String legacyContent) {
        UserNarrativeDTO dto = new UserNarrativeDTO();
        dto.setLoanPurpose(loanPurpose);
        dto.setIncomeDescription(incomeDescription);
        dto.setOccupationDescription(occupationDescription);
        dto.setAdditionalDescription(additionalDescription);
        dto.setRiskExplanation(riskExplanation);
        dto.setLegacyContent(legacyContent);
        return dto;
    }

    public List<OcrDocumentDTO> fromUploadedDocuments(List<UploadedDocumentDTO> uploads) {
        List<OcrDocumentDTO> list = new ArrayList<>();
        if (uploads == null) {
            return list;
        }
        for (UploadedDocumentDTO upload : uploads) {
            if (upload == null) {
                continue;
            }
            OcrDocumentDTO doc = new OcrDocumentDTO();
            doc.setDocumentType(upload.getDocumentType());
            doc.setFileMd5(upload.getFileMd5());
            list.add(doc);
        }
        return list;
    }

    private UserNarrativeDTO enrichNarrative(UserNarrativeDTO narrative, String legacyContent) {
        UserNarrativeDTO dto = narrative != null ? narrative : new UserNarrativeDTO();
        if (!StringUtils.hasText(dto.getLegacyContent()) && StringUtils.hasText(legacyContent)) {
            dto.setLegacyContent(legacyContent);
        }
        return dto;
    }

    private List<String> buildCrossCheckHints(UnifiedRiskContextDTO ctx) {
        List<String> hints = new ArrayList<>();
        BigDecimal declaredIncome = ctx.getStructuredApplication() != null
                ? ctx.getStructuredApplication().getIncome() : null;
        if (declaredIncome == null) {
            return hints;
        }
        for (OcrDocumentDTO doc : ctx.getOcrDocuments()) {
            if (doc == null || !"BANK_STATEMENT".equalsIgnoreCase(doc.getDocumentType())) {
                continue;
            }
            Double ocrIncome = extractAverageIncome(doc.getText());
            if (ocrIncome == null) {
                continue;
            }
            double declared = declaredIncome.doubleValue();
            if (Math.abs(declared - ocrIncome) / Math.max(declared, 1) > 0.15) {
                hints.add(String.format(Locale.ROOT,
                        "用户填写收入为%s，银行流水识别收入约%s，存在不一致风险",
                        formatAmount(declared), formatAmount(ocrIncome)));
            }
        }
        return hints;
    }

    private Double extractAverageIncome(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.contains("平均")) {
            Matcher m = Pattern.compile("平均[^0-9]*(\\d+)").matcher(text);
            if (m.find()) {
                return Double.valueOf(m.group(1));
            }
        }
        Matcher m = INCOME_PATTERN.matcher(text);
        double sum = 0;
        int count = 0;
        while (m.find()) {
            sum += Double.parseDouble(m.group(1));
            count++;
        }
        return count > 0 ? sum / count : null;
    }

    private String formatAmount(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.01) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String buildLegacySummary(UnifiedRiskContextDTO ctx, String legacyContent) {
        StringBuilder sb = new StringBuilder();
        UserNarrativeDTO n = ctx.getUserNarrative();
        if (n != null) {
            appendLine(sb, "借款用途", n.getLoanPurpose());
            appendLine(sb, "收入说明", n.getIncomeDescription());
            appendLine(sb, "职业说明", n.getOccupationDescription());
            appendLine(sb, "补充说明", n.getAdditionalDescription());
            appendLine(sb, "风险说明", n.getRiskExplanation());
            appendLine(sb, "用户说明", n.getLegacyContent());
        }
        StructuredApplicationDTO s = ctx.getStructuredApplication();
        if (s != null) {
            appendLine(sb, "申请金额", s.getApplyAmount() != null ? s.getApplyAmount().toPlainString() : null);
            appendLine(sb, "月收入", s.getIncome() != null ? s.getIncome().toPlainString() : null);
            appendLine(sb, "职业", s.getOccupation());
        }
        for (String hint : ctx.getCrossCheckHints()) {
            appendLine(sb, "交叉校验", hint);
        }
        if (sb.length() == 0 && StringUtils.hasText(legacyContent)) {
            return legacyContent.trim();
        }
        return sb.toString().trim();
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(label).append(':').append(value.trim());
    }
}
