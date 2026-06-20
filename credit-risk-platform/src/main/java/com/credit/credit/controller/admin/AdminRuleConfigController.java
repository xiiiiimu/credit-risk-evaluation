package com.credit.credit.controller.admin;

import com.credit.common.Result;
import com.credit.common.context.UserHolder;
import com.credit.platformconfig.entity.RuleConfig;
import com.credit.platformconfig.service.RuleConfigService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/admin/config/rule")
public class AdminRuleConfigController {

    @Resource
    private RuleConfigService ruleConfigService;

    @GetMapping("/{ruleCode}/versions")
    public Result versions(@PathVariable("ruleCode") String ruleCode) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        List<RuleConfig> list = ruleConfigService.listVersions(ruleCode);
        return Result.ok(list);
    }

    @PostMapping("/{ruleCode}/rollback/{version}")
    public Result rollback(@PathVariable("ruleCode") String ruleCode,
                           @PathVariable("version") int version) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        RuleConfig config = ruleConfigService.rollback(ruleCode, version);
        if (config == null) {
            return Result.fail("版本不存在");
        }
        return Result.ok(config);
    }
}
