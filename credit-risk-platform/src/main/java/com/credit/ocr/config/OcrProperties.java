package com.credit.ocr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "credit.agent.ocr")
public class OcrProperties {

    /** mock / tencent / aliyun */
    private String provider = "mock";
    private double minConfidence = 0.75;
    private boolean blockOnQualityIssue = true;
}
