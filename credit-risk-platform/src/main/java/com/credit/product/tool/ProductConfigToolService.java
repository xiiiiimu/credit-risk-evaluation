package com.credit.product.tool;

import cn.hutool.json.JSONUtil;
import com.credit.common.Result;
import com.credit.credit.entity.CreditProduct;
import com.credit.product.dto.ProductContextDTO;
import com.credit.product.entity.ProductRuleConfig;
import com.credit.product.service.CreditProductService;
import com.credit.product.service.ProductMaterialRequirementService;
import com.credit.product.service.ProductRuleConfigService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class ProductConfigToolService {

    @Resource
    private CreditProductService creditProductService;
    @Resource
    private ProductRuleConfigService productRuleConfigService;
    @Resource
    private ProductMaterialRequirementService productMaterialRequirementService;

    public Result getCreditProductContext(Map<String, Object> args) {
        Long productId = longVal(args, "productId");
        ProductContextDTO ctx = creditProductService.buildProductContext(productId);
        if (ctx == null) {
            return Result.fail("产品不存在或已下线");
        }
        return Result.ok(JSONUtil.parseObj(JSONUtil.toJsonStr(ctx)));
    }

    public Result getProductRuleConfig(Map<String, Object> args) {
        Long productId = longVal(args, "productId");
        ProductRuleConfig config = productRuleConfigService.getEnabledLatest(productId, null);
        Map<String, Object> data = new HashMap<>();
        if (config == null) {
            data.put("ruleContent", productRuleConfigService.defaultSafeRules());
            data.put("version", 0);
            data.put("fallback", true);
            return Result.ok(data);
        }
        data.put("ruleCode", config.getRuleCode());
        data.put("ruleContent", JSONUtil.parse(config.getRuleContentJson()));
        data.put("version", config.getVersion());
        return Result.ok(data);
    }

    public Result getProductMaterialRequirements(Map<String, Object> args) {
        Long productId = longVal(args, "productId");
        CreditProduct product = creditProductService.getActiveProduct(productId);
        if (product == null) {
            return Result.fail("产品不存在或已下线");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("productId", productId);
        data.put("requiredMaterials", productMaterialRequirementService.toAgentView(productId));
        return Result.ok(data);
    }

    private Long longVal(Map<String, Object> args, String key) {
        if (args == null || args.get(key) == null) {
            return null;
        }
        return Long.valueOf(args.get(key).toString());
    }
}
