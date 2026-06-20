package com.credit.ocr.service;

import com.credit.ocr.dto.OcrResult;

public interface OcrService {

    OcrResult recognize(String documentType, String fileMd5, String fileName, String mockText);
}
