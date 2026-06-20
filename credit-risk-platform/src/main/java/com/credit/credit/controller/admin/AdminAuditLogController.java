package com.credit.credit.controller.admin;

import com.credit.audit.entity.AuditLog;
import com.credit.audit.service.AuditLogService;
import com.credit.common.Result;
import com.credit.common.context.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
public class AdminAuditLogController {

    @Resource
    private AuditLogService auditLogService;

    @GetMapping("/workflow/{workflowId}")
    public Result listByWorkflow(@PathVariable("workflowId") String workflowId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        List<AuditLog> logs = auditLogService.listByWorkflowId(workflowId);
        return Result.ok(logs);
    }
}
