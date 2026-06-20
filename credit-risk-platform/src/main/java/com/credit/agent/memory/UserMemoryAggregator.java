package com.credit.agent.memory;

import com.credit.agent.entity.UserMemory;
import com.credit.agent.mapper.UserMemoryMapper;
import com.credit.agent.risk.enums.UserRiskLevel;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class UserMemoryAggregator {

    @Resource
    private UserMemoryMapper userMemoryMapper;

    public UserMemory refresh(Long userId) {
        return getOrRefresh(userId);
    }

    public UserMemory getOrRefresh(Long userId) {
        UserMemory memory = userMemoryMapper.selectById(userId);
        if (memory != null) {
            return memory;
        }
        UserMemory created = new UserMemory();
        created.setUserId(userId);
        created.setRiskLevel(UserRiskLevel.LOW);
        created.setComplaintCount(0);
        created.setCompensationCount(0);
        created.setCompensationAmountTotal(0L);
        created.setComplaintCount7d(0);
        created.setDistinctShopCount7d(0);
        created.setCompensationCount30d(0);
        created.setCompensationRejected30d(0);
        created.setCleanDays(0);
        created.setPlatformContactFlag(false);
        created.setRiskLevelSince(LocalDateTime.now());
        created.setCreateTime(LocalDateTime.now());
        created.setUpdateTime(LocalDateTime.now());
        userMemoryMapper.insert(created);
        return created;
    }
}
