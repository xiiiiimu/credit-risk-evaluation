package com.credit.agent.entity;



import com.baomidou.mybatisplus.annotation.TableId;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;



import java.time.LocalDateTime;



@Data

@TableName("tb_user_memory")

public class UserMemory {



    @TableId

    private Long userId;

    private Integer complaintCount;

    private Integer compensationCount;

    private Long compensationAmountTotal;

    private String riskLevel;

    private Integer complaintCount7d;

    private Integer distinctShopCount7d;

    private Integer compensationCount30d;

    private Integer compensationRejected30d;

    private String preferredCategory;

    private LocalDateTime lastComplaintTime;

    private LocalDateTime lastIncidentTime;

    private LocalDateTime riskLevelSince;

    private Integer cleanDays;

    private Boolean platformContactFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}

