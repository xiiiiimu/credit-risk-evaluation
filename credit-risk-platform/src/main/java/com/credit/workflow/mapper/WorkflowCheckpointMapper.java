package com.credit.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.workflow.entity.WorkflowCheckpoint;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowCheckpointMapper extends BaseMapper<WorkflowCheckpoint> {
}
