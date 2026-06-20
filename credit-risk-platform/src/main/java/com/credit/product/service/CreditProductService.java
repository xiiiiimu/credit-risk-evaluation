package com.credit.product.service;

import cn.hutool.json.JSONUtil;
import com.credit.credit.cache.CreditCacheService;
import com.credit.credit.entity.CreditProduct;
import com.credit.credit.mapper.CreditProductMapper;
import com.credit.product.dto.ProductContextDTO;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

@Service
public class CreditProductService {

    public static final String STATUS_ACTIVE = "ACTIVE";

    @Resource
    private CreditProductMapper creditProductMapper;
    @Resource
    private CreditCacheService creditCacheService;
    @Resource
    private ProductMaterialRequirementService productMaterialRequirementService;
    @Resource
    private ProductRuleConfigService productRuleConfigService;

    public CreditProduct getActiveProduct(Long productId) {
        if (productId == null) {
            return null;
        }
        return creditCacheService.getProduct(productId, id -> {
            CreditProduct product = creditProductMapper.selectById(id);
            if (product == null || !STATUS_ACTIVE.equals(product.getStatus())) {
                return null;
            }
            return product;
        });
    }

    public ProductContextDTO buildProductContext(Long productId) {
        CreditProduct product = getActiveProduct(productId);
        if (product == null) {
            return null;
        }
        ProductContextDTO ctx = new ProductContextDTO();
        ctx.setProductId(product.getId());
        ctx.setProductCode(product.getProductCode());
        ctx.setProductName(product.getName());
        ctx.setProductType(product.getProductType() != null ? product.getProductType() : "CONSUMER_CREDIT");
        ctx.setMinAmount(product.getMinAmount());
        ctx.setMaxAmount(product.getMaxAmount());
        ctx.setBaseRate(product.getInterestRate());
        ctx.setSupportedTerms(parseSupportedTerms(product));
        ctx.setRequiredMaterials(productMaterialRequirementService.toAgentView(productId));
        ctx.setRuleSummary(productRuleConfigService.getEnabledJson(productId));
        ctx.setDescription(product.getDescription());
        ctx.setStatus(product.getStatus());
        return ctx;
    }

    public List<Integer> parseSupportedTerms(CreditProduct product) {
        if (product == null) {
            return Collections.singletonList(12);
        }
        if (product.getSupportedTermsJson() != null && !product.getSupportedTermsJson().trim().isEmpty()) {
            return JSONUtil.toList(product.getSupportedTermsJson(), Integer.class);
        }
        if (product.getTermMonths() != null) {
            return Collections.singletonList(product.getTermMonths());
        }
        return Collections.singletonList(12);
    }

    public void evictProductCache(Long productId) {
        creditCacheService.evictProduct(productId);
    }
}
