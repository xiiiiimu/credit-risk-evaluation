package com.credit.ocr.service;

import com.credit.ocr.dto.OcrResult;
import org.springframework.stereotype.Service;

/** 预留阿里云 OCR 接入点，当前未实现。 */
@Service
public class AliyunOcrService implements OcrService {

    @Override
    public OcrResult recognize(String documentType, String fileMd5, String fileName, String mockText) {
        throw new UnsupportedOperationException("Aliyun OCR not configured, use MockOcrService");
    }
}
