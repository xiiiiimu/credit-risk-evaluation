package com.credit.credit.controller;

import com.credit.credit.cache.CreditCacheService;
import com.credit.credit.entity.CreditProduct;
import com.credit.credit.mapper.CreditProductMapper;
import com.credit.common.Result;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/credit/product")
public class CreditProductController {

    @Resource
    private CreditProductMapper creditProductMapper;
    @Resource
    private CreditCacheService creditCacheService;

    /** 热点产品查询：逻辑过期 + 互斥重建 */
    @GetMapping("/{productId:\\d+}")
    public Result detail(@PathVariable("productId") Long productId) {
        CreditProduct product = creditCacheService.getProduct(productId, creditProductMapper::selectById);
        if (product == null) {
            return Result.fail("产品不存在");
        }
        return Result.ok(product);
    }
}
