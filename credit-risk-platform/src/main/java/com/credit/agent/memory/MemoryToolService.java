package com.credit.agent.memory;

import com.credit.agent.entity.UserMemory;
import com.credit.common.Result;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Service
public class MemoryToolService {

    @Resource
    private UserMemoryAggregator userMemoryAggregator;

    public Result getUserMemory(Long userId) {
        UserMemory m = userMemoryAggregator.refresh(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", m.getUserId());
        data.put("complaintCount", m.getComplaintCount());
        data.put("compensationCount", m.getCompensationCount());
        data.put("compensationAmountTotal", m.getCompensationAmountTotal());
        data.put("riskLevel", m.getRiskLevel());
        data.put("complaintCount7d", m.getComplaintCount7d());
        data.put("distinctShopCount7d", m.getDistinctShopCount7d());
        data.put("compensationCount30d", m.getCompensationCount30d());
        data.put("compensationRejected30d", m.getCompensationRejected30d());
        data.put("cleanDays", m.getCleanDays());
        data.put("platformContactFlag", m.getPlatformContactFlag());
        data.put("preferredCategory", m.getPreferredCategory());
        data.put("lastComplaintTime", m.getLastComplaintTime());
        data.put("lastIncidentTime", m.getLastIncidentTime());
        return Result.ok(data);
    }
}
