package com.credit.credit.controller.admin;

import com.credit.credit.entity.CreditApplication;
import com.credit.credit.service.CreditRecordService;
import com.credit.credit.trace.CreditWorkflowTraceService;
import com.credit.common.Result;
import com.credit.common.context.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/credit/apply")
public class AdminCreditTraceController {

    @Resource
    private CreditRecordService creditRecordService;
    @Resource
    private CreditWorkflowTraceService creditWorkflowTraceService;

    /** Agent 工作流节点耗时追踪，用于生产级可观测性演示 */
    @GetMapping("/{applicationId:\\d+}/trace")
    public Result trace(@PathVariable("applicationId") Long applicationId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        CreditApplication app = creditRecordService.getEntity(applicationId);
        if (app == null) {
            return Result.fail("申请不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("applicationId", applicationId);
        data.put("workflowId", app.getWorkflowId());
        data.put("nodes", creditWorkflowTraceService.listByApplicationId(applicationId));
        if (app.getWorkflowId() != null) {
            data.put("workflowNodes", creditWorkflowTraceService.listByWorkflowId(app.getWorkflowId()));
        }
        return Result.ok(data);
    }
}
