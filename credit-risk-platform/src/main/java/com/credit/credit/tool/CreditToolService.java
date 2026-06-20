package com.credit.credit.tool;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.credit.credit.dto.CreditApplyVO;
import com.credit.credit.entity.CreditApplication;
import com.credit.credit.entity.CreditBureauQuery;
import com.credit.credit.entity.CreditProduct;
import com.credit.credit.enums.ApplicationStatus;
import com.credit.credit.enums.FinalDecision;
import com.credit.credit.fraud.FraudRuleEngine;
import com.credit.credit.fraud.FraudSignalClient;
import com.credit.credit.fraud.dto.FraudRuleResult;
import com.credit.credit.fraud.dto.FraudSignalDTO;
import com.credit.credit.mapper.CreditApplicationMapper;
import com.credit.credit.mapper.CreditBureauQueryMapper;
import com.credit.credit.mapper.CreditProductMapper;
import com.credit.credit.service.CreditRecordService;
import com.credit.credit.trace.CreditWorkflowTraceService;
import com.credit.common.Result;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CreditToolService {

    @Resource
    private CreditApplicationMapper creditApplicationMapper;
    @Resource
    private CreditProductMapper creditProductMapper;
    @Resource
    private CreditBureauQueryMapper creditBureauQueryMapper;
    @Resource
    private CreditRecordService creditRecordService;
    @Resource
    private FraudSignalClient fraudSignalClient;
    @Resource
    private FraudRuleEngine fraudRuleEngine;
    @Resource
    private CreditWorkflowTraceService creditWorkflowTraceService;

    public Result getCreditApplication(Long applicationId) {
        CreditApplyVO vo = creditRecordService.getDetail(applicationId);
        if (vo == null) {
            return Result.fail("申请不存在");
        }
        return Result.ok(vo);
    }

    public Result getCreditProduct(Long productId) {
        CreditProduct product = creditProductMapper.selectById(productId);
        if (product == null) {
            return Result.fail("产品不存在");
        }
        return Result.ok(product);
    }

    public Result verifyApplicationDocuments(Long userId, Long productId, String content) {
        Map<String, Object> result = new HashMap<>();
        boolean complete = content != null && content.trim().length() >= 20;
        double docScore = complete ? 0.9 : 0.4;
        result.put("verifiedDocuments", complete);
        result.put("documentStatus", complete ? "VERIFIED" : "INCOMPLETE");
        result.put("documentScore", docScore);
        result.put("missingDocs", complete ? java.util.Collections.emptyList()
                : java.util.Arrays.asList("INCOME_PROOF", "ID_CARD"));
        return Result.ok(result);
    }

    /** 用户历史申请画像，供 load_memory 注入 Agent */
    public Result getUserCreditHistory(Long userId) {
        List<CreditApplication> all = creditApplicationMapper.selectList(
                new QueryWrapper<CreditApplication>().eq("user_id", userId));
        int pastApply = all.size();
        int pastReject = (int) all.stream()
                .filter(a -> ApplicationStatus.REJECTED.equals(a.getStatus())
                        || FinalDecision.REJECTED.equals(a.getFinalDecision())).count();
        int pastManual = (int) all.stream()
                .filter(a -> ApplicationStatus.MANUAL_REVIEW.equals(a.getStatus())).count();
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        int recent7d = (int) all.stream()
                .filter(a -> a.getCreateTime() != null && a.getCreateTime().isAfter(sevenDaysAgo)).count();
        String lastDecision = all.stream()
                .filter(a -> a.getFinalDecision() != null)
                .sorted((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()))
                .map(CreditApplication::getFinalDecision)
                .findFirst().orElse("NONE");

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("pastApplyCount", pastApply);
        data.put("pastRejectCount", pastReject);
        data.put("pastManualReviewCount", pastManual);
        data.put("recentApplyCount7d", recent7d);
        data.put("lastDecision", lastDecision);
        data.put("highFrequencyApply", recent7d > 5);
        return Result.ok(data);
    }

    /** 外部反欺诈信号 + 规则引擎评分 */
    public Result evaluateFraudSignals(Long userId, Long applicationId, String contentHint) {
        FraudSignalDTO signals = fraudSignalClient.fetchSignals(userId, applicationId, contentHint);
        Map<String, Object> history = loadHistoryMap(userId);
        if (history != null && Boolean.TRUE.equals(history.get("highFrequencyApply"))) {
            signals = FraudSignalDTO.builder()
                    .userId(signals.getUserId())
                    .applicationId(signals.getApplicationId())
                    .ipAbnormal(signals.isIpAbnormal())
                    .deviceAbnormal(signals.isDeviceAbnormal())
                    .proxyIp(signals.isProxyIp())
                    .multiAccountDevice(signals.isMultiAccountDevice())
                    .geoVelocityAbnormal(signals.isGeoVelocityAbnormal())
                    .highFrequencyApply(true)
                    .blacklistHit(signals.isBlacklistHit())
                    .riskLevel(signals.getRiskLevel())
                    .confidence(signals.getConfidence())
                    .build();
        }
        FraudRuleResult rule = fraudRuleEngine.evaluate(signals);
        Map<String, Object> payload = new HashMap<>();
        payload.put("signals", signals);
        payload.put("fraudScore", rule.getFraudScore());
        payload.put("fraudLevel", rule.getFraudLevel());
        payload.put("hitRules", rule.getHitRules());
        return Result.ok(payload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadHistoryMap(Long userId) {
        Result historyResult = getUserCreditHistory(userId);
        if (historyResult == null || historyResult.getData() == null) {
            return null;
        }
        Object data = historyResult.getData();
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        return null;
    }

    /** 脱敏征信摘要（供缓存层，不含完整原文） */
    public Result getCreditBureauSummary(Long userId) {
        CreditBureauQuery latest = creditBureauQueryMapper.selectOne(
                new QueryWrapper<CreditBureauQuery>()
                        .eq("user_id", userId)
                        .orderByDesc("id")
                        .last("LIMIT 1"));
        if (latest == null || latest.getResultSummaryJson() == null) {
            return Result.ok(null);
        }
        try {
            Map<String, Object> raw = JSONUtil.toBean(latest.getResultSummaryJson(), Map.class);
            Map<String, Object> summary = new HashMap<>();
            summary.put("creditScore", raw.get("creditScore"));
            summary.put("overdueCount24m", raw.get("overdueCount24m"));
            summary.put("blacklistHit", raw.get("blacklistHit"));
            summary.put("queryTime", latest.getCreateTime());
            return Result.ok(summary);
        } catch (Exception e) {
            return Result.ok(null);
        }
    }

    public Result saveCreditBureauQueryLog(Long userId, Long applicationId, String requestHash,
                                           String resultSummaryJson, String mcpTraceId,
                                           Integer mcpLatencyMs, String mcpError) {
        if (requestHash == null || requestHash.trim().isEmpty()) {
            return Result.fail("requestHash 不能为空");
        }
        CreditBureauQuery existing = creditBureauQueryMapper.selectOne(
                new QueryWrapper<CreditBureauQuery>().eq("request_hash", requestHash.trim()).last("LIMIT 1"));
        if (existing != null) {
            return Result.ok(existing);
        }
        CreditBureauQuery query = new CreditBureauQuery();
        query.setUserId(userId);
        query.setApplicationId(applicationId);
        query.setQueryType("CREDIT_REPORT");
        query.setRequestHash(requestHash.trim());
        query.setResultSummaryJson(resultSummaryJson);
        query.setMcpTraceId(mcpTraceId);
        if (mcpError != null && resultSummaryJson != null) {
            Map<String, Object> wrap = new HashMap<>();
            wrap.put("summary", resultSummaryJson);
            wrap.put("mcpLatencyMs", mcpLatencyMs);
            wrap.put("mcpError", mcpError);
            query.setResultSummaryJson(JSONUtil.toJsonStr(wrap));
        }
        try {
            creditBureauQueryMapper.insert(query);
        } catch (DuplicateKeyException e) {
            CreditBureauQuery dup = creditBureauQueryMapper.selectOne(
                    new QueryWrapper<CreditBureauQuery>().eq("request_hash", requestHash.trim()).last("LIMIT 1"));
            return Result.ok(dup);
        }
        return Result.ok(query);
    }

    public Result saveCreditWorkflowTrace(Map<String, Object> args) {
        creditWorkflowTraceService.saveFromMap(args);
        return Result.ok();
    }

    public static String buildRequestHash(Long userId, Long applicationId, String queryReason) {
        String raw = userId + ":" + (applicationId != null ? applicationId : "") + ":" + queryReason;
        return DigestUtil.sha256Hex(raw);
    }
}
