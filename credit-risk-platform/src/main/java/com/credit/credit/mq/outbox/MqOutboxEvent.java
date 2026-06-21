package com.credit.credit.mq.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_mq_outbox_event")
public class MqOutboxEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventKey;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String destination;
    private String payloadJson;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
