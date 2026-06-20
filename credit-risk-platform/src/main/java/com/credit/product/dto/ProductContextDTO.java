package com.credit.product.dto;

import cn.hutool.json.JSONObject;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ProductContextDTO {

    private Long productId;
    private String productCode;
    private String productName;
    private String productType;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private BigDecimal baseRate;
    private List<Integer> supportedTerms = new ArrayList<>();
    private List<Map<String, Object>> requiredMaterials = new ArrayList<>();
    private JSONObject ruleSummary;
    private String description;
    private String status;
}
