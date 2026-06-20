package com.credit.credit.controller.admin;

import com.credit.common.Result;
import com.credit.common.context.UserHolder;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/metrics")
public class AdminMetricsController {

    @Resource
    private MeterRegistry meterRegistry;

    @GetMapping("/summary")
    public Result summary() {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agentNodeInvoke", counterSummary("agent.node.invoke"));
        data.put("agentNodeRetry", counterSummary("agent.node.retry"));
        data.put("agentLlmInvoke", counterSummary("agent.llm.invoke"));
        data.put("agentLlmTokens", counterSummary("agent.llm.tokens"));
        data.put("workflowManualReview", counterSummary("workflow.manual_review"));
        data.put("agentLlmCacheHit", counterSummary("agent.llm.cache_hit"));
        data.put("agentRemoteCall", counterSummary("agent.remote.call"));
        return Result.ok(data);
    }

    private Map<String, Object> counterSummary(String name) {
        Map<String, Object> summary = new HashMap<>();
        double total = 0;
        double success = 0;
        for (Meter meter : Search.in(meterRegistry).name(name).meters()) {
            if (meter instanceof io.micrometer.core.instrument.Counter) {
                io.micrometer.core.instrument.Counter counter = (io.micrometer.core.instrument.Counter) meter;
                double count = counter.count();
                total += count;
                String successTag = counter.getId().getTag("success");
                if ("true".equalsIgnoreCase(successTag)) {
                    success += count;
                }
            }
        }
        summary.put("total", total);
        summary.put("success", success);
        summary.put("failure", Math.max(0, total - success));
        if (total > 0) {
            summary.put("successRate", success / total);
        } else {
            summary.put("successRate", null);
        }
        return summary;
    }
}
