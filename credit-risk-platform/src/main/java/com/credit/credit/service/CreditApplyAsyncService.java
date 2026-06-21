package com.credit.credit.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.credit.credit.dto.CreditApplySubmitRequest;
import com.credit.credit.dto.UploadedDocumentDTO;
import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.agent.idempotent.IdempotencyService;
import com.credit.credit.mq.trigger.CreditApprovalTaskTrigger;
import com.credit.input.dto.StructuredApplicationDTO;
import com.credit.input.dto.UserNarrativeDTO;
import com.credit.input.service.InputFusionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private CreditApplySubmissionTxService creditApplySubmissionTxService;
    @Resource
    private CreditAsyncTaskMapper creditAsyncTaskMapper;
    @Resource
    private CreditRecordService creditRecordService;
    @Resource
    private IdempotencyService idempotencyService;
    @Resource
    private InputFusionService inputFusionService;

    @Autowired(required = false)
    private CreditApprovalTaskTrigger creditApprovalTaskTrigger;

    @Value("${credit.mq.enabled:true}")
    private boolean mqEnabled;

    public Long submitAsync(Long userId, CreditApplySubmitRequest request,
                            String sessionId, String traceId, String idempotencyKey) {
        // 幂等以 tb_agent_idempotent_record(scope, idempotency_key) 唯一约束为准，不依赖 credit_async_task 查重。
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return doSubmitAsync(userId, request, sessionId, traceId, null);
        }
        String requestHash = buildRequestHash(userId, request);
        Map<String, Object> cached = idempotencyService.execute(
                "credit.apply.submit",
                idempotencyKey,
                requestHash,
                () -> {
                    Long id = doSubmitAsync(userId, request, sessionId, traceId, idempotencyKey.trim());
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("taskId", id);
                    return payload;
                },
                Map.class);
        if (cached != null && cached.get("taskId") != null) {
            return Long.valueOf(cached.get("taskId").toString());
        }
        throw new IllegalStateException("idempotent submit returned empty taskId");
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

            long txStart = System.nanoTime();
            creditApplySubmissionTxService.createTaskWithWorkflowAndOutbox(task, traceId);
            taskId = task.getId();
            log.info("[PERF][submit] taskId={} workflowId={} stage=submissionTx cost={}ms",
                    taskId, workflowId, elapsedMs(txStart));

            if (!mqEnabled && creditApprovalTaskTrigger != null) {
                long triggerStart = System.nanoTime();
                creditApprovalTaskTrigger.trigger(task);
                log.info("[PERF][submit] taskId={} workflowId={} stage=directTrigger cost={}ms",
                        taskId, workflowId, elapsedMs(triggerStart));
            }
            return task.getId();
        } finally {
            log.info("[PERF][submit] taskId={} workflowId={} stage=doSubmitAsync.total cost={}ms",
                    taskId, workflowId, elapsedMs(totalStart));
        }
    }

    static String buildRequestHash(Long userId, CreditApplySubmitRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("productId", request.getProductId());
        payload.put("applyAmount", request.getApplyAmount());
        payload.put("applyTerm", request.getApplyTerm());
        payload.put("purpose", request.getPurpose());
        payload.put("content", request.getContent());
        payload.put("loanPurpose", request.getLoanPurpose());
        payload.put("incomeDescription", request.getIncomeDescription());
        payload.put("occupationDescription", request.getOccupationDescription());
        payload.put("additionalDescription", request.getAdditionalDescription());
        payload.put("riskExplanation", request.getRiskExplanation());
        payload.put("income", request.getIncome());
        payload.put("occupation", request.getOccupation());
        payload.put("age", request.getAge());
        payload.put("contactInfo", request.getContactInfo());
        payload.put("documents", request.getDocuments());
        return DigestUtil.sha256Hex(JSONUtil.toJsonStr(payload));
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
