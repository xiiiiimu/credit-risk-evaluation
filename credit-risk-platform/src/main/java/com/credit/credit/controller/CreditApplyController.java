package com.credit.credit.controller;

import com.credit.agent.config.TraceIdInterceptor;
import com.credit.credit.cache.CreditCacheService;
import com.credit.workflow.service.WorkflowPersistenceService;
import com.credit.credit.dto.CreditApplySubmitRequest;
import com.credit.credit.dto.CreditApplyVO;
import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.service.CreditApplyAsyncService;
import com.credit.credit.service.CreditRecordService;
import com.credit.agent.service.AgentConversationService;
import com.credit.common.Result;
import com.credit.common.context.UserHolder;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/credit/apply")
public class CreditApplyController {

    @Resource
    private CreditApplyAsyncService creditApplyAsyncService;
    @Resource
    private CreditRecordService creditRecordService;
    @Resource
    private AgentConversationService agentConversationService;
    @Resource
    private CreditCacheService creditCacheService;
    @Resource
    private WorkflowPersistenceService workflowPersistenceService;

    @PostMapping("/submit")
    public Result submit(
            @RequestBody CreditApplySubmitRequest request,
            @RequestHeader(value = TraceIdInterceptor.TRACE_HEADER, required = false) String traceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        if (request == null || request.getProductId() == null
                || request.getApplyAmount() == null
                || !hasMinimumInput(request)) {
            return Result.fail("productId、applyAmount 与申请说明不能为空");
        }
        Long userId = UserHolder.getUser().getId();
        String tid = traceId != null ? traceId : MDC.get(TraceIdInterceptor.MDC_KEY);
        if (tid == null) {
            tid = UUID.randomUUID().toString().replace("-", "");
        }
        String sessionId = agentConversationService.ensureSession(
                request.getSessionId(), userId, AgentConversationService.SCENE_CREDIT);

        String idemKey = request.getIdempotencyKey() != null ? request.getIdempotencyKey() : idempotencyHeader;
        Long taskId = creditApplyAsyncService.submitAsync(
                userId,
                request,
                sessionId,
                tid,
                idemKey);
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        CreditAsyncTask created = creditApplyAsyncService.getTask(taskId, userId);
        data.put("status", created != null ? created.getStatus() : CreditAsyncTask.PENDING);
        data.put("workflowId", created != null ? created.getWorkflowId() : null);
        data.put("pollUrl", "/api/credit/apply/task/" + taskId);
        return Result.ok(data);
    }

    @GetMapping("/task/{taskId:\\d+}")
    public Result taskStatus(@PathVariable("taskId") Long taskId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        Long userId = UserHolder.getUser().getId();
        CreditAsyncTask task = creditApplyAsyncService.getTask(taskId, userId);
        if (task == null) {
            return Result.fail("任务不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId());
        data.put("status", task.getStatus());
        data.put("applicationId", task.getApplicationId());
        data.put("errorMsg", task.getErrorMsg());
        data.put("workflowId", task.getWorkflowId());
        if (task.getWorkflowId() != null) {
            data.put("workflowExecution", workflowPersistenceService.getExecutionChain(task.getWorkflowId()));
        }
        if (CreditAsyncTask.SUCCESS.equals(task.getStatus())
                || CreditAsyncTask.MANUAL_REVIEW.equals(task.getStatus())) {
            data.put("application", creditApplyAsyncService.getResultIfReady(taskId, userId));
        }
        return Result.ok(data);
    }

    @GetMapping("/{id:\\d+}")
    public Result detail(@PathVariable("id") Long id) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        Long userId = UserHolder.getUser().getId();
        CreditApplyVO vo = creditCacheService.getApplication(id, creditRecordService::getDetail);
        if (vo == null || !userId.equals(vo.getUserId())) {
            return Result.fail("申请不存在");
        }
        return Result.ok(vo);
    }

    @GetMapping("/mine")
    public Result mine(@RequestParam(value = "limit", defaultValue = "10") int limit) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        Long userId = UserHolder.getUser().getId();
        return Result.ok(creditCacheService.getMyApplications(userId,
                uid -> creditRecordService.listByUser(uid, limit)));
    }

    private boolean hasMinimumInput(CreditApplySubmitRequest request) {
        if (request.getContent() != null && !request.getContent().trim().isEmpty()) {
            return true;
        }
        return hasText(request.getLoanPurpose())
                || hasText(request.getIncomeDescription())
                || hasText(request.getAdditionalDescription())
                || request.getIncome() != null
                || hasText(request.getOccupation());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
