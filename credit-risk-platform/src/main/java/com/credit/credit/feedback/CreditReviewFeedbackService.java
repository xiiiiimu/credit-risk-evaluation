package com.credit.credit.feedback;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.credit.credit.entity.CreditApplication;
import com.credit.credit.entity.CreditReviewFeedback;
import com.credit.credit.mapper.CreditReviewFeedbackMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 人工复核反馈闭环：沉淀 Agent 建议 vs Java 决策 vs 人工决策，供后续分析。
 */
@Service
public class CreditReviewFeedbackService {

    @Resource
    private CreditReviewFeedbackMapper creditReviewFeedbackMapper;

    public CreditReviewFeedback record(CreditApplication app, String humanDecision,
                                       String reviewReason, Long reviewedBy) {
        CreditReviewFeedback fb = new CreditReviewFeedback();
        fb.setApplicationId(app.getId());
        fb.setUserId(app.getUserId());
        fb.setAgentSuggestion(app.getAgentSuggestion());
        fb.setJavaDecision(app.getFinalDecision());
        fb.setHumanDecision(humanDecision);
        fb.setRiskScore(app.getRiskScore());
        fb.setHitRulesJson(app.getHitRulesJson());
        fb.setReviewReason(reviewReason);
        fb.setReviewedBy(reviewedBy);
        fb.setReviewedAt(LocalDateTime.now());
        creditReviewFeedbackMapper.insert(fb);
        return fb;
    }

    public List<CreditReviewFeedback> listRecent(int limit) {
        return creditReviewFeedbackMapper.selectList(
                new QueryWrapper<CreditReviewFeedback>()
                        .orderByDesc("reviewed_at")
                        .last("LIMIT " + Math.min(limit, 100)));
    }
}
