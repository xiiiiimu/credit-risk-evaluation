package com.credit.agent.tool;

import com.credit.agent.metrics.AgentToolMetrics;
import com.credit.agent.memory.MemoryToolService;
import com.credit.agent.service.AgentTaskLogService;
import com.credit.agent.workflow.AgentWorkflowTraceService;
import com.credit.common.Result;
import com.credit.credit.tool.CreditToolService;
import com.credit.workflow.tool.WorkflowToolService;
import com.credit.platformconfig.tool.PlatformConfigToolService;
import com.credit.audit.tool.AuditToolService;
import com.credit.agent.cache.AgentCacheToolService;
import com.credit.input.tool.InputFusionToolService;
import com.credit.ocr.tool.OcrToolService;
import com.credit.product.tool.ProductConfigToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

@Slf4j
@Service
public class ToolInvokeService {

    @Resource
    private ToolRegistry toolRegistry;
    @Resource
    private CreditToolService creditToolService;
    @Resource
    private MemoryToolService memoryToolService;
    @Resource
    private AgentWorkflowTraceService agentWorkflowTraceService;
    @Resource
    private AgentTaskLogService agentTaskLogService;
    @Resource
    private AgentToolMetrics agentToolMetrics;
    @Resource
    private WorkflowToolService workflowToolService;
    @Resource
    private PlatformConfigToolService platformConfigToolService;
    @Resource
    private AuditToolService auditToolService;
    @Resource
    private AgentCacheToolService agentCacheToolService;
    @Resource
    private OcrToolService ocrToolService;
    @Resource
    private InputFusionToolService inputFusionToolService;
    @Resource
    private ProductConfigToolService productConfigToolService;

    public Result invoke(String tool, Map<String, Object> args) {
        if (!toolRegistry.supports(tool)) {
            return Result.fail("未知 Tool: " + tool);
        }
        long start = System.currentTimeMillis();
        Result result;
        try {
            result = doInvoke(tool, args);
        } catch (Exception e) {
            log.error("Tool invoke error: {}", tool, e);
            result = Result.fail(e.getMessage());
        }
        long cost = System.currentTimeMillis() - start;
        boolean ok = Boolean.TRUE.equals(result.getSuccess());
        agentTaskLogService.log(tool, args, result, ok, cost);
        agentToolMetrics.recordTool(tool, ok, cost);
        return result;
    }

    private Result doInvoke(String tool, Map<String, Object> args) {
        switch (tool) {
            case "get_user_memory":
                return memoryToolService.getUserMemory(ToolArgs.getLong(args, "userId"));
            case "append_workflow_trace":
                agentWorkflowTraceService.appendFromMap(args);
                return Result.ok();
            case "get_credit_application":
                return creditToolService.getCreditApplication(ToolArgs.getLong(args, "applicationId"));
            case "get_credit_product":
                return productConfigToolService.getCreditProductContext(args);
            case "verify_application_documents":
                return creditToolService.verifyApplicationDocuments(
                        ToolArgs.getLong(args, "userId"),
                        ToolArgs.getLong(args, "productId"),
                        ToolArgs.getString(args, "content"));
            case "save_credit_bureau_query_log":
                return creditToolService.saveCreditBureauQueryLog(
                        ToolArgs.getLong(args, "userId"),
                        ToolArgs.getLong(args, "applicationId"),
                        ToolArgs.getString(args, "requestHash"),
                        ToolArgs.getString(args, "resultSummaryJson"),
                        ToolArgs.getString(args, "mcpTraceId"),
                        args != null && args.get("mcpLatencyMs") != null
                                ? ToolArgs.getInt(args, "mcpLatencyMs", 0) : null,
                        ToolArgs.getString(args, "mcpError"));
            case "get_user_credit_history":
                return creditToolService.getUserCreditHistory(ToolArgs.getLong(args, "userId"));
            case "evaluate_fraud_signals":
                return creditToolService.evaluateFraudSignals(
                        ToolArgs.getLong(args, "userId"),
                        ToolArgs.getLong(args, "applicationId"),
                        ToolArgs.getString(args, "contentHint"));
            case "save_credit_workflow_trace":
                creditToolService.saveCreditWorkflowTrace(args);
                return Result.ok();
            case "get_credit_bureau_summary":
                return creditToolService.getCreditBureauSummary(ToolArgs.getLong(args, "userId"));
            case "resolve_workflow_idempotent":
                return workflowToolService.resolveWorkflowIdempotent(args);
            case "acquire_workflow_execution":
                return workflowToolService.acquireWorkflowExecution(args);
            case "release_workflow_lock":
                return workflowToolService.releaseWorkflowLock(args);
            case "start_workflow":
                return workflowToolService.startWorkflow(args);
            case "begin_workflow_node":
                return workflowToolService.beginWorkflowNode(args);
            case "complete_workflow_node":
                return workflowToolService.completeWorkflowNode(args);
            case "save_workflow_checkpoint":
                return workflowToolService.saveWorkflowCheckpoint(args);
            case "load_workflow_checkpoint":
                return workflowToolService.loadWorkflowCheckpoint(args);
            case "finish_workflow":
                return workflowToolService.finishWorkflow(args);
            case "get_workflow_execution":
                return workflowToolService.getWorkflowExecution(args);
            case "get_prompt_config":
                return platformConfigToolService.getPromptConfig(args);
            case "get_rule_config":
                return platformConfigToolService.getRuleConfig(args);
            case "save_audit_log":
                return auditToolService.saveAuditLog(args);
            case "get_llm_cache":
                return agentCacheToolService.getLlmCache(args);
            case "set_llm_cache":
                return agentCacheToolService.setLlmCache(args);
            case "get_ocr_cache":
                return agentCacheToolService.getOcrCache(args);
            case "set_ocr_cache":
                return agentCacheToolService.setOcrCache(args);
            case "recognize_document":
                return ocrToolService.recognizeDocument(args);
            case "fuse_application_input":
                return inputFusionToolService.fuseApplicationInput(args);
            case "get_product_rule_config":
                return productConfigToolService.getProductRuleConfig(args);
            case "get_product_material_requirements":
                return productConfigToolService.getProductMaterialRequirements(args);
            default:
                return Result.fail("Tool 未实现: " + tool);
        }
    }
}
