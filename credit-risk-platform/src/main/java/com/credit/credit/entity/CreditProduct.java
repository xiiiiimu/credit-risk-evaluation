package com.credit.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_credit_product")
public class CreditProduct {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String productCode;
    private String name;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private BigDecimal interestRate;
    private Integer termMonths;
    private String supportedTermsJson;
    private String productType;
    private String description;
    private Integer dailyQuota;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
