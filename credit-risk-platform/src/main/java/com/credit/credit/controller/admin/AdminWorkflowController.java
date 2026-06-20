package com.credit.credit.controller.admin;

import com.credit.common.Result;
import com.credit.common.context.UserHolder;
import com.credit.workflow.service.WorkflowPersistenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/credit/workflow")
public class AdminWorkflowController {

    @Resource
    private WorkflowPersistenceService workflowPersistenceService;

    @GetMapping("/{workflowId}")
    public Result execution(@PathVariable("workflowId") String workflowId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        Map<String, Object> chain = workflowPersistenceService.getExecutionChain(workflowId);
        if (chain == null) {
            return Result.fail("workflow 不存在");
        }
        return Result.ok(chain);
    }
}
