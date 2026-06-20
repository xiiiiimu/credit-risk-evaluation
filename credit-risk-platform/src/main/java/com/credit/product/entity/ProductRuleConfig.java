package com.credit.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_product_rule_config")
public class ProductRuleConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String ruleCode;
    private String ruleContentJson;
    private Integer version;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
