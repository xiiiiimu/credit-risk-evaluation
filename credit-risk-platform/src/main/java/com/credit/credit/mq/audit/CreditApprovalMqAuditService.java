package com.credit.credit.mq.audit;

import com.credit.audit.metrics.WorkflowMetrics;
import com.credit.audit.service.AuditLogService;
import com.credit.credit.mq.message.CreditApprovalTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class CreditApprovalMqAuditService {

    public static final String EVENT_MQ_SEND_SUCCESS = "MQ_SEND_SUCCESS";
    public static final String EVENT_MQ_SEND_FAILED = "MQ_SEND_FAILED";
    public static final String EVENT_MQ_CONSUME_START = "MQ_CONSUME_START";
    public static final String EVENT_MQ_CONSUME_SUCCESS = "MQ_CONSUME_SUCCESS";
    public static final String EVENT_MQ_CONSUME_FAILED = "MQ_CONSUME_FAILED";
    public static final String EVENT_MQ_RETRY = "MQ_RETRY";
    public static final String EVENT_MQ_DEAD_LETTER = "MQ_DEAD_LETTER";
    public static final String EVENT_MQ_CONSUME_SKIP = "MQ_CONSUME_SKIP";

    public static final String CALL_MQ = "MQ";

    @Resource
    private AuditLogService auditLogService;
    @Resource
    private WorkflowMetrics workflowMetrics;

    public void record(String event, CreditApprovalTaskMessage message, boolean success,
                       long costMs, String errorMsg, Map<String, Object> extra) {
        long totalStart = System.nanoTime();
        Long taskId = message != null ? message.getTaskId() : null;
        String workflowId = message != null ? message.getWorkflowId() : null;
        try {
            Map<String, Object> request = new HashMap<>();
            if (message != null) {
                request.put("taskId", message.getTaskId());
                request.put("workflowId", message.getWorkflowId());
                request.put("applicationId", message.getApplicationId());
                request.put("userId", message.getUserId());
                request.put("productId", message.getProductId());
                request.put("traceId", message.getTraceId());
            }
            if (extra != null) {
                request.putAll(extra);
            }

            Map<String, Object> args = new HashMap<>();
            args.put("workflowId", message != null ? message.getWorkflowId() : null);
            args.put("traceId", message != null ? message.getTraceId() : null);
            args.put("nodeName", event);
            args.put("callType", CALL_MQ);
            args.put("request", request);
            args.put("success", success);
            args.put("costTimeMs", (int) costMs);
            args.put("errorMsg", errorMsg);

            long insertStart = System.nanoTime();
            auditLogService.save(args);
            log.info("[PERF][submit] taskId={} workflowId={} stage=auditLogMapper.insert cost={}ms",
                    taskId, workflowId, elapsedMs(insertStart));

            long metricsStart = System.nanoTime();
            workflowMetrics.recordMq(event, success, costMs);
            log.info("[PERF][submit] taskId={} workflowId={} stage=workflowMetrics.recordMq cost={}ms",
                    taskId, workflowId, elapsedMs(metricsStart));
        } finally {
            log.info("[PERF][submit] taskId={} workflowId={} stage=mqAuditService.record.total cost={}ms",
                    taskId, workflowId, elapsedMs(totalStart));
        }
    }

    public void recordRetry(CreditApprovalTaskMessage message, int reconsumeTimes, String errorMsg) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("reconsumeTimes", reconsumeTimes);
        record(EVENT_MQ_RETRY, message, false, 0, errorMsg, extra);
    }

    private static long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }
}
