package com.credit.ocr.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OcrResult {

    private String text;
    private double confidence;
    private Object boundingBox;
    private int page;
    private String documentType;
    private String fileMd5;
    private List<String> qualityFlags = new ArrayList<>();
    private boolean cacheHit;
    private long costTimeMs;
}
