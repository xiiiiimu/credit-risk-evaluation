package com.credit.input.tool;

import cn.hutool.json.JSONUtil;
import com.credit.common.Result;
import com.credit.input.dto.OcrDocumentDTO;
import com.credit.input.dto.StructuredApplicationDTO;
import com.credit.input.dto.UnifiedRiskContextDTO;
import com.credit.input.dto.UserNarrativeDTO;
import com.credit.input.service.InputFusionService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Map;

@Service
public class InputFusionToolService {

    @Resource
    private InputFusionService inputFusionService;

    @SuppressWarnings("unchecked")
    public Result fuseApplicationInput(Map<String, Object> args) {
        StructuredApplicationDTO structured = parseBean(args.get("structuredApplication"),
                StructuredApplicationDTO.class, new StructuredApplicationDTO());
        UserNarrativeDTO narrative = parseBean(args.get("userNarrative"),
                UserNarrativeDTO.class, new UserNarrativeDTO());
        List<OcrDocumentDTO> ocrDocuments = parseList(args.get("ocrDocuments"), OcrDocumentDTO.class);
        String legacyContent = stringVal(args, "legacyContent");

        UnifiedRiskContextDTO ctx = inputFusionService.fuse(structured, narrative, ocrDocuments, legacyContent,
                parseMap(args.get("productContext")));
        return Result.ok(JSONUtil.parseObj(JSONUtil.toJsonStr(ctx)));
    }

    private Map<String, Object> parseMap(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) raw;
            return map;
        }
        return JSONUtil.parseObj(JSONUtil.toJsonStr(raw));
    }

    private <T> T parseBean(Object raw, Class<T> type, T defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (type.isInstance(raw)) {
            return type.cast(raw);
        }
        return JSONUtil.toBean(JSONUtil.toJsonStr(raw), type);
    }

    private <T> List<T> parseList(Object raw, Class<T> type) {
        if (raw == null) {
            return null;
        }
        return JSONUtil.toList(JSONUtil.toJsonStr(raw), type);
    }

    private String stringVal(Map<String, Object> args, String key) {
        if (args == null || args.get(key) == null) {
            return null;
        }
        return args.get(key).toString();
    }
}
