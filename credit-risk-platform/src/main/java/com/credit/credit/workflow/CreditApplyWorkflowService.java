package com.credit.credit.workflow;

import cn.hutool.json.JSONUtil;
import com.credit.agent.config.TraceIdInterceptor;
import com.credit.credit.approval.CreditApprovalEngine;
import com.credit.credit.approval.dto.CreditDecision;
import com.credit.credit.approval.dto.CreditDecisionContext;
import com.credit.credit.cache.CreditCacheService;
import com.credit.credit.dto.CreditAnalysisDTO;
import com.credit.credit.entity.CreditApplication;
import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.entity.CreditProduct;
import com.credit.credit.entity.ManualReviewTicket;
import com.credit.credit.enums.ApplicationStatus;
import com.credit.credit.limit.CreditLimitGrantService;
import com.credit.credit.mapper.CreditApplicationMapper;
import com.credit.credit.mapper.CreditProductMapper;
import com.credit.credit.risk.CreditRiskScoreService;
import com.credit.credit.risk.dto.RiskScoreInput;
import com.credit.credit.risk.dto.RiskScoreResult;
import com.credit.credit.service.CreditRecordService;
import com.credit.agent.entity.UserMemory;
import com.credit.agent.memory.UserMemoryAggregator;
import com.credit.agent.risk.enums.UserRiskLevel;
import com.credit.agent.workflow.AgentWorkflowTraceService;
import com.credit.agent.workflow.state.StateTransition;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class CreditApplyWorkflowService {

    @Resource
    private CreditRecordService creditRecordService;
    @Resource
    private CreditApplicationMapper creditApplicationMapper;
    @Resource
    private CreditProductMapper creditProductMapper;
    @Resource
    private CreditApprovalEngine creditApprovalEngine;
    @Resource
    private CreditRiskScoreService creditRiskScoreService;
    @Resource
    private UserMemoryAggregator userMemoryAggregator;
    @Resource
    private CreditLimitGrantService creditLimitGrantService;
    @Resource
    private AgentWorkflowTraceService agentWorkflowTraceService;
    @Resource
    private CreditCacheService creditCacheService;

    @Transactional(rollbackFor = Exception.class)
    public Long commit(CreditAsyncTask task, CreditAnalysisDTO analysis) {
        Long taskId = task != null ? task.getId() : null;
        String workflowId = task != null ? task.getWorkflowId() : null;
        if (analysis == null) {
            throw new IllegalArgumentException("分析结果为空");
        }

            CreditApplication app;
            if (task.getApplicationId() != null) {
                app = creditApplicationMapper.selectById(task.getApplicationId());
                if (app == null) {
                    throw new IllegalStateException("草稿申请不存在: " + task.getApplicationId());
                }
            } else {
                app = new CreditApplication();
                app.setApplicationNo("CA" + System.currentTimeMillis()
                        + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
                app.setUserId(task.getUserId());
                app.setProductId(task.getProductId());
                app.setApplyAmount(task.getApplyAmount());
                app.setApplyTerm(task.getApplyTerm());
                app.setPurpose(task.getPurpose());
                app.setContent(task.getContent());
                app.setSessionId(task.getSessionId());
                app.setWorkflowId(task.getWorkflowId());
                app.setIdempotencyKey(task.getIdempotencyKey());
                creditRecordService.saveApplication(app);
            }
            app.setVerifiedDocuments(Boolean.TRUE.equals(analysis.getVerifiedDocuments()));
            app.setDocumentStatus(Boolean.TRUE.equals(analysis.getVerifiedDocuments()) ? "VERIFIED" : "INCOMPLETE");
            app.setStatus(ApplicationStatus.PENDING_DECISION);
            app.setAiSummary(analysis.getAiSummary());
            app.setAiAnalysisJson(analysis.getAiAnalysisJson());
            app.setAiSuggestionJson(creditRecordService.buildAiSuggestionJson(analysis));
            app.setAgentSuggestion(analysis.getConsensusSuggestion() != null
                    ? analysis.getConsensusSuggestion() : analysis.getAgentSuggestion());
            app.setSuggestedAmount(analysis.getSuggestAmount());
            app.setSuggestedRate(analysis.getSuggestRate());
            app.setRiskLevel(analysis.getRiskLevel());
            app.setMinAgentConfidence(analysis.getMinAgentConfidence());
            app.setFraudScore(analysis.getFraudScore());
            app.setIncomeDebtRatio(analysis.getIncomeDebtRatio() != null
                    ? BigDecimal.valueOf(analysis.getIncomeDebtRatio()) : null);
            app.setDocumentScore(analysis.getDocumentScore() != null
                    ? BigDecimal.valueOf(analysis.getDocumentScore()) : null);
            app.setBureauUnavailable(Boolean.TRUE.equals(analysis.getBureauUnavailable()));
            app.setConflictDetected(Boolean.TRUE.equals(analysis.getConflictDetected()));
            if (analysis.getAgentConflicts() != null) {
                app.setAgentConflictsJson(JSONUtil.toJsonStr(analysis.getAgentConflicts()));
            }
            if (analysis.getConsensusJson() != null) {
                app.setConsensusJson(analysis.getConsensusJson());
            }
            creditRecordService.updateApplication(app);

            long ruleEngineStart = System.nanoTime();
            UserMemory userMem = userMemoryAggregator.refresh(task.getUserId());

            RiskScoreResult riskPreview = creditRiskScoreService.calculate(
                    RiskScoreInput.builder()
                            .bureauCreditScore(analysis.getBureauCreditScore())
                            .fraudScore(analysis.getFraudScore() != null ? analysis.getFraudScore() : 0)
                            .incomeDebtRatio(analysis.getIncomeDebtRatio())
                            .documentScore(analysis.getDocumentScore())
                            .minAgentConfidence(analysis.getMinAgentConfidence() != null
                                    ? analysis.getMinAgentConfidence().doubleValue() : null)
                            .highFrequencyApply(analysis.getRecentApplyCount7d() != null
                                    && analysis.getRecentApplyCount7d() > 5)
                            .bureauUnavailable(Boolean.TRUE.equals(analysis.getBureauUnavailable()))
                            .fraudHitRules(analysis.getFraudHitRules())
                            .build());

            CreditDecision decision = creditApprovalEngine.decide(
                    CreditDecisionContext.builder()
                            .verifiedDocuments(Boolean.TRUE.equals(analysis.getVerifiedDocuments()))
                            .userMemory(userMem)
                            .creditEligible(analysis.getCreditEligible())
                            .minAgentConfidence(analysis.getMinAgentConfidence() != null
                                    ? analysis.getMinAgentConfidence().doubleValue() : null)
                            .agentConflicts(analysis.getAgentConflicts())
                            .fraudScore(analysis.getFraudScore())
                            .fraudHitRules(analysis.getFraudHitRules())
                            .agentSuggestion(analysis.getAgentSuggestion())
                            .consensusSuggestion(analysis.getConsensusSuggestion())
                            .needManualReview(analysis.getNeedManualReview())
                            .conflictDetected(analysis.getConflictDetected())
                            .bureauUnavailable(analysis.getBureauUnavailable())
                            .applyAmount(task.getApplyAmount())
                            .applyTerm(task.getApplyTerm())
                            .productId(task.getProductId())
                            .agentRiskLevel(analysis.getRiskLevel())
                            .monthlyIncome(extractMonthlyIncome(task))
                            .bureauCreditScore(analysis.getBureauCreditScore())
                            .incomeDebtRatio(analysis.getIncomeDebtRatio())
                            .documentScore(analysis.getDocumentScore())
                            .highFrequencyApply(analysis.getRecentApplyCount7d() != null
                                    && analysis.getRecentApplyCount7d() > 5)
                            .recentApplyCount7d(analysis.getRecentApplyCount7d())
                            .riskScoreResult(riskPreview)
                            .agentVotes(analysis.getAgentVotes())
                            .build());
            log.info("[PERF][consume] taskId={} workflowId={} stage=ruleEngine cost={}ms decision={}",
                    taskId, workflowId, elapsedMs(ruleEngineStart), decision.getRoute());

            long dbUpdateStart = System.nanoTime();
            app.setRiskScore(decision.getRiskScore());
            app.setHitRulesJson(decision.getHitRules() != null ? JSONUtil.toJsonStr(decision.getHitRules()) : null);
            app.setDecisionReasonJson(decision.getDecisionReason() != null
                    ? JSONUtil.toJsonStr(decision.getDecisionReason()) : null);

            String platformRisk = decision.getRiskLevel() != null ? decision.getRiskLevel()
                    : (userMem.getRiskLevel() != null ? userMem.getRiskLevel() : UserRiskLevel.MEDIUM);
            String platformDecisionJson = buildPlatformDecisionJson(decision, platformRisk);
            app.setPlatformDecisionJson(platformDecisionJson);
            app.setFinalDecision(decision.getRoute());
            app.setRiskLevel(platformRisk);

            ManualReviewTicket ticket = null;
            if (CreditDecision.MANUAL_REVIEW.equals(decision.getRoute())) {
                StateTransition.check("credit_application", ApplicationStatus.PENDING_DECISION, ApplicationStatus.MANUAL_REVIEW);
                app.setStatus(ApplicationStatus.MANUAL_REVIEW);
                ticket = creditRecordService.createManualReviewTicket(
                        app,
                        analysis.getTicketTitle() != null ? analysis.getTicketTitle() : "信贷人工复核",
                        analysis.getTicketDescription() != null ? analysis.getTicketDescription() : task.getContent(),
                        decision.getDecisionReasonSummary(),
                        mapPriority(platformRisk));
            } else if (CreditDecision.APPROVED.equals(decision.getRoute())) {
                StateTransition.check("credit_application", ApplicationStatus.PENDING_DECISION, ApplicationStatus.APPROVED);
                app.setStatus(ApplicationStatus.APPROVED);
                app.setApprovedAmount(decision.getApprovedAmount());
                app.setApprovedRate(decision.getApprovedRate());
                int term = decision.getApprovedTerm() != null ? decision.getApprovedTerm()
                        : (task.getApplyTerm() != null ? task.getApplyTerm() : 12);
                creditLimitGrantService.grant(
                        task.getUserId(), task.getProductId(), app.getId(),
                        decision.getApprovedAmount(), term);
            } else {
                StateTransition.check("credit_application", ApplicationStatus.PENDING_DECISION, ApplicationStatus.REJECTED);
                app.setStatus(ApplicationStatus.REJECTED);
            }

            creditRecordService.updateApplication(app);
            creditCacheService.evictApplication(app.getId(), app.getUserId());
            creditCacheService.evictWorkflow(task.getWorkflowId());
            creditCacheService.evictRiskResult(task.getWorkflowId());

            creditRecordService.createAdvisory(
                    app,
                    ticket != null ? ticket.getId() : null,
                    decision.getApprovedAmount(),
                    decision.getApprovedRate(),
                    decision.getApprovedTerm(),
                    app.getAgentSuggestion(),
                    app.getAiSuggestionJson());

            userMemoryAggregator.refresh(task.getUserId());

            String traceId = task.getTraceId() != null ? task.getTraceId() : MDC.get(TraceIdInterceptor.MDC_KEY);
            agentWorkflowTraceService.append(
                    task.getWorkflowId(),
                    traceId,
                    "PlatformDecision",
                    "CreditApplyWorkflowService:commit",
                    "platform_decision",
                    platformDecisionJson,
                    decision.getRoute(),
                    null,
                    null,
                    decision.getAudit(),
                    null,
                    0,
                    null);

            log.info("[PERF][consume] taskId={} workflowId={} stage=workflowCommit cost={}ms applicationId={}",
                    taskId, workflowId, elapsedMs(dbUpdateStart), app.getId());

        log.info("[credit-commit] applicationId={} decision={} riskScore={} hitRules={} workflowId={}",
                app.getId(), decision.getRoute(), decision.getRiskScore(), decision.getHitRules(), task.getWorkflowId());
        return app.getId();
    }

    private static long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }

    private String buildPlatformDecisionJson(CreditDecision decision, String platformRisk) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("decision", decision.getRoute());
        payload.put("route", decision.getRoute());
        payload.put("reason", decision.getReason());
        payload.put("decisionReason", decision.getDecisionReason());
        payload.put("decisionReasonSummary", decision.getDecisionReasonSummary());
        payload.put("hitRules", decision.getHitRules());
        payload.put("riskScore", decision.getRiskScore());
        payload.put("riskLevel", decision.getRiskLevel());
        payload.put("platformRiskLevel", platformRisk);
        payload.put("needManualReview", decision.isNeedManualReview());
        payload.put("approvedAmount", decision.getApprovedAmount());
        payload.put("approvedRate", decision.getApprovedRate());
        payload.put("approvedTerm", decision.getApprovedTerm());
        payload.put("finalDecision", decision.getRoute());
        if (decision.getAudit() != null) {
            payload.put("audit", decision.getAudit());
        }
        return JSONUtil.toJsonStr(payload);
    }

    private String mapPriority(String riskLevel) {
        if (UserRiskLevel.HIGH.equals(riskLevel)) {
            return "HIGH";
        }
        if (UserRiskLevel.LOW.equals(riskLevel)) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private java.math.BigDecimal extractMonthlyIncome(CreditAsyncTask task) {
        if (task.getStructuredApplicationJson() == null) {
            return null;
        }
        try {
            cn.hutool.json.JSONObject json = cn.hutool.json.JSONUtil.parseObj(task.getStructuredApplicationJson());
            if (json.getBigDecimal("income") != null) {
                return json.getBigDecimal("income");
            }
        } catch (Exception ignored) {
            // ignore parse errors
        }
        return null;
    }
}
