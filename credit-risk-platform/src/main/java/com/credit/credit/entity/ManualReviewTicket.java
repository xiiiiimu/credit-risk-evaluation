package com.credit.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_manual_review_ticket")
public class ManualReviewTicket {

    public static final String OPEN = "OPEN";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicationId;
    private Long userId;
    private String title;
    private String description;
    private String reason;
    private String priority;
    private String status;
    private Long handlerId;
    private String resolveNote;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
