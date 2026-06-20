package com.credit.credit.workflow;

import com.credit.credit.entity.CreditAdvisory;
import com.credit.credit.entity.CreditApplication;
import com.credit.credit.entity.CreditProduct;
import com.credit.credit.entity.ManualReviewTicket;
import com.credit.credit.enums.ApplicationStatus;
import com.credit.credit.enums.FinalDecision;
import com.credit.credit.feedback.CreditReviewFeedbackService;
import com.credit.credit.limit.CreditLimitGrantService;
import com.credit.credit.mapper.CreditAdvisoryMapper;
import com.credit.credit.mapper.CreditApplicationMapper;
import com.credit.credit.mapper.CreditProductMapper;
import com.credit.credit.mapper.ManualReviewTicketMapper;
import com.credit.credit.cache.CreditCacheService;
import com.credit.agent.workflow.state.StateTransition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;

@Slf4j
@Service
public class ManualReviewWorkflowService {

    @Resource
    private CreditApplicationMapper creditApplicationMapper;
    @Resource
    private CreditAdvisoryMapper creditAdvisoryMapper;
    @Resource
    private ManualReviewTicketMapper manualReviewTicketMapper;
    @Resource
    private CreditProductMapper creditProductMapper;
    @Resource
    private CreditLimitGrantService creditLimitGrantService;
    @Resource
    private CreditReviewFeedbackService creditReviewFeedbackService;
    @Resource
    private CreditCacheService creditCacheService;

    @Transactional(rollbackFor = Exception.class)
    public CreditApplication approve(Long applicationId, Long operatorId,
                                     BigDecimal approvedAmount, BigDecimal approvedRate,
                                     String reviewReason) {
        CreditApplication app = creditApplicationMapper.selectById(applicationId);
        if (app == null || !ApplicationStatus.MANUAL_REVIEW.equals(app.getStatus())) {
            return null;
        }
        StateTransition.check("credit_application", ApplicationStatus.MANUAL_REVIEW, ApplicationStatus.APPROVED);
        app.setStatus(ApplicationStatus.APPROVED);
        app.setFinalDecision(FinalDecision.APPROVED);
        app.setApprovedAmount(approvedAmount);
        app.setApprovedRate(approvedRate);
        creditApplicationMapper.updateById(app);

        CreditProduct product = creditProductMapper.selectById(app.getProductId());
        int term = app.getApplyTerm() != null ? app.getApplyTerm()
                : (product != null ? product.getTermMonths() : 12);
        creditLimitGrantService.grant(app.getUserId(), app.getProductId(), app.getId(), approvedAmount, term);

        resolveTicket(app, operatorId, reviewReason != null ? reviewReason : "人工审批通过");
        markAdvisoryDecided(app.getId());
        creditReviewFeedbackService.record(app, FinalDecision.APPROVED, reviewReason, operatorId);
        creditCacheService.evictApplication(app.getId(), app.getUserId());
        log.info("[credit-manual] approved applicationId={} operatorId={}", applicationId, operatorId);
        return app;
    }

    @Transactional(rollbackFor = Exception.class)
    public CreditApplication reject(Long applicationId, Long operatorId, String reason) {
        CreditApplication app = creditApplicationMapper.selectById(applicationId);
        if (app == null || !ApplicationStatus.MANUAL_REVIEW.equals(app.getStatus())) {
            return null;
        }
        StateTransition.check("credit_application", ApplicationStatus.MANUAL_REVIEW, ApplicationStatus.REJECTED);
        app.setStatus(ApplicationStatus.REJECTED);
        app.setFinalDecision(FinalDecision.REJECTED);
        creditApplicationMapper.updateById(app);

        resolveTicket(app, operatorId, reason != null ? reason : "人工审批拒绝");
        markAdvisoryDecided(app.getId());
        creditReviewFeedbackService.record(app, FinalDecision.REJECTED, reason, operatorId);
        creditCacheService.evictApplication(app.getId(), app.getUserId());
        log.info("[credit-manual] rejected applicationId={} operatorId={}", applicationId, operatorId);
        return app;
    }

    private void resolveTicket(CreditApplication app, Long operatorId, String note) {
        if (app.getTicketId() == null) {
            return;
        }
        ManualReviewTicket ticket = manualReviewTicketMapper.selectById(app.getTicketId());
        if (ticket == null) {
            return;
        }
        StateTransition.check("manual_review", ManualReviewTicket.OPEN, ManualReviewTicket.RESOLVED);
        ticket.setStatus(ManualReviewTicket.RESOLVED);
        ticket.setHandlerId(operatorId);
        ticket.setResolveNote(note);
        manualReviewTicketMapper.updateById(ticket);
    }

    private void markAdvisoryDecided(Long applicationId) {
        CreditAdvisory advisory = creditAdvisoryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CreditAdvisory>()
                        .eq("application_id", applicationId)
                        .orderByDesc("id")
                        .last("LIMIT 1"));
        if (advisory != null) {
            advisory.setStatus(CreditAdvisory.DECIDED);
            creditAdvisoryMapper.updateById(advisory);
        }
    }
}
