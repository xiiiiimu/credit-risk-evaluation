package com.credit.credit.controller;

import cn.hutool.json.JSONUtil;
import com.credit.credit.cache.CreditCacheService;
import com.credit.credit.tool.CreditToolService;
import com.credit.common.Result;
import com.credit.common.context.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/credit/record")
public class CreditRecordController {

    @Resource
    private CreditCacheService creditCacheService;
    @Resource
    private CreditToolService creditToolService;

    /** 征信脱敏摘要（仅缓存摘要，不缓存敏感原文） */
    @GetMapping("/summary")
    public Result bureauSummary() {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        Long userId = UserHolder.getUser().getId();
        String summary = creditCacheService.getBureauSummary(userId, uid -> {
            Result r = creditToolService.getCreditBureauSummary(uid);
            if (r.getData() == null) {
                return null;
            }
            return JSONUtil.toJsonStr(r.getData());
        });
        if (summary == null) {
            return Result.ok(null);
        }
        return Result.ok(JSONUtil.parse(summary));
    }
}
