package com.credit.credit.mq.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface MqOutboxEventMapper extends BaseMapper<MqOutboxEvent> {

    @Select("SELECT * FROM tb_mq_outbox_event "
            + "WHERE status IN ('NEW', 'FAILED') "
            + "AND (next_retry_time IS NULL OR next_retry_time <= NOW()) "
            + "ORDER BY id ASC LIMIT #{limit}")
    List<MqOutboxEvent> selectPending(@Param("limit") int limit);

    @Update("UPDATE tb_mq_outbox_event SET status = 'SENDING', update_time = NOW() "
            + "WHERE id = #{id} AND status IN ('NEW', 'FAILED') "
            + "AND (next_retry_time IS NULL OR next_retry_time <= NOW())")
    int claimSending(@Param("id") Long id);
}
