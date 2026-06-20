package com.credit.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.credit.workflow.entity.WorkflowNodeRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowNodeMapper extends BaseMapper<WorkflowNodeRecord> {
}
