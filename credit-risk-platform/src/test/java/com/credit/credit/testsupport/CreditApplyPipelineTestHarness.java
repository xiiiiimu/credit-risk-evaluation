package com.credit.credit.testsupport;

import cn.hutool.json.JSONObject;
import com.credit.agent.entity.UserMemory;
import com.credit.agent.memory.UserMemoryAggregator;
import com.credit.agent.risk.enums.UserRiskLevel;
import com.credit.agent.workflow.AgentWorkflowTraceService;
import com.credit.credit.approval.CreditApprovalEngine;
import com.credit.credit.approval.ProductApprovalCalculator;
import com.credit.credit.approval.dto.CreditDecision;
import com.credit.credit.cache.CreditCacheService;
import com.credit.credit.dto.CreditAnalysisDTO;
import com.credit.credit.entity.CreditApplication;
import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.entity.CreditProduct;
import com.credit.credit.enums.ApplicationStatus;
import com.credit.credit.limit.CreditLimitGrantService;
import com.credit.credit.mapper.CreditApplicationMapper;
import com.credit.credit.mapper.CreditProductMapper;
import com.credit.credit.risk.CreditRiskScoreService;
import com.credit.credit.service.CreditRecordService;
import com.credit.credit.workflow.CreditApplyWorkflowService;
import com.credit.product.service.CreditProductService;
import com.credit.product.service.ProductRuleConfigService;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 申请终审链路测试 Harness：真实 Rule Engine + Mock DB/外部依赖。
 */
public class CreditApplyPipelineTestHarness {

    private final CreditApplyWorkflowService workflowService = new CreditApplyWorkflowService();
    private final CreditApprovalEngine approvalEngine = new CreditApprovalEngine();
    private final ProductApprovalCalculator productCalculator = new ProductApprovalCalculator();
    private final CreditRiskScoreService riskScoreService = new CreditRiskScoreService();

    private final CreditRecordService creditRecordService = mock(CreditRecordService.class);
    private final CreditApplicationMapper creditApplicationMapper = mock(CreditApplicationMapper.class);
    private final CreditProductMapper creditProductMapper = mock(CreditProductMapper.class);
    private final UserMemoryAggregator userMemoryAggregator = mock(UserMemoryAggregator.class);
    private final CreditLimitGrantService creditLimitGrantService = mock(CreditLimitGrantService.class);
    private final AgentWorkflowTraceService agentWorkflowTraceService = mock(AgentWorkflowTraceService.class);
    private final CreditCacheService creditCacheService = mock(CreditCacheService.class);
    private final CreditProductService creditProductService = mock(CreditProductService.class);
    private final MutableProductRuleConfigService productRuleConfigService = new MutableProductRuleConfigService();

    private final AtomicReference<CreditApplication> lastSavedApp = new AtomicReference<>();
    private CreditApplication draftApplication;
    private CreditProduct activeProduct;

    public CreditApplyPipelineTestHarness() {
        wireServices();
        setupDefaultProductAndRules();
        setupDefaultUserMemory();
    }

