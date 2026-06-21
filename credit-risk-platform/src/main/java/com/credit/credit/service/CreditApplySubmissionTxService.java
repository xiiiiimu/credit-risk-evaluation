package com.credit.credit.service;

import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.credit.mq.outbox.MqOutboxService;
import com.credit.workflow.service.WorkflowPersistenceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 提交事务边界：task + workflow + outbox 在同一本地事务中落库。
 */
@Service
public class CreditApplySubmissionTxService {

    @Resource
    private CreditAsyncTaskMapper creditAsyncTaskMapper;
    @Resource
    private WorkflowPersistenceService workflowPersistenceService;
    @Resource
    private MqOutboxService mqOutboxService;

    @Value("${credit.mq.enabled:true}")
    private boolean mqEnabled;

    @Transactional(rollbackFor = Exception.class)
    public CreditAsyncTask createTaskWithWorkflowAndOutbox(CreditAsyncTask task, String traceId) {
        creditAsyncTaskMapper.insert(task);
        workflowPersistenceService.initWorkflowIfAbsent(
                task.getWorkflowId(), traceId, task.getId(), null);
        if (mqEnabled) {
            mqOutboxService.enqueue(task);
        }
        return task;
    }
}
