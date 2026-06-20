package com.credit.platformconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.credit.platformconfig.entity.PromptConfig;
import com.credit.platformconfig.mapper.PromptConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
public class PromptConfigService {

    @Resource
    private PromptConfigMapper promptConfigMapper;

    public PromptConfig getEnabledLatest(String promptCode) {
        if (promptCode == null || promptCode.trim().isEmpty()) {
            return null;
        }
        return promptConfigMapper.selectOne(
                new QueryWrapper<PromptConfig>()
                        .eq("prompt_code", promptCode.trim())
                        .eq("enabled", true)
                        .orderByDesc("version")
                        .last("LIMIT 1"));
    }

    public List<PromptConfig> listVersions(String promptCode) {
        return promptConfigMapper.selectList(
                new QueryWrapper<PromptConfig>()
                        .eq("prompt_code", promptCode)
                        .orderByDesc("version"));
    }

    @Transactional(rollbackFor = Exception.class)
    public PromptConfig rollback(String promptCode, int version) {
        promptConfigMapper.update(null,
                new UpdateWrapper<PromptConfig>()
                        .eq("prompt_code", promptCode)
                        .set("enabled", false));
        PromptConfig target = promptConfigMapper.selectOne(
                new QueryWrapper<PromptConfig>()
                        .eq("prompt_code", promptCode)
                        .eq("version", version)
                        .last("LIMIT 1"));
        if (target == null) {
            return null;
        }
        target.setEnabled(true);
        promptConfigMapper.updateById(target);
        return target;
    }
}
