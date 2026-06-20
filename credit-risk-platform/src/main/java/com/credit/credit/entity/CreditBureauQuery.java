package com.credit.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_credit_bureau_query")
public class CreditBureauQuery {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private Long userId;
    private String queryType;
    private String requestHash;
    private String resultSummaryJson;
    private String mcpTraceId;
    private LocalDateTime createTime;
}
