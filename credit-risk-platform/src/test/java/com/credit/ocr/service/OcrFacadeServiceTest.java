package com.credit.ocr.service;

import com.credit.agent.cache.AgentResultCacheService;
import com.credit.audit.service.AuditLogService;
import com.credit.ocr.config.OcrProperties;
import com.credit.ocr.dto.OcrResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OcrFacadeServiceTest {

    @Mock
    private MockOcrService mockOcrService;
    @Mock
    private AgentResultCacheService agentResultCacheService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private OcrProperties ocrProperties;

    @InjectMocks
    private OcrFacadeService ocrFacadeService;

    @Test
    void recognizeDocument_usesCacheOnSecondCall() {
        when(agentResultCacheService.getOcrResult("md5-1")).thenReturn(null, "{\"text\":\"cached\",\"confidence\":0.96,\"qualityFlags\":[]}");
        OcrResult mock = new OcrResult();
        mock.setText("hello");
        mock.setConfidence(0.96);
        when(mockOcrService.recognize(any(), eq("md5-1"), any(), any())).thenReturn(mock);
        when(ocrProperties.getMinConfidence()).thenReturn(0.75);
        when(ocrProperties.isBlockOnQualityIssue()).thenReturn(true);

        OcrResult first = ocrFacadeService.recognizeDocument("wf", "t", "ocr_preprocess", "BANK_STATEMENT", "md5-1", "a.png", null);
        OcrResult second = ocrFacadeService.recognizeDocument("wf", "t", "ocr_preprocess", "BANK_STATEMENT", "md5-1", "a.png", null);

        assertFalse(first.isCacheHit());
        assertTrue(second.isCacheHit());
        verify(mockOcrService, times(1)).recognize(any(), eq("md5-1"), any(), any());
    }

    @Test
    void shouldManualReview_whenConfidenceLow() {
        OcrResult result = new OcrResult();
        result.setConfidence(0.5);
        when(ocrProperties.getMinConfidence()).thenReturn(0.75);
        when(ocrProperties.isBlockOnQualityIssue()).thenReturn(true);
        assertTrue(ocrFacadeService.shouldManualReview(result));
    }
}
