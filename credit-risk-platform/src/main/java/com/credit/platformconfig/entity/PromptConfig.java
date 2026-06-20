package com.credit.platformconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_prompt_config")
public class PromptConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String promptCode;
    private String promptContent;
    private Integer version;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
