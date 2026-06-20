package com.credit.credit.testsupport;

import cn.hutool.json.JSONObject;
import com.credit.platformconfig.service.RuleConfigService;

/**
 * 单元测试用 RuleConfig 桩：无 DB 时走内置默认值。
 */
public class RuleConfigServiceStub extends RuleConfigService {

    @Override
    public JSONObject getEnabledJson(String ruleCode) {
        return null;
    }
}
