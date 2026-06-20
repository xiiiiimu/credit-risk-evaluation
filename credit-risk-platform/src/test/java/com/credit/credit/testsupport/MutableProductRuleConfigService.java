package com.credit.credit.testsupport;

import cn.hutool.json.JSONObject;
import com.credit.product.service.ProductRuleConfigService;

/**
 * 测试用产品规则配置：仅覆盖 getEnabledJson，其余解析逻辑走真实实现。
 */
public class MutableProductRuleConfigService extends ProductRuleConfigService {

    private JSONObject enabledJson;

    public void setEnabledJson(JSONObject enabledJson) {
        this.enabledJson = enabledJson;
    }

    @Override
    public JSONObject getEnabledJson(Long productId) {
        return enabledJson != null ? enabledJson : defaultSafeRules();
    }
}
