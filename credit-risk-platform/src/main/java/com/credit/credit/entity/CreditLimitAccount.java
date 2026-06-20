package com.credit.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_credit_limit_account")
public class CreditLimitAccount {

    public static final String ACTIVE = "ACTIVE";
    public static final String FROZEN = "FROZEN";
    public static final String EXPIRED = "EXPIRED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long productId;
    private Long applicationId;
    private BigDecimal approvedAmount;
    private BigDecimal usedAmount;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
