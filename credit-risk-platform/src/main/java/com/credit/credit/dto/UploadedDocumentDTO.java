package com.credit.credit.dto;

import lombok.Data;

@Data
public class UploadedDocumentDTO {

    /** ID_CARD / INCOME_PROOF / BANK_STATEMENT / CREDIT_REPORT / OTHER */
    private String documentType;
    private String fileName;
    private String fileMd5;
    private String mimeType;
    /** Mock OCR 可选：直接传入识别文本（演示用） */
    private String mockText;
}