    private void wireServices() {
        ReflectionTestUtils.setField(riskScoreService, "ruleConfigService", new RuleConfigServiceStub());
        ReflectionTestUtils.setField(productCalculator, "creditProductService", creditProductService);
        ReflectionTestUtils.setField(productCalculator, "productRuleConfigService", productRuleConfigService);
        ReflectionTestUtils.setField(approvalEngine, "riskProperties", TestAgentRiskProperties.lowRiskAutoApprove());
        ReflectionTestUtils.setField(approvalEngine, "creditRiskScoreService", riskScoreService);
        ReflectionTestUtils.setField(approvalEngine, "ruleConfigService", new RuleConfigServiceStub());
        ReflectionTestUtils.setField(approvalEngine, "productApprovalCalculator", productCalculator);

        ReflectionTestUtils.setField(workflowService, "creditRecordService", creditRecordService);
        ReflectionTestUtils.setField(workflowService, "creditApplicationMapper", creditApplicationMapper);
        ReflectionTestUtils.setField(workflowService, "creditProductMapper", creditProductMapper);
        ReflectionTestUtils.setField(workflowService, "creditApprovalEngine", approvalEngine);
        ReflectionTestUtils.setField(workflowService, "creditRiskScoreService", riskScoreService);
        ReflectionTestUtils.setField(workflowService, "userMemoryAggregator", userMemoryAggregator);
        ReflectionTestUtils.setField(workflowService, "creditLimitGrantService", creditLimitGrantService);
        ReflectionTestUtils.setField(workflowService, "agentWorkflowTraceService", agentWorkflowTraceService);
        ReflectionTestUtils.setField(workflowService, "creditCacheService", creditCacheService);

        lenient().when(creditRecordService.buildAiSuggestionJson(any())).thenReturn("{}");
        doAnswer(inv -> {
            lastSavedApp.set(inv.getArgument(0));
            return null;
        }).when(creditRecordService).updateApplication(any());
        lenient().when(creditRecordService.createAdvisory(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
        lenient().when(creditRecordService.createManualReviewTicket(any(), any(), any(), any(), any()))
                .thenReturn(null);
    }

    public void setupDefaultProductAndRules() {
        activeProduct = new CreditProduct();
        activeProduct.setId(1L);
        activeProduct.setName("消费信用贷");
        activeProduct.setMinAmount(new BigDecimal("1000"));
        activeProduct.setMaxAmount(new BigDecimal("200000"));
        activeProduct.setInterestRate(new BigDecimal("4.8"));
        activeProduct.setSupportedTermsJson("[6,12,24,36]");
        activeProduct.setStatus(CreditProductService.STATUS_ACTIVE);

        when(creditProductService.getActiveProduct(1L)).thenReturn(activeProduct);
        when(creditProductService.parseSupportedTerms(activeProduct)).thenReturn(Arrays.asList(6, 12, 24, 36));
        productRuleConfigService.setEnabledJson(defaultProductRules(1.0, 0.6, 0.0, 1.2));
    }

    public void setupDefaultUserMemory() {
        UserMemory user = new UserMemory();
        user.setUserId(1001L);
        user.setRiskLevel(UserRiskLevel.LOW);
        user.setComplaintCount7d(0);
        when(userMemoryAggregator.refresh(1001L)).thenReturn(user);
    }

    public CreditAsyncTask newTask(BigDecimal amount, int term) {
        CreditAsyncTask task = new CreditAsyncTask();
        task.setId(99L);
        task.setUserId(1001L);
        task.setProductId(1L);
        task.setApplyAmount(amount);
        task.setApplyTerm(term);
        task.setPurpose("消费");
        task.setContent("低风险正常申请");
        task.setWorkflowId("wf-e2e-" + System.nanoTime());
        task.setStructuredApplicationJson("{\"income\":12000}");

        draftApplication = new CreditApplication();
        draftApplication.setId(501L);
        draftApplication.setUserId(task.getUserId());
        draftApplication.setProductId(task.getProductId());
        draftApplication.setApplyAmount(task.getApplyAmount());
        draftApplication.setApplyTerm(task.getApplyTerm());
        draftApplication.setStatus(ApplicationStatus.DRAFT);
        task.setApplicationId(draftApplication.getId());

        when(creditApplicationMapper.selectById(draftApplication.getId())).thenReturn(draftApplication);
        return task;
    }

    public CreditDecision commit(CreditAsyncTask task, CreditAnalysisDTO analysis) {
        workflowService.commit(task, analysis);
        CreditApplication saved = lastSavedApp.get();
        if (saved == null) {
            throw new IllegalStateException("application not persisted");
        }
        return CreditDecision.builder()
                .route(saved.getFinalDecision())
                .approvedAmount(saved.getApprovedAmount())
                .approvedRate(saved.getApprovedRate())
                .approvedTerm(parseApprovedTerm(saved))
                .needManualReview(CreditDecision.MANUAL_REVIEW.equals(saved.getFinalDecision()))
                .build();
    }

    private Integer parseApprovedTerm(CreditApplication app) {
        if (app.getPlatformDecisionJson() == null) {
            return app.getApplyTerm();
        }
        JSONObject json = cn.hutool.json.JSONUtil.parseObj(app.getPlatformDecisionJson());
        return json.getInt("approvedTerm");
    }

    public void useProductRules(double lowRatio, double mediumRatio, double highRatio, double mediumRateAdj) {
        productRuleConfigService.setEnabledJson(defaultProductRules(lowRatio, mediumRatio, highRatio, mediumRateAdj));
    }

    public static JSONObject defaultProductRules(double lowRatio, double mediumRatio, double highRatio,
                                                 double mediumRateAdj) {
        JSONObject json = new JSONObject();
        json.set("riskLevelAmountRatio", cn.hutool.json.JSONUtil.parseObj(String.format(
                "{\"LOW\":%s,\"MEDIUM\":%s,\"HIGH\":%s}", lowRatio, mediumRatio, highRatio)));
        json.set("riskLevelRateAdjustment", cn.hutool.json.JSONUtil.parseObj(String.format(
                "{\"LOW\":0.0,\"MEDIUM\":%s,\"HIGH\":0.0}", mediumRateAdj)));
        json.set("rejectThreshold", 80);
        json.set("manualReviewThreshold", 60);
        json.set("maxDebtIncomeRatio", 0.5);
        return json;
    }

    public CreditApplication lastApplication() {
        return lastSavedApp.get();
    }

    public ProductRuleConfigService productRuleConfigService() {
        return productRuleConfigService;
    }

    public CreditProductService creditProductService() {
        return creditProductService;
    }

    public ProductApprovalCalculator productCalculator() {
        return productCalculator;
    }

    public CreditApprovalEngine approvalEngine() {
        return approvalEngine;
    }
}
