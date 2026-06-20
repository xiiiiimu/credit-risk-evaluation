package com.credit.ocr.tool;

import cn.hutool.json.JSONUtil;
import com.credit.common.Result;
import com.credit.ocr.dto.OcrResult;
import com.credit.ocr.service.OcrFacadeService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class OcrToolService {

    @Resource
    private OcrFacadeService ocrFacadeService;

    public Result recognizeDocument(Map<String, Object> args) {
        String workflowId = stringVal(args, "workflowId");
        String traceId = stringVal(args, "traceId");
        String nodeName = stringVal(args, "nodeName", "ocr_preprocess");
        String documentType = stringVal(args, "documentType", "OTHER");
        String fileMd5 = stringVal(args, "fileMd5");
        String fileName = stringVal(args, "fileName");
        String mockText = stringVal(args, "mockText");

        OcrResult result = ocrFacadeService.recognizeDocument(
                workflowId, traceId, nodeName, documentType, fileMd5, fileName, mockText);

        Map<String, Object> data = new HashMap<>();
        data.put("text", result.getText());
        data.put("confidence", result.getConfidence());
        data.put("boundingBox", result.getBoundingBox());
        data.put("page", result.getPage());
        data.put("documentType", result.getDocumentType());
        data.put("fileMd5", result.getFileMd5());
        data.put("qualityFlags", result.getQualityFlags());
        data.put("cacheHit", result.isCacheHit());
        data.put("costTimeMs", result.getCostTimeMs());
        data.put("manualReviewRequired", ocrFacadeService.shouldManualReview(result));
        return Result.ok(data);
    }

    private String stringVal(Map<String, Object> args, String key) {
        if (args == null || args.get(key) == null) {
            return null;
        }
        return args.get(key).toString();
    }

    private String stringVal(Map<String, Object> args, String key, String defaultValue) {
        String v = stringVal(args, key);
        return v != null ? v : defaultValue;
    }
}
