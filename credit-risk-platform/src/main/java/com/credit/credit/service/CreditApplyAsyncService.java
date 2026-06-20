package com.credit.credit.service;

import cn.hutool.json.JSONUtil;
import com.credit.credit.dto.CreditApplySubmitRequest;
import com.credit.credit.dto.UploadedDocumentDTO;
import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.agent.idempotent.IdempotencyService;
import com.credit.input.dto.StructuredApplicationDTO;
import com.credit.input.dto.UserNarrativeDTO;
import com.credit.input.service.InputFusionService;
import com.credit.credit.mq.trigger.CreditApprovalTaskTrigger;
import com.credit.workflow.service.WorkflowPersistenceService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class CreditApplyAsyncService {

    @Resource
    private CreditApprovalTaskTrigger creditApprovalTaskTrigger;
    @Resource
    private CreditAsyncTaskMapper creditAsyncTaskMapper;
    @Resource
    private CreditRecordService creditRecordService;
    @Resource
    private IdempotencyService idempotencyService;
    @Resource
    private WorkflowPersistenceService workflowPersistenceService;
    @Resource
    private InputFusionService inputFusionService;

    public Long submitAsync(Long userId, CreditApplySubmitRequest request,
                            String sessionId, String traceId, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            CreditAsyncTask existing = creditAsyncTaskMapper.selectOne(
                    new QueryWrapper<CreditAsyncTask>()
                            .eq("idempotency_key", idempotencyKey.trim())
                            .last("LIMIT 1"));
            if (existing != null) {
                return existing.getId();
            }
            Map<String, Object> cached = idempotencyService.execute(
                    "credit.apply.submit",
                    idempotencyKey,
                    () -> {
                        Long id = doSubmitAsync(userId, request, sessionId, traceId, idempotencyKey);
                        Map<String, Object> m = new HashMap<>();
                        m.put("taskId", id);
                        return m;
                    },
                    Map.class);
            if (cached != null && cached.get("taskId") != null) {
                return Long.valueOf(cached.get("taskId").toString());
            }
        }
        return doSubmitAsync(userId, request, sessionId, traceId, idempotencyKey);
    }

    private Long doSubmitAsync(Long userId, CreditApplySubmitRequest request,
                               String sessionId, String traceId, String idempotencyKey) {
        long totalStart = System.nanoTime();
        Long taskId = null;
        String workflowId = null;
        try {
            long inputFusionStart = System.nanoTime();
            StructuredApplicationDTO structured = inputFusionService.fromApply(
                    userId,
                    request.getProductId(),
                    request.getApplyAmount(),
                    request.getApplyTerm(),
                    request.getPurpose(),
                    request.getIncome(),
                    request.getOccupation(),
                    request.getAge(),
                    request.getContactInfo());
            UserNarrativeDTO narrative = inputFusionService.fromNarrativeFields(
                    request.getLoanPurpose(),
                    request.getIncomeDescription(),
                    request.getOccupationDescription(),
                    request.getAdditionalDescription(),
                    request.getRiskExplanation(),
                    request.getContent());
            log.info("[PERF][submit] stage=inputFusion cost={}ms", elapsedMs(inputFusionStart));

            CreditAsyncTask task = new CreditAsyncTask();
            task.setUserId(userId);
            task.setProductId(request.getProductId());
            task.setApplyAmount(request.getApplyAmount());
            task.setApplyTerm(request.getApplyTerm() != null ? request.getApplyTerm() : 12);
            task.setPurpose(request.getPurpose());
            task.setContent(request.getContent() != null ? request.getContent().trim() : null);
            task.setStructuredApplicationJson(JSONUtil.toJsonStr(structured));
            task.setUserNarrativeJson(JSONUtil.toJsonStr(narrative));
            List<UploadedDocumentDTO> documents = request.getDocuments();
            if (documents != null && !documents.isEmpty()) {
                task.setUploadedDocumentsJson(JSONUtil.toJsonStr(documents));
            }
            task.setSessionId(sessionId);
            task.setTraceId(traceId);
            task.setWorkflowId(UUID.randomUUID().toString().replace("-", ""));
            task.setIdempotencyKey(idempotencyKey);
            task.setStatus(CreditAsyncTask.PENDING);
            workflowId = task.getWorkflowId();

            long taskInsertStart = System.nanoTime();
            creditAsyncTaskMapper.insert(task);
            taskId = task.getId();
            log.info("[PERF][submit] taskId={} workflowId={} stage=taskInsert cost={}ms",
                    taskId, workflowId, elapsedMs(taskInsertStart));

            long initWorkflowStart = System.nanoTime();
            workflowPersistenceService.initWorkflowIfAbsent(
                    task.getWorkflowId(), traceId, task.getId(), null);
            log.info("[PERF][submit] taskId={} workflowId={} stage=initWorkflow cost={}ms",
                    taskId, workflowId, elapsedMs(initWorkflowStart));

            long triggerStart = System.nanoTime();
            creditApprovalTaskTrigger.trigger(task);
            log.info("[PERF][submit] taskId={} workflowId={} stage=trigger cost={}ms",
                    taskId, workflowId, elapsedMs(triggerStart));
            return task.getId();
        } finally {
            log.info("[PERF][submit] taskId={} workflowId={} stage=doSubmitAsync.total cost={}ms",
                    taskId, workflowId, elapsedMs(totalStart));
        }
    }

    private static long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }

    public CreditAsyncTask getTask(Long taskId, Long userId) {
        CreditAsyncTask task = creditAsyncTaskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            return null;
        }
        return task;
    }

    public com.credit.credit.dto.CreditApplyVO getResultIfReady(Long taskId, Long userId) {
        CreditAsyncTask task = getTask(taskId, userId);
        if (task == null || task.getApplicationId() == null) {
            return null;
        }
        if (!CreditAsyncTask.SUCCESS.equals(task.getStatus())
                && !CreditAsyncTask.MANUAL_REVIEW.equals(task.getStatus())) {
            return null;
        }
        return creditRecordService.getDetail(task.getApplicationId());
    }
}
