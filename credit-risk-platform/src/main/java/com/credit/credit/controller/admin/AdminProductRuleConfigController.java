package com.credit.credit.controller.admin;

import com.credit.common.Result;
import com.credit.product.entity.ProductRuleConfig;
import com.credit.product.service.CreditProductService;
import com.credit.product.service.ProductRuleConfigService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/admin/config/product")
public class AdminProductRuleConfigController {

    @Resource
    private ProductRuleConfigService productRuleConfigService;
    @Resource
    private CreditProductService creditProductService;

    @GetMapping("/{productId}/rules/versions")
    public Result listVersions(@PathVariable Long productId,
                               @RequestParam(defaultValue = "PRODUCT_APPROVAL_RULES") String ruleCode) {
        List<ProductRuleConfig> versions = productRuleConfigService.listVersions(productId, ruleCode);
        return Result.ok(versions);
    }

    @PostMapping("/{productId}/rules/rollback/{version}")
    public Result rollback(@PathVariable Long productId,
                           @PathVariable int version,
                           @RequestParam(defaultValue = "PRODUCT_APPROVAL_RULES") String ruleCode) {
        ProductRuleConfig config = productRuleConfigService.rollback(productId, ruleCode, version);
        if (config == null) {
            return Result.fail("版本不存在");
        }
        creditProductService.evictProductCache(productId);
        return Result.ok(config);
    }
}
