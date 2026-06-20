package com.credit.ocr.service;

import cn.hutool.json.JSONUtil;
import com.credit.agent.cache.AgentResultCacheService;
import com.credit.audit.service.AuditLogService;
import com.credit.ocr.config.OcrProperties;
import com.credit.ocr.dto.OcrResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class OcrFacadeService {

    @Resource
    private MockOcrService mockOcrService;
    @Resource
    private AgentResultCacheService agentResultCacheService;
    @Resource
    private AuditLogService auditLogService;
    @Resource
    private OcrProperties ocrProperties;

    public OcrResult recognizeDocument(String workflowId, String traceId, String nodeName,
                                       String documentType, String fileMd5,
                                       String fileName, String mockText) {
        long start = System.currentTimeMillis();
        boolean cacheHit = false;
        OcrResult result = null;

        if (StringUtils.hasText(fileMd5)) {
            String cached = agentResultCacheService.getOcrResult(fileMd5);
            if (StringUtils.hasText(cached)) {
                result = JSONUtil.toBean(cached, OcrResult.class);
                cacheHit = true;
            }
        }

        if (result == null) {
            result = mockOcrService.recognize(documentType, fileMd5, fileName, mockText);
            if (StringUtils.hasText(fileMd5)) {
                agentResultCacheService.setOcrResult(fileMd5, JSONUtil.toJsonStr(result), null);
            }
        }

        result.setCacheHit(cacheHit);
        result.setCostTimeMs(System.currentTimeMillis() - start);

        Map<String, Object> auditArgs = new HashMap<>();
        auditArgs.put("workflowId", workflowId);
        auditArgs.put("traceId", traceId);
        auditArgs.put("nodeName", nodeName != null ? nodeName : "ocr_preprocess");
        auditArgs.put("callType", "OCR");
        auditArgs.put("request", documentType + ":" + fileMd5);
        auditArgs.put("response", JSONUtil.toJsonStr(result));
        auditArgs.put("tokenCount", 0);
        auditArgs.put("costTimeMs", result.getCostTimeMs());
        auditArgs.put("success", true);
        auditArgs.put("cacheHit", cacheHit);
        auditArgs.put("promptVersion", 0);
        auditLogService.save(auditArgs);

        return result;
    }

    public boolean shouldManualReview(OcrResult result) {
        if (result == null) {
            return true;
        }
        if (result.getConfidence() < ocrProperties.getMinConfidence()) {
            return true;
        }
        if (!ocrProperties.isBlockOnQualityIssue()) {
            return false;
        }
        return result.getQualityFlags() != null && !result.getQualityFlags().isEmpty();
    }
}
