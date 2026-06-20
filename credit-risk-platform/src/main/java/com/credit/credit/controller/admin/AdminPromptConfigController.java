package com.credit.credit.controller.admin;

import com.credit.common.Result;
import com.credit.common.context.UserHolder;
import com.credit.platformconfig.entity.PromptConfig;
import com.credit.platformconfig.service.PromptConfigService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/admin/config/prompt")
public class AdminPromptConfigController {

    @Resource
    private PromptConfigService promptConfigService;

    @GetMapping("/{promptCode}/versions")
    public Result versions(@PathVariable("promptCode") String promptCode) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        List<PromptConfig> list = promptConfigService.listVersions(promptCode);
        return Result.ok(list);
    }

    @PostMapping("/{promptCode}/rollback/{version}")
    public Result rollback(@PathVariable("promptCode") String promptCode,
                           @PathVariable("version") int version) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        PromptConfig config = promptConfigService.rollback(promptCode, version);
        if (config == null) {
            return Result.fail("版本不存在");
        }
        return Result.ok(config);
    }
}
