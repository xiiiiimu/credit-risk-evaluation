package com.credit.product.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.credit.product.entity.ProductRuleConfig;
import com.credit.product.mapper.ProductRuleConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
public class ProductRuleConfigService {

    public static final String DEFAULT_RULE_CODE = "PRODUCT_APPROVAL_RULES";

    @Resource
    private ProductRuleConfigMapper productRuleConfigMapper;

    public ProductRuleConfig getEnabledLatest(Long productId, String ruleCode) {
        if (productId == null) {
            return null;
        }
        String code = ruleCode != null ? ruleCode : DEFAULT_RULE_CODE;
        return productRuleConfigMapper.selectOne(
                new QueryWrapper<ProductRuleConfig>()
                        .eq("product_id", productId)
                        .eq("rule_code", code)
                        .eq("enabled", true)
                        .orderByDesc("version")
                        .last("LIMIT 1"));
    }

    public JSONObject getEnabledJson(Long productId) {
        ProductRuleConfig config = getEnabledLatest(productId, DEFAULT_RULE_CODE);
        if (config == null || config.getRuleContentJson() == null) {
            return defaultSafeRules();
        }
        return JSONUtil.parseObj(config.getRuleContentJson());
    }

    public JSONObject defaultSafeRules() {
        JSONObject json = new JSONObject();
        json.set("riskLevelAmountRatio", JSONUtil.parseObj("{\"LOW\":1.0,\"MEDIUM\":0.6,\"HIGH\":0.0}"));
        json.set("riskLevelRateAdjustment", JSONUtil.parseObj("{\"LOW\":0.0,\"MEDIUM\":1.2,\"HIGH\":0.0}"));
        json.set("rejectThreshold", 80);
        json.set("manualReviewThreshold", 60);
        json.set("maxDebtIncomeRatio", 0.5);
        return json;
    }

    public double getRatio(JSONObject rules, String riskLevel, double defaultValue) {
        if (rules == null) {
            return defaultValue;
        }
        JSONObject ratio = rules.getJSONObject("riskLevelAmountRatio");
        if (ratio == null || riskLevel == null) {
            return defaultValue;
        }
        return ratio.getDouble(riskLevel, defaultValue);
    }

    public double getRateAdjustment(JSONObject rules, String riskLevel, double defaultValue) {
        if (rules == null) {
            return defaultValue;
        }
        JSONObject adj = rules.getJSONObject("riskLevelRateAdjustment");
        if (adj == null || riskLevel == null) {
            return defaultValue;
        }
        return adj.getDouble(riskLevel, defaultValue);
    }

    public List<ProductRuleConfig> listVersions(Long productId, String ruleCode) {
        return productRuleConfigMapper.selectList(
                new QueryWrapper<ProductRuleConfig>()
                        .eq("product_id", productId)
                        .eq("rule_code", ruleCode != null ? ruleCode : DEFAULT_RULE_CODE)
                        .orderByDesc("version"));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductRuleConfig rollback(Long productId, String ruleCode, int version) {
        String code = ruleCode != null ? ruleCode : DEFAULT_RULE_CODE;
        productRuleConfigMapper.update(null,
                new UpdateWrapper<ProductRuleConfig>()
                        .eq("product_id", productId)
                        .eq("rule_code", code)
                        .set("enabled", false));
        ProductRuleConfig target = productRuleConfigMapper.selectOne(
                new QueryWrapper<ProductRuleConfig>()
                        .eq("product_id", productId)
                        .eq("rule_code", code)
                        .eq("version", version)
                        .last("LIMIT 1"));
        if (target == null) {
            return null;
        }
        target.setEnabled(true);
        productRuleConfigMapper.updateById(target);
        return target;
    }
}
