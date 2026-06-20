package com.credit.platformconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_rule_config")
public class RuleConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleCode;
    private String ruleContent;
    private Integer version;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
