package com.credit.credit.service;

import cn.hutool.json.JSONUtil;
import com.credit.agent.config.TraceIdInterceptor;
import com.credit.credit.dto.CreditAnalysisDTO;
import com.credit.credit.dto.UploadedDocumentDTO;
import com.credit.credit.entity.CreditApplication;
import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.enums.ApplicationStatus;
import com.credit.credit.mapper.CreditApplicationMapper;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.credit.mq.exception.CreditApprovalTaskRetryableException;
import com.credit.credit.trace.CreditWorkflowTraceService;
import com.credit.credit.workflow.CreditApplyWorkflowService;
import com.credit.agent.dto.CreditAgentRequest;
import com.credit.agent.dto.CreditAnalysisResponse;
import com.credit.agent.facade.AgentRemoteClient;
import com.credit.agent.facade.AgentWorkflowLockConstants;
import com.credit.agent.health.AgentUnavailableException;
import com.credit.agent.exception.WorkflowRunningException;
import com.credit.input.dto.StructuredApplicationDTO;
import com.credit.input.dto.UserNarrativeDTO;
import com.credit.workflow.dto.WorkflowAcquireResult;
import com.credit.workflow.dto.WorkflowIdempotentResult;
import com.credit.workflow.enums.WorkflowIdempotentAction;
import com.credit.workflow.enums.WorkflowStatus;
import com.credit.workflow.service.WorkflowExecutionService;
import com.credit.workflow.service.WorkflowIdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class CreditApplyAsyncProcessor {

    @Resource
    private CreditAsyncTaskMapper creditAsyncTaskMapper;
    @Resource
    private CreditApplicationMapper creditApplicationMapper;
    @Resource
    private AgentRemoteClient agentRemoteClient;
    @Resource
    private CreditApplyWorkflowService creditApplyWorkflowService;
    @Resource
    private CreditDraftApplicationService creditDraftApplicationService;
    @Resource
    private CreditWorkflowTraceService creditWorkflowTraceService;
    @Resource
    private WorkflowExecutionService workflowExecutionService;
    @Resource
    private WorkflowIdempotencyService workflowIdempotencyService;
    @Resource
    private CreditApprovalTaskGuardService taskGuardService;

    /**
     * 兼容旧测试与直连触发器。
     */
    public void run(Long taskId) {
        processTask(taskId);
    }

    /**
     * 执行审批任务：复用 Workflow 锁/CAS/幂等，供 MQ Consumer 或直连模式调用。
     *
     * @return true 表示已处理（成功/失败落库）；false 表示 workflow 已在执行中，本次为重复投递应 ACK 跳过
     */
    public boolean processTask(Long taskId) {
        long totalStart = System.nanoTime();
        String workflowId = null;
        CreditAsyncTask task = null;
        String lockOwner = "task-" + taskId;
        boolean lockHeld = false;
        try {
            long loadTaskStart = System.nanoTime();
            task = creditAsyncTaskMapper.selectById(taskId);
            if (task == null) {
                log.warn("[credit-processor] task not found taskId={}", taskId);
                return true;
            }
            workflowId = task.getWorkflowId();
            if (taskGuardService.isTerminal(task.getStatus())) {
                log.info("[credit-processor] skip terminal taskId={} status={}", taskId, task.getStatus());
                return true;
            }
            if (task.getTraceId() != null) {
                MDC.put(TraceIdInterceptor.MDC_KEY, task.getTraceId());
            }

            long idempotencyStart = System.nanoTime();
            WorkflowIdempotentResult idem = workflowIdempotencyService.resolve(workflowId);
            if (WorkflowIdempotentAction.RETURN_RESULT.equals(idem.getAction())) {
                if (idem.getResultJson() != null) {
                    return completeFromCachedResult(task, taskId, workflowId, idem.getResultJson());
                }
                throw new CreditApprovalTaskRetryableException(
                        "workflow 结果尚未就绪: " + task.getWorkflowId());
            }
            if (shouldIdempotencyWaitSkip(idem)) {
                logIdempotencyWaitSkip(taskId, workflowId, idempotencyStart, idem.getAction(), idem.getStatus());
                return false;
            }

            long idempotencyAcquireStart = System.nanoTime();
            WorkflowAcquireResult acquire = workflowExecutionService.acquireForExecution(
                    task.getWorkflowId(),
                    task.getTraceId(),
                    taskId,
                    task.getApplicationId(),
                    lockOwner);
            lockHeld = acquire.isAcquired();
            log.info("[PERF][consume] taskId={} workflowId={} stage=idempotencyAcquire cost={}ms acquired={} action={} status={}",
                    taskId, workflowId, elapsedMs(idempotencyAcquireStart),
                    acquire.isAcquired(), acquire.getIdempotentAction(), acquire.getStatus());

            if (WorkflowIdempotentAction.RETURN_RESULT.equals(acquire.getIdempotentAction())) {
                if (acquire.getCachedResultJson() != null) {
                    return completeFromCachedResult(task, taskId, workflowId, acquire.getCachedResultJson());
                }
                throw new CreditApprovalTaskRetryableException(
                        "workflow 结果尚未就绪: " + task.getWorkflowId());
            }
            if (shouldIdempotencyWaitSkip(acquire)) {
                logIdempotencyWaitSkip(taskId, workflowId, idempotencyStart,
                        acquire.getIdempotentAction(), acquire.getStatus());
                return false;
            }
            if (!acquire.isAcquired()) {
                throw new CreditApprovalTaskRetryableException(
                        "workflow 获取执行权失败: " + task.getWorkflowId());
            }
            if (!WorkflowIdempotentAction.RUN.equals(acquire.getIdempotentAction())) {
                throw new CreditApprovalTaskRetryableException(
                        "workflow 幂等动作不可执行: " + acquire.getIdempotentAction());
            }

            task.setStatus(CreditAsyncTask.RUNNING);
            creditAsyncTaskMapper.updateById(task);
            if (task.getApplicationId() == null) {
                CreditApplication draft = creditDraftApplicationService.createDraft(task);
                task.setApplicationId(draft.getId());
                creditAsyncTaskMapper.updateById(task);
            }
            CreditAgentRequest req = buildAgentRequest(task, lockOwner);
            logPerf(taskId, workflowId, "loadTask", loadTaskStart);

            long agentConfirmStart = System.nanoTime();
            WorkflowIdempotentResult agentConfirm = workflowIdempotencyService.resolve(workflowId);
            if (WorkflowIdempotentAction.RETURN_RESULT.equals(agentConfirm.getAction())) {
                if (agentConfirm.getResultJson() != null) {
                    return completeFromCachedResult(task, taskId, workflowId, agentConfirm.getResultJson());
                }
                throw new CreditApprovalTaskRetryableException(
                        "workflow 结果尚未就绪: " + task.getWorkflowId());
            }
            if (shouldIdempotencyWaitSkip(agentConfirm)) {
                logIdempotencyWaitSkip(taskId, workflowId, agentConfirmStart,
                        agentConfirm.getAction(), agentConfirm.getStatus());
                return false;
            }
            if (shouldSkipAgentForRunningNotOwned(agentConfirm, workflowId, lockOwner)) {
                log.info("[PERF][consume] taskId={} workflowId={} stage=agentCall.confirmSkip cost={}ms "
                                + "status={} lockOwner={} lockHeldBySelf={}",
                        taskId, workflowId, elapsedMs(agentConfirmStart),
                        agentConfirm.getStatus(), lockOwner,
                        workflowExecutionService.isLockHeldBy(workflowId, lockOwner));
                return false;
            }

            CreditAnalysisResponse resp;
            long agentCallStart = System.nanoTime();
            logPerf(taskId, workflowId, "agentCall.start", agentCallStart);
            try {
                resp = agentRemoteClient.analyzeCreditApplication(req, task.getTraceId());
                logPerf(taskId, workflowId, "agentCall.end", agentCallStart);
            } catch (AgentUnavailableException e) {
                logPerf(taskId, workflowId, "agentCall.end", agentCallStart);
                log.warn("[credit-processor] agent unavailable workflowId={} msg={}",
                        task.getWorkflowId(), e.getMessage());
                resp = buildAgentUnavailableResponse(task);
            } catch (ResourceAccessException e) {
                logPerf(taskId, workflowId, "agentCall.end", agentCallStart);
                throw new CreditApprovalTaskRetryableException(
                        "Agent 调用网络异常: " + e.getMessage(), e);
            } catch (WorkflowRunningException e) {
                logPerf(taskId, workflowId, "agentCall.end", agentCallStart);
                log.warn("[credit-processor] unexpected workflow running during agent call workflowId={} msg={}",
                        task.getWorkflowId(), e.getMessage());
                logIdempotencyWaitSkip(taskId, workflowId, agentCallStart,
                        WorkflowIdempotentAction.WAIT, WorkflowStatus.RUNNING);
                return false;
            }
            if (resp == null) {
                throw new CreditApprovalTaskRetryableException("信贷分析 Agent 无响应");
            }
            CreditAnalysisDTO dto = toDto(resp, task.getWorkflowId());
            Long applicationId = creditApplyWorkflowService.commit(task, dto);
            creditWorkflowTraceService.backfillApplicationId(task.getWorkflowId(), applicationId, taskId);
            task.setApplicationId(applicationId);
            task.setStatus(resolveTerminalTaskStatus(applicationId));
            task.setErrorMsg(null);
            log.info("[credit-processor] success taskId={} workflowId={} applicationId={} taskStatus={}",
                    taskId, task.getWorkflowId(), applicationId, task.getStatus());
            return true;
        } catch (CreditApprovalTaskRetryableException e) {
            if (task != null) {
                task.setStatus(CreditAsyncTask.RUNNING);
                task.setErrorMsg(e.getMessage());
            }
            throw e;
        } catch (Exception e) {
            WorkflowRunningException running = unwrapWorkflowRunning(e);
            if (running != null) {
                log.warn("[credit-processor] unexpected workflow running workflowId={} msg={}",
                        workflowId, running.getMessage());
                logIdempotencyWaitSkip(taskId, workflowId, totalStart,
                        WorkflowIdempotentAction.WAIT, WorkflowStatus.RUNNING);
                return false;
            }
            log.error("[credit-processor] failed taskId={} workflowId={}", taskId, workflowId, e);
            if (task != null) {
                task.setStatus(CreditAsyncTask.FAILED);
                task.setErrorMsg(e.getMessage());
            }
            return true;
        } finally {
            long taskUpdateStart = System.nanoTime();
            if (task != null) {
                if (lockHeld) {
                    workflowExecutionService.releaseLock(task.getWorkflowId());
                }
                creditAsyncTaskMapper.updateById(task);
            }
            logPerf(taskId, workflowId, "taskUpdate", taskUpdateStart);
            logPerf(taskId, workflowId, "processTask.total", totalStart);
            MDC.remove(TraceIdInterceptor.MDC_KEY);
        }
    }

    private String resolveTerminalTaskStatus(Long applicationId) {
        CreditApplication app = creditApplicationMapper.selectById(applicationId);
        if (app != null && ApplicationStatus.MANUAL_REVIEW.equals(app.getStatus())) {
            return CreditAsyncTask.MANUAL_REVIEW;
        }
        return CreditAsyncTask.SUCCESS;
    }

    private CreditAgentRequest buildAgentRequest(CreditAsyncTask task, String lockOwner) {
        CreditAgentRequest req = new CreditAgentRequest();
        req.setUserId(task.getUserId());
        req.setProductId(task.getProductId());
        req.setApplyAmount(task.getApplyAmount());
        req.setApplyTerm(task.getApplyTerm());
        req.setPurpose(task.getPurpose());
        req.setContent(task.getContent());
        req.setSessionId(task.getSessionId());
        req.setTraceId(task.getTraceId());
        req.setWorkflowId(task.getWorkflowId());
        req.setApplicationId(task.getApplicationId());
        req.setTaskId(task.getId());
        req.setLockMode(AgentWorkflowLockConstants.MODE_JAVA_OWNED);
        req.setLockOwner(lockOwner);

        if (task.getStructuredApplicationJson() != null) {
            StructuredApplicationDTO structured = JSONUtil.toBean(
                    task.getStructuredApplicationJson(), StructuredApplicationDTO.class);
            req.setIncome(structured.getIncome());
            req.setOccupation(structured.getOccupation());
            req.setAge(structured.getAge());
            req.setContactInfo(structured.getContactInfo());
            req.setStructuredApplication(JSONUtil.parseObj(task.getStructuredApplicationJson()));
        }
        if (task.getUserNarrativeJson() != null) {
            UserNarrativeDTO narrative = JSONUtil.toBean(task.getUserNarrativeJson(), UserNarrativeDTO.class);
            req.setLoanPurpose(narrative.getLoanPurpose());
            req.setIncomeDescription(narrative.getIncomeDescription());
            req.setOccupationDescription(narrative.getOccupationDescription());
            req.setAdditionalDescription(narrative.getAdditionalDescription());
            req.setRiskExplanation(narrative.getRiskExplanation());
            req.setUserNarrative(JSONUtil.parseObj(task.getUserNarrativeJson()));
        }
        if (task.getUploadedDocumentsJson() != null) {
            List<UploadedDocumentDTO> docs = JSONUtil.toList(
                    task.getUploadedDocumentsJson(), UploadedDocumentDTO.class);
            req.setDocuments(docs);
        }
        return req;
    }

    private CreditAnalysisDTO toDto(CreditAnalysisResponse resp, String workflowId) {
        CreditAnalysisDTO dto = new CreditAnalysisDTO();
        dto.setWorkflowId(workflowId != null ? workflowId : resp.getWorkflowId());
        dto.setPurpose(resp.getPurpose());
        dto.setVerifiedDocuments(resp.getVerifiedDocuments());
        dto.setCreditEligible(resp.getCreditEligible());
        dto.setAiSummary(resp.getSummary());
        dto.setAiAnalysisJson(resp.getAiAnalysisJson());
        dto.setAgentSuggestion(resp.getAgentSuggestion());
        dto.setConsensusSuggestion(resp.getConsensusSuggestion());
        dto.setNeedManualReview(resp.getNeedManualReview());
        dto.setConflictDetected(resp.getConflictDetected());
        dto.setBureauUnavailable(resp.getBureauUnavailable());
        dto.setRiskLevel(resp.getRiskLevel());
        dto.setRiskScore(resp.getRiskScore());
        dto.setMinAgentConfidence(resp.getMinAgentConfidence());
        dto.setAgentConflicts(resp.getAgentConflicts());
        dto.setFraudHitRules(resp.getFraudHitRules());
        dto.setFraudScore(resp.getFraudScore());
        dto.setBureauCreditScore(resp.getBureauCreditScore());
        dto.setIncomeDebtRatio(resp.getIncomeDebtRatio());
        dto.setDocumentScore(resp.getDocumentScore());
        dto.setAgentVotes(resp.getAgentVotes());
        dto.setConsensusJson(resp.getConsensusJson());
        dto.setRecentApplyCount7d(resp.getRecentApplyCount7d());
        dto.setReason(resp.getReason());
        dto.setAnswer(resp.getAnswer());
        dto.setDegraded(resp.getDegraded());
        dto.setTicketTitle(resp.getTicketTitle());
        dto.setTicketDescription(resp.getTicketDescription());
        return dto;
    }

    private CreditAnalysisResponse buildAgentUnavailableResponse(CreditAsyncTask task) {
        CreditAnalysisResponse resp = new CreditAnalysisResponse();
        resp.setWorkflowId(task.getWorkflowId());
        resp.setPurpose(task.getPurpose());
        resp.setNeedManualReview(true);
        resp.setAgentSuggestion("SUGGEST_MANUAL");
        resp.setConsensusSuggestion("SUGGEST_MANUAL");
        resp.setReason("Agent 服务不可用，转人工审核");
        resp.setSummary("Agent 服务不可用，转人工审核");
        return resp;
    }

    private boolean completeFromCachedResult(CreditAsyncTask task, Long taskId, String workflowId,
                                           String cachedResultJson) {
        CreditAnalysisResponse resp = JSONUtil.toBean(cachedResultJson, CreditAnalysisResponse.class);
        log.info("[credit-processor] workflow idempotent hit workflowId={}", workflowId);
        logPerf(taskId, workflowId, "agentCall.start", System.nanoTime());
        logPerf(taskId, workflowId, "agentCall.end", System.nanoTime());
        if (task.getApplicationId() == null) {
            CreditApplication draft = creditDraftApplicationService.createDraft(task);
            task.setApplicationId(draft.getId());
        }
        task.setStatus(CreditAsyncTask.RUNNING);
        CreditAnalysisDTO dto = toDto(resp, workflowId);
        Long applicationId = creditApplyWorkflowService.commit(task, dto);
        creditWorkflowTraceService.backfillApplicationId(workflowId, applicationId, taskId);
        task.setApplicationId(applicationId);
        task.setStatus(resolveTerminalTaskStatus(applicationId));
        task.setErrorMsg(null);
        return true;
    }

    private boolean shouldSkipAgentForRunningNotOwned(WorkflowIdempotentResult confirm,
                                                      String workflowId, String lockOwner) {
        if (confirm == null || confirm.getStatus() == null) {
            return false;
        }
        if (!WorkflowStatus.RUNNING.equals(confirm.getStatus())
                && !WorkflowStatus.PENDING.equals(confirm.getStatus())) {
            return false;
        }
        return !workflowExecutionService.isLockHeldBy(workflowId, lockOwner);
    }

    private static boolean shouldIdempotencyWaitSkip(WorkflowIdempotentResult idem) {
        if (idem == null || !WorkflowIdempotentAction.WAIT.equals(idem.getAction())) {
            return false;
        }
        return WorkflowStatus.RUNNING.equals(idem.getStatus())
                || WorkflowStatus.PENDING.equals(idem.getStatus());
    }

    private static boolean shouldIdempotencyWaitSkip(WorkflowAcquireResult acquire) {
        if (acquire == null || acquire.isAcquired()) {
            return false;
        }
        if (!WorkflowIdempotentAction.WAIT.equals(acquire.getIdempotentAction())) {
            return false;
        }
        String status = acquire.getStatus();
        return status == null
                || WorkflowStatus.RUNNING.equals(status)
                || WorkflowStatus.PENDING.equals(status)
                || WorkflowStatus.INIT.equals(status);
    }

    private static void logIdempotencyWaitSkip(Long taskId, String workflowId, long startNano,
                                               String action, String status) {
        log.info("[PERF][consume] taskId={} workflowId={} stage=idempotencyWaitSkip cost={}ms action={} status={}",
                taskId, workflowId, elapsedMs(startNano), action, status);
    }

    private static void logPerf(Long taskId, String workflowId, String stage, long startNano) {
        log.info("[PERF][consume] taskId={} workflowId={} stage={} cost={}ms",
                taskId, workflowId, stage, elapsedMs(startNano));
    }

    private static long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }

    private static WorkflowRunningException unwrapWorkflowRunning(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof WorkflowRunningException) {
                return (WorkflowRunningException) cur;
            }
            cur = cur.getCause();
        }
        return null;
    }
}
