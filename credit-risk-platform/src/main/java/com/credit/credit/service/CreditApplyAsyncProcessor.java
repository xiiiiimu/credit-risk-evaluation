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
import com.credit.agent.health.AgentUnavailableException;
import com.credit.input.dto.StructuredApplicationDTO;
import com.credit.input.dto.UserNarrativeDTO;
import com.credit.workflow.dto.WorkflowAcquireResult;
import com.credit.workflow.enums.WorkflowIdempotentAction;
import com.credit.workflow.service.WorkflowExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private CreditApprovalTaskGuardService taskGuardService;

    /**
     * 兼容旧测试与直连触发器。
     */
    public void run(Long taskId) {
        processTask(taskId);
    }

    /**
     * 执行审批任务：复用 Workflow 锁/CAS/幂等，供 MQ Consumer 或直连模式调用。
     */
    public void processTask(Long taskId) {
        CreditAsyncTask task = creditAsyncTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[credit-processor] task not found taskId={}", taskId);
            return;
        }
        if (taskGuardService.isTerminal(task.getStatus())) {
            log.info("[credit-processor] skip terminal taskId={} status={}", taskId, task.getStatus());
            return;
        }
        if (task.getTraceId() != null) {
            MDC.put(TraceIdInterceptor.MDC_KEY, task.getTraceId());
        }
        task.setStatus(CreditAsyncTask.RUNNING);
        creditAsyncTaskMapper.updateById(task);
        String lockOwner = "task-" + taskId;
        boolean lockHeld = false;
        try {
            CreditApplication draft = creditDraftApplicationService.createDraft(task);
            task.setApplicationId(draft.getId());
            creditAsyncTaskMapper.updateById(task);

            CreditAgentRequest req = buildAgentRequest(task);

            CreditAnalysisResponse resp;
            WorkflowAcquireResult acquire = workflowExecutionService.acquireForExecution(
                    task.getWorkflowId(),
                    task.getTraceId(),
                    taskId,
                    draft.getId(),
                    lockOwner);
            lockHeld = acquire.isAcquired();

            if (WorkflowIdempotentAction.RETURN_RESULT.equals(acquire.getIdempotentAction())
                    && acquire.getCachedResultJson() != null) {
                resp = JSONUtil.toBean(acquire.getCachedResultJson(), CreditAnalysisResponse.class);
                log.info("[credit-processor] workflow idempotent hit workflowId={}", task.getWorkflowId());
            } else if (WorkflowIdempotentAction.WAIT.equals(acquire.getIdempotentAction())) {
                throw new CreditApprovalTaskRetryableException(
                        "workflow 正在执行: " + task.getWorkflowId());
            } else if (!acquire.isAcquired()) {
                throw new CreditApprovalTaskRetryableException(
                        "workflow 获取执行权失败: " + task.getWorkflowId());
            } else {
                try {
                    resp = agentRemoteClient.analyzeCreditApplication(req, task.getTraceId());
                } catch (AgentUnavailableException e) {
                    log.warn("[credit-processor] agent unavailable workflowId={} msg={}",
                            task.getWorkflowId(), e.getMessage());
                    resp = buildAgentUnavailableResponse(task);
                } catch (ResourceAccessException e) {
                    throw new CreditApprovalTaskRetryableException(
                            "Agent 调用网络异常: " + e.getMessage(), e);
                }
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
        } catch (CreditApprovalTaskRetryableException e) {
            task.setStatus(CreditAsyncTask.RUNNING);
            task.setErrorMsg(e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[credit-processor] failed taskId={} workflowId={}", taskId, task.getWorkflowId(), e);
            task.setStatus(CreditAsyncTask.FAILED);
            task.setErrorMsg(e.getMessage());
        } finally {
            if (lockHeld) {
                workflowExecutionService.releaseLock(task.getWorkflowId());
            }
            creditAsyncTaskMapper.updateById(task);
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

    private CreditAgentRequest buildAgentRequest(CreditAsyncTask task) {
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
}
