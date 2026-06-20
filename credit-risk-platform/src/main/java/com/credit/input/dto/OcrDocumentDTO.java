package com.credit.input.dto;

import lombok.Data;

import java.util.List;

@Data
public class OcrDocumentDTO {

    private String documentType;
    private String text;
    private Double confidence;
    private String fileMd5;
    private List<String> qualityFlags;
    private Integer page;
    private Object boundingBox;
}
