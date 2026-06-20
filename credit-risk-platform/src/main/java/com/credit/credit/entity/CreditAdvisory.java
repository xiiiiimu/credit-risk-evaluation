package com.credit.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_credit_advisory")
public class CreditAdvisory {

    public static final String PENDING = "PENDING";
    public static final String DECIDED = "DECIDED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private Long ticketId;
    private Long userId;
    private BigDecimal suggestAmount;
    private BigDecimal suggestRate;
    private Integer suggestTerm;
    private String agentConsensusJson;
    private String agentSuggestion;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
