package com.credit.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class ToolRegistry {

    private static final Set<String> CREDIT_TOOLS;

    static {
        Set<String> names = new LinkedHashSet<>();
        names.add("get_user_memory");
        names.add("append_workflow_trace");
        names.add("get_credit_application");
        names.add("get_credit_product");
        names.add("verify_application_documents");
        names.add("save_credit_bureau_query_log");
        names.add("get_user_credit_history");
        names.add("evaluate_fraud_signals");
        names.add("save_credit_workflow_trace");
        names.add("get_credit_bureau_summary");
        names.add("resolve_workflow_idempotent");
        names.add("acquire_workflow_execution");
        names.add("release_workflow_lock");
        names.add("start_workflow");
        names.add("begin_workflow_node");
        names.add("complete_workflow_node");
        names.add("save_workflow_checkpoint");
        names.add("load_workflow_checkpoint");
        names.add("finish_workflow");
        names.add("get_workflow_execution");
        names.add("get_prompt_config");
        names.add("get_rule_config");
        names.add("save_audit_log");
        names.add("get_llm_cache");
        names.add("set_llm_cache");
        names.add("get_ocr_cache");
        names.add("set_ocr_cache");
        names.add("recognize_document");
        names.add("fuse_application_input");
        names.add("get_product_rule_config");
        names.add("get_product_material_requirements");
        CREDIT_TOOLS = Collections.unmodifiableSet(names);
    }

    public Set<String> listTools() {
        return CREDIT_TOOLS;
    }

    public boolean supports(String tool) {
        return CREDIT_TOOLS.contains(tool);
    }
}
