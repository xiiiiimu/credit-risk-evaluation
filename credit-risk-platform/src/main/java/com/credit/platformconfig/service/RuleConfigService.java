package com.credit.platformconfig.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.credit.platformconfig.entity.RuleConfig;
import com.credit.platformconfig.mapper.RuleConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
public class RuleConfigService {

    @Resource
    private RuleConfigMapper ruleConfigMapper;

    public RuleConfig getEnabledLatest(String ruleCode) {
        if (ruleCode == null || ruleCode.trim().isEmpty()) {
            return null;
        }
        return ruleConfigMapper.selectOne(
                new QueryWrapper<RuleConfig>()
                        .eq("rule_code", ruleCode.trim())
                        .eq("enabled", true)
                        .orderByDesc("version")
                        .last("LIMIT 1"));
    }

    public JSONObject getEnabledJson(String ruleCode) {
        RuleConfig config = getEnabledLatest(ruleCode);
        if (config == null || config.getRuleContent() == null) {
            return null;
        }
        return JSONUtil.parseObj(config.getRuleContent());
    }

    public int getInt(JSONObject json, String key, int defaultValue) {
        if (json == null || !json.containsKey(key)) {
            return defaultValue;
        }
        return json.getInt(key, defaultValue);
    }

    public double getDouble(JSONObject json, String key, double defaultValue) {
        if (json == null || !json.containsKey(key)) {
            return defaultValue;
        }
        return json.getDouble(key, defaultValue);
    }

    public List<RuleConfig> listVersions(String ruleCode) {
        return ruleConfigMapper.selectList(
                new QueryWrapper<RuleConfig>()
                        .eq("rule_code", ruleCode)
                        .orderByDesc("version"));
    }

    @Transactional(rollbackFor = Exception.class)
    public RuleConfig rollback(String ruleCode, int version) {
        ruleConfigMapper.update(null,
                new UpdateWrapper<RuleConfig>()
                        .eq("rule_code", ruleCode)
                        .set("enabled", false));
        RuleConfig target = ruleConfigMapper.selectOne(
                new QueryWrapper<RuleConfig>()
                        .eq("rule_code", ruleCode)
                        .eq("version", version)
                        .last("LIMIT 1"));
        if (target == null) {
            return null;
        }
        target.setEnabled(true);
        ruleConfigMapper.updateById(target);
        return target;
    }
}
