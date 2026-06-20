package com.credit.credit.e2e;

import com.credit.credit.approval.dto.CreditDecision;
import com.credit.credit.dto.CreditAnalysisDTO;
import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.testsupport.CreditAnalysisFixtures;
import com.credit.credit.testsupport.CreditApplyPipelineTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E：Mock Agent 分析结果 → Java Rule Engine 终审。
 */
class CreditApplyPipelineE2ETest {

    private CreditApplyPipelineTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new CreditApplyPipelineTestHarness();
    }

    @Test
    void e2e_lowRiskApplication_finalApprovedByJavaRuleEngine() {
        CreditAsyncTask task = harness.newTask(new BigDecimal("50000"), 12);
        CreditAnalysisDTO agentView = CreditAnalysisFixtures.lowRiskApprovedAgentView();

        CreditDecision decision = harness.commit(task, agentView);

        assertEquals(CreditDecision.APPROVED, decision.getRoute());
        assertNotNull(decision.getApprovedAmount());
        assertNotNull(decision.getApprovedRate());
        assertEquals(Integer.valueOf(12), decision.getApprovedTerm());
        assertTrue(decision.getApprovedAmount().compareTo(new BigDecimal("50000")) <= 0);
    }

    @Test
    void e2e_mediumRiskApplication_reducedAmountOrManualReview() {
        harness.useProductRules(1.0, 0.6, 0.0, 1.2);
        CreditAsyncTask task = harness.newTask(new BigDecimal("150000"), 24);
        CreditAnalysisDTO agentView = CreditAnalysisFixtures.mediumRiskAgentView();

        CreditDecision decision = harness.commit(task, agentView);

        assertEquals(CreditDecision.APPROVED, decision.getRoute());
        assertTrue(decision.getApprovedAmount().compareTo(new BigDecimal("150000")) < 0);
        assertTrue(decision.getApprovedRate().compareTo(new BigDecimal("4.8")) > 0);
    }

    @Test
    void e2e_highFraudApplication_finalRejected() {
        CreditAsyncTask task = harness.newTask(new BigDecimal("30000"), 12);
        CreditAnalysisDTO agentView = CreditAnalysisFixtures.highFraudAgentView();

        CreditDecision decision = harness.commit(task, agentView);

        assertEquals(CreditDecision.REJECTED, decision.getRoute());
    }

    @Test
    void e2e_ocrLowConfidenceApplication_finalRejectedOrManualReview() {
        CreditAsyncTask task = harness.newTask(new BigDecimal("20000"), 12);
        CreditAnalysisDTO agentView = CreditAnalysisFixtures.ocrLowConfidenceManualReviewView();

        CreditDecision decision = harness.commit(task, agentView);

        assertTrue(
                CreditDecision.REJECTED.equals(decision.getRoute())
                        || CreditDecision.MANUAL_REVIEW.equals(decision.getRoute()),
                "OCR 低置信度应拒绝或转人工，实际=" + decision.getRoute());
    }
}
