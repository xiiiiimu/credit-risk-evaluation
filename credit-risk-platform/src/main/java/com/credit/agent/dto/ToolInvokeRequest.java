package com.credit.agent.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ToolInvokeRequest {
    private String tool;
    private Map<String, Object> args;
}
