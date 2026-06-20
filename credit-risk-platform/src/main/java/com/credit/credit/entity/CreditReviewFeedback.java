package com.credit.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_credit_review_feedback")
public class CreditReviewFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private Long userId;
    private String agentSuggestion;
    private String javaDecision;
    private String humanDecision;
    private Integer riskScore;
    private String hitRulesJson;
    private String reviewReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createTime;
}
