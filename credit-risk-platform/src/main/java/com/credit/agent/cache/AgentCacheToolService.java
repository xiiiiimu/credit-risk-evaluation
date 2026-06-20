package com.credit.agent.cache;

import com.credit.common.Result;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class AgentCacheToolService {

    @Resource
    private AgentResultCacheService agentResultCacheService;

    public Result getLlmCache(Map<String, Object> args) {
        int promptVersion = intVal(args, "promptVersion", 0);
        String inputHash = stringVal(args, "inputHash");
        String content = agentResultCacheService.getLlmResult(promptVersion, inputHash);
        Map<String, Object> data = new HashMap<>(3);
        data.put("hit", content != null);
        data.put("content", content);
        return Result.ok(data);
    }

    public Result setLlmCache(Map<String, Object> args) {
        agentResultCacheService.setLlmResult(
                intVal(args, "promptVersion", 0),
                stringVal(args, "inputHash"),
                stringVal(args, "content"),
                longVal(args, "ttlHours"));
        return Result.ok();
    }

    public Result getOcrCache(Map<String, Object> args) {
        String fileMd5 = stringVal(args, "fileMd5");
        String content = agentResultCacheService.getOcrResult(fileMd5);
        Map<String, Object> data = new HashMap<>(3);
        data.put("hit", content != null);
        data.put("content", content);
        return Result.ok(data);
    }

    public Result setOcrCache(Map<String, Object> args) {
        agentResultCacheService.setOcrResult(
                stringVal(args, "fileMd5"),
                stringVal(args, "content"),
                longVal(args, "ttlHours"));
        return Result.ok();
    }

    private String stringVal(Map<String, Object> args, String key) {
        if (args == null || args.get(key) == null) {
            return null;
        }
        return args.get(key).toString();
    }

    private int intVal(Map<String, Object> args, String key, int defaultValue) {
        if (args == null || args.get(key) == null) {
            return defaultValue;
        }
        return Integer.parseInt(args.get(key).toString());
    }

    private Long longVal(Map<String, Object> args, String key) {
        if (args == null || args.get(key) == null) {
            return null;
        }
        return Long.valueOf(args.get(key).toString());
    }
}
