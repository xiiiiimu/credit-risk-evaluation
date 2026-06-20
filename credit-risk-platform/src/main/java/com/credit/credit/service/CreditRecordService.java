package com.credit.credit.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.credit.credit.dto.CreditApplyVO;
import com.credit.credit.entity.CreditAdvisory;
import com.credit.credit.entity.CreditApplication;
import com.credit.credit.entity.CreditProduct;
import com.credit.credit.entity.ManualReviewTicket;
import com.credit.credit.enums.ApplicationStatus;
import com.credit.credit.mapper.CreditAdvisoryMapper;
import com.credit.credit.mapper.CreditApplicationMapper;
import com.credit.credit.mapper.CreditProductMapper;
import com.credit.credit.mapper.ManualReviewTicketMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CreditRecordService {

    @Resource
    private CreditApplicationMapper creditApplicationMapper;
    @Resource
    private CreditProductMapper creditProductMapper;
    @Resource
    private ManualReviewTicketMapper manualReviewTicketMapper;
    @Resource
    private CreditAdvisoryMapper creditAdvisoryMapper;

    public CreditApplication saveApplication(CreditApplication app) {
        creditApplicationMapper.insert(app);
        return app;
    }

    public CreditApplication getEntity(Long id) {
        return creditApplicationMapper.selectById(id);
    }

    public CreditApplyVO getDetail(Long id) {
        CreditApplication app = creditApplicationMapper.selectById(id);
        if (app == null) {
            return null;
        }
        return toVO(app);
    }

    public List<CreditApplyVO> listByUser(Long userId, int limit) {
        return creditApplicationMapper.selectList(
                new QueryWrapper<CreditApplication>()
                        .eq("user_id", userId)
                        .orderByDesc("create_time")
                        .last("LIMIT " + Math.min(limit, 50)))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public List<CreditApplyVO> listAll(String status, int limit) {
        QueryWrapper<CreditApplication> qw = new QueryWrapper<CreditApplication>()
                .orderByDesc("create_time")
                .last("LIMIT " + Math.min(limit, 100));
        if (status != null && !status.trim().isEmpty()) {
            qw.eq("status", status.trim());
        }
        return creditApplicationMapper.selectList(qw).stream().map(this::toVO).collect(Collectors.toList());
    }

    public List<CreditApplyVO> listPendingManualReview(int limit) {
        return creditApplicationMapper.selectList(
                new QueryWrapper<CreditApplication>()
                        .eq("final_decision", ApplicationStatus.MANUAL_REVIEW)
                        .orderByDesc("create_time")
                        .last("LIMIT " + Math.min(limit, 50)))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public ManualReviewTicket createManualReviewTicket(CreditApplication app, String title,
                                                       String description, String reason, String priority) {
        ManualReviewTicket ticket = new ManualReviewTicket();
        ticket.setApplicationId(app.getId());
        ticket.setUserId(app.getUserId());
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setReason(reason);
        ticket.setPriority(priority != null ? priority : "MEDIUM");
        ticket.setStatus(ManualReviewTicket.OPEN);
        manualReviewTicketMapper.insert(ticket);

        app.setTicketId(ticket.getId());
        creditApplicationMapper.updateById(app);
        return ticket;
    }

    public CreditAdvisory createAdvisory(CreditApplication app, Long ticketId,
                                         java.math.BigDecimal suggestAmount,
                                         java.math.BigDecimal suggestRate,
                                         Integer suggestTerm,
                                         String agentSuggestion,
                                         String agentConsensusJson) {
        CreditAdvisory advisory = new CreditAdvisory();
        advisory.setApplicationId(app.getId());
        advisory.setTicketId(ticketId);
        advisory.setUserId(app.getUserId());
        advisory.setSuggestAmount(suggestAmount);
        advisory.setSuggestRate(suggestRate);
        advisory.setSuggestTerm(suggestTerm);
        advisory.setAgentSuggestion(agentSuggestion);
        advisory.setAgentConsensusJson(agentConsensusJson);
        advisory.setStatus(CreditAdvisory.PENDING);
        creditAdvisoryMapper.insert(advisory);
        return advisory;
    }

    public void updateApplication(CreditApplication app) {
        creditApplicationMapper.updateById(app);
    }

    public CreditAdvisory getAdvisoryByApplication(Long applicationId) {
        return creditAdvisoryMapper.selectOne(
                new QueryWrapper<CreditAdvisory>()
                        .eq("application_id", applicationId)
                        .orderByDesc("id")
                        .last("LIMIT 1"));
    }

    private CreditApplyVO toVO(CreditApplication app) {
        CreditApplyVO vo = new CreditApplyVO();
        vo.setId(app.getId());
        vo.setApplicationNo(app.getApplicationNo());
        vo.setUserId(app.getUserId());
        vo.setProductId(app.getProductId());
        vo.setApplyAmount(app.getApplyAmount());
        vo.setApplyTerm(app.getApplyTerm());
        vo.setPurpose(app.getPurpose());
        vo.setContent(app.getContent());
        vo.setVerifiedDocuments(app.getVerifiedDocuments());
        vo.setStatus(app.getStatus());
        vo.setFinalDecision(app.getFinalDecision());
        vo.setAgentSuggestion(app.getAgentSuggestion());
        vo.setSuggestedAmount(app.getSuggestedAmount());
        vo.setSuggestedRate(app.getSuggestedRate());
        vo.setApprovedAmount(app.getApprovedAmount());
        vo.setApprovedRate(app.getApprovedRate());
        vo.setRiskLevel(app.getRiskLevel());
        vo.setRiskScore(app.getRiskScore());
        vo.setFraudScore(app.getFraudScore());
        vo.setBureauUnavailable(app.getBureauUnavailable());
        vo.setConflictDetected(app.getConflictDetected());
        vo.setPlatformDecisionJson(app.getPlatformDecisionJson());
        if (app.getHitRulesJson() != null) {
            vo.setHitRules(JSONUtil.toList(app.getHitRulesJson(), String.class));
        }
        if (app.getDecisionReasonJson() != null) {
            vo.setDecisionReason(JSONUtil.toList(app.getDecisionReasonJson(), String.class));
        }
        vo.setAiSummary(app.getAiSummary());
        vo.setWorkflowId(app.getWorkflowId());
        vo.setCreateTime(app.getCreateTime());
        if (app.getProductId() != null) {
            CreditProduct product = creditProductMapper.selectById(app.getProductId());
            if (product != null) {
                vo.setProductName(product.getName());
            }
        }
        return vo;
    }

    public String buildAiSuggestionJson(com.credit.credit.dto.CreditAnalysisDTO analysis) {
        return JSONUtil.toJsonStr(analysis);
    }
}
