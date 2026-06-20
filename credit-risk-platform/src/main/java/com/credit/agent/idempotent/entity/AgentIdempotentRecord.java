package com.credit.agent.idempotent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_agent_idempotent_record")
public class AgentIdempotentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String scope;
    private String idempotencyKey;
    private String requestHash;
    private String responseJson;
    private LocalDateTime createTime;
}
