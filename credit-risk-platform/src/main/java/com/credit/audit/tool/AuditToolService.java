package com.credit.audit.tool;

import com.credit.audit.entity.AuditLog;
import com.credit.audit.service.AuditLogService;
import com.credit.common.Result;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuditToolService {

    @Resource
    private AuditLogService auditLogService;

    public Result saveAuditLog(Map<String, Object> args) {
        AuditLog row = auditLogService.save(args);
        Map<String, Object> data = new HashMap<>(2);
        data.put("id", row.getId());
        return Result.ok(data);
    }
}
