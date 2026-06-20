package com.credit.platformconfig.tool;

import com.credit.common.Result;
import com.credit.platformconfig.entity.PromptConfig;
import com.credit.platformconfig.entity.RuleConfig;
import com.credit.platformconfig.service.PromptConfigService;
import com.credit.platformconfig.service.RuleConfigService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class PlatformConfigToolService {

    @Resource
    private PromptConfigService promptConfigService;
    @Resource
    private RuleConfigService ruleConfigService;

    public Result getPromptConfig(Map<String, Object> args) {
        String code = stringVal(args, "promptCode");
        PromptConfig config = promptConfigService.getEnabledLatest(code);
        if (config == null) {
            return Result.fail("prompt 不存在: " + code);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("promptCode", config.getPromptCode());
        data.put("promptContent", config.getPromptContent());
        data.put("version", config.getVersion());
        return Result.ok(data);
    }

    public Result getRuleConfig(Map<String, Object> args) {
        String code = stringVal(args, "ruleCode");
        RuleConfig config = ruleConfigService.getEnabledLatest(code);
        if (config == null) {
            return Result.fail("rule 不存在: " + code);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("ruleCode", config.getRuleCode());
        data.put("ruleContent", config.getRuleContent());
        data.put("version", config.getVersion());
        return Result.ok(data);
    }

    private String stringVal(Map<String, Object> args, String key) {
        if (args == null || args.get(key) == null) {
            return null;
        }
        return args.get(key).toString();
    }
}
