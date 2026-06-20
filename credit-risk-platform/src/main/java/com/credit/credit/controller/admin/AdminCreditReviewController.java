package com.credit.credit.controller.admin;

import com.credit.credit.entity.CreditApplication;
import com.credit.credit.feedback.CreditReviewFeedbackService;
import com.credit.credit.service.CreditRecordService;
import com.credit.credit.workflow.ManualReviewWorkflowService;
import com.credit.common.Result;
import com.credit.common.context.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/credit/review")
public class AdminCreditReviewController {

    @Resource
    private CreditRecordService creditRecordService;
    @Resource
    private ManualReviewWorkflowService manualReviewWorkflowService;
    @Resource
    private CreditReviewFeedbackService creditReviewFeedbackService;

    @GetMapping("/list")
    public Result list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(creditRecordService.listAll(status, limit));
    }

    @GetMapping("/pending")
    public Result pending(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(creditRecordService.listPendingManualReview(limit));
    }

    @GetMapping("/{id:\\d+}")
    public Result detail(@PathVariable("id") Long id) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(creditRecordService.getDetail(id));
    }

    @PutMapping("/{id:\\d+}/approve")
    public Result approve(
            @PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        BigDecimal amount = body != null && body.get("approvedAmount") != null
                ? new BigDecimal(body.get("approvedAmount").toString()) : null;
        BigDecimal rate = body != null && body.get("approvedRate") != null
                ? new BigDecimal(body.get("approvedRate").toString()) : null;
        CreditApplication app = creditRecordService.getEntity(id);
        if (app == null) {
            return Result.fail("申请不存在");
        }
        if (amount == null) {
            amount = app.getSuggestedAmount() != null ? app.getSuggestedAmount() : app.getApplyAmount();
        }
        if (rate == null) {
            rate = app.getSuggestedRate();
        }
        String reviewReason = body != null && body.get("reviewReason") != null
                ? body.get("reviewReason").toString() : "人工审批通过";
        CreditApplication result = manualReviewWorkflowService.approve(
                id, UserHolder.getUser().getId(), amount, rate, reviewReason);
        if (result == null) {
            return Result.fail("无法审批，请确认申请处于 MANUAL_REVIEW 状态");
        }
        return Result.ok(creditRecordService.getDetail(id));
    }

    @PutMapping("/{id:\\d+}/reject")
    public Result reject(
            @PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, String> body) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        String reason = body != null ? body.get("reason") : "不符合授信规则";
        CreditApplication result = manualReviewWorkflowService.reject(id, UserHolder.getUser().getId(), reason);
        if (result == null) {
            return Result.fail("无法拒绝，请确认申请处于 MANUAL_REVIEW 状态");
        }
        return Result.ok(creditRecordService.getDetail(id));
    }

    @GetMapping("/feedback")
    public Result feedback(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        return Result.ok(creditReviewFeedbackService.listRecent(limit));
    }
}
