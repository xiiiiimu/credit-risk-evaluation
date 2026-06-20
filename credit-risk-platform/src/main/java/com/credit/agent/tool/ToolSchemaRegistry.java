package com.credit.agent.tool;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolSchemaRegistry {

    @javax.annotation.Resource
    private ToolRegistry toolRegistry;

    public List<Map<String, Object>> listSchemas() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String name : toolRegistry.listTools()) {
            list.add(schemaOf(name));
        }
        return list;
    }

    private Map<String, Object> schemaOf(String name) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", name);
        schema.put("parameters", parametersOf(name));
        return schema;
    }

    private Map<String, Object> parametersOf(String name) {
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        switch (name) {
            case "get_user_memory":
            case "get_user_credit_history":
            case "get_credit_bureau_summary":
                props.put("userId", prop("integer", "用户ID"));
                required.add("userId");
                break;
            case "get_credit_application":
                props.put("applicationId", prop("integer", "申请ID"));
                required.add("applicationId");
                break;
            case "get_credit_product":
                props.put("productId", prop("integer", "产品ID"));
                required.add("productId");
                break;
            case "verify_application_documents":
                props.put("userId", prop("integer", "用户ID"));
                props.put("productId", prop("integer", "产品ID"));
                props.put("content", prop("string", "申请说明"));
                required.add("userId");
                required.add("productId");
                required.add("content");
                break;
            case "evaluate_fraud_signals":
                props.put("userId", prop("integer", "用户ID"));
                props.put("applicationId", prop("integer", "申请ID"));
                props.put("contentHint", prop("string", "内容关键字"));
                required.add("userId");
                break;
            case "save_credit_workflow_trace":
            case "append_workflow_trace":
                props.put("args", prop("object", "trace payload"));
                break;
            default:
                props.put("args", prop("object", "见 ToolInvokeService"));
                break;
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", props);
        if (!required.isEmpty()) {
            parameters.put("required", required);
        }
        return parameters;
    }

    private Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }
}
