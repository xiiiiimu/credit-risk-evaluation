package com.credit.ocr.service;

import com.credit.ocr.dto.OcrResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mock OCR：根据材料类型与 MD5 生成可复现的识别文本，便于演示与单测。
 */
@Service
public class MockOcrService implements OcrService {

    public static final double DEFAULT_CONFIDENCE = 0.96;

    @Override
    public OcrResult recognize(String documentType, String fileMd5, String fileName, String mockText) {
        long start = System.currentTimeMillis();
        OcrResult result = new OcrResult();
        result.setDocumentType(documentType != null ? documentType : "OTHER");
        result.setFileMd5(fileMd5);
        result.setPage(1);
        result.setBoundingBox(null);

        List<String> flags = detectQualityFlags(fileName, fileMd5, mockText);
        result.setQualityFlags(flags);

        if (StringUtils.hasText(mockText)) {
            result.setText(mockText.trim());
        } else {
            result.setText(buildMockText(documentType, fileMd5));
        }

        double confidence = DEFAULT_CONFIDENCE;
        if (flags.contains("LOW_CONFIDENCE")) {
            confidence = 0.55;
        } else if (flags.contains("BLURRY")) {
            confidence = 0.62;
        }
        result.setConfidence(confidence);
        result.setCostTimeMs(System.currentTimeMillis() - start);
        return result;
    }

    private String buildMockText(String documentType, String fileMd5) {
        String type = documentType != null ? documentType.toUpperCase(Locale.ROOT) : "OTHER";
        switch (type) {
            case "ID_CARD":
                return "姓名：张三\n身份证号：110101199001011234\n地址：北京市朝阳区";
            case "INCOME_PROOF":
                return "单位：某科技有限公司\n职位：软件工程师\n月薪：12000元\n盖章有效";
            case "BANK_STATEMENT":
                if (fileMd5 != null && fileMd5.toLowerCase(Locale.ROOT).contains("low")) {
                    return "近6个月工资入账：8000, 8200, 7900, 8100, 8000, 8050\n平均约8050元";
                }
                return "近6个月工资入账：11800, 12000, 11900, 12100, 12000, 11950\n平均约11958元";
            case "CREDIT_REPORT":
                return "征信报告摘要：无逾期记录，信用卡使用率35%";
            default:
                return "其他材料 OCR 文本，fileMd5=" + (fileMd5 != null ? fileMd5 : "unknown");
        }
    }

    private List<String> detectQualityFlags(String fileName, String fileMd5, String mockText) {
        List<String> flags = new ArrayList<>();
        String hint = ((fileName != null ? fileName : "") + " "
                + (fileMd5 != null ? fileMd5 : "") + " "
                + (mockText != null ? mockText : "")).toLowerCase(Locale.ROOT);
        if (hint.contains("blur")) {
            flags.add("BLURRY");
        }
        if (hint.contains("copy")) {
            flags.add("COPY_SUSPECTED");
        }
        if (hint.contains("screenshot") || hint.contains("screen")) {
            flags.add("SCREENSHOT_SUSPECTED");
        }
        if (hint.contains("tamper")) {
            flags.add("TAMPER_SUSPECTED");
        }
        if (hint.contains("lowconf") || hint.contains("low_conf")) {
            flags.add("LOW_CONFIDENCE");
        }
        return flags;
    }
}
