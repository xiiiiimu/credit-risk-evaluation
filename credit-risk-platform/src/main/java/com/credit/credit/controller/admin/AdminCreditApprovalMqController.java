package com.credit.credit.controller.admin;

import com.credit.common.Result;
import com.credit.common.context.UserHolder;
import com.credit.credit.mq.service.CreditApprovalTaskRedeliveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/credit/mq")
@ConditionalOnProperty(name = "credit.mq.enabled", havingValue = "true", matchIfMissing = true)
public class AdminCreditApprovalMqController {

    @Resource
    private CreditApprovalTaskRedeliveryService redeliveryService;

    /**
     * 人工补偿：对 MQ_SEND_FAILED / FAILED 等任务重新投递审批消息。
     */
    @PostMapping("/redelivery/{taskId:\\d+}")
    public Result redelivery(@PathVariable("taskId") Long taskId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        boolean ok = redeliveryService.redeliver(taskId);
        if (!ok) {
            return Result.fail("补偿投递失败或任务状态不可重投");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("status", "MQ_SENT");
        return Result.ok(data);
    }
}
