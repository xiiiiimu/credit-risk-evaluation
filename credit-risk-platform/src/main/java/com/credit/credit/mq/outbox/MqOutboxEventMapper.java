package com.credit.credit.mq.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface MqOutboxEventMapper extends BaseMapper<MqOutboxEvent> {

    /**
     * 待发送事件：NEW/FAILED 到点可发，或 SENDING 超时（Publisher 发送过程中宕机）可恢复。
     */
    @Select("SELECT * FROM tb_mq_outbox_event "
            + "WHERE ("
            + "  status IN ('NEW', 'FAILED') "
            + "  AND (next_retry_time IS NULL OR next_retry_time <= NOW())"
            + ") OR ("
            + "  status = 'SENDING' "
            + "  AND update_time < DATE_SUB(NOW(), INTERVAL 2 MINUTE)"
            + ") "
            + "ORDER BY create_time ASC LIMIT #{limit}")
    List<MqOutboxEvent> selectPending(@Param("limit") int limit);

    /**
     * CAS 抢占发送权：允许 NEW/FAILED 或超时 SENDING 进入 SENDING。
     */
    @Update("UPDATE tb_mq_outbox_event SET status = 'SENDING', update_time = NOW() "
            + "WHERE id = #{id} AND ("
            + "  (status IN ('NEW', 'FAILED') "
            + "   AND (next_retry_time IS NULL OR next_retry_time <= NOW()))"
            + "  OR (status = 'SENDING' "
            + "      AND update_time < DATE_SUB(NOW(), INTERVAL 2 MINUTE))"
            + ")")
    int claimSending(@Param("id") Long id);
}
