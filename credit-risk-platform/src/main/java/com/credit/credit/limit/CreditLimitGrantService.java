package com.credit.credit.limit;

import com.credit.credit.entity.CreditLimitAccount;
import com.credit.credit.mapper.CreditLimitAccountMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class CreditLimitGrantService {

    @Resource
    private CreditLimitAccountMapper creditLimitAccountMapper;

    @Transactional(rollbackFor = Exception.class)
    public CreditLimitAccount grant(Long userId, Long productId, Long applicationId,
                                    BigDecimal approvedAmount, int termMonths) {
        CreditLimitAccount account = new CreditLimitAccount();
        account.setUserId(userId);
        account.setProductId(productId);
        account.setApplicationId(applicationId);
        account.setApprovedAmount(approvedAmount);
        account.setUsedAmount(BigDecimal.ZERO);
        account.setStatus(CreditLimitAccount.ACTIVE);
        account.setExpireTime(LocalDateTime.now().plusMonths(termMonths));
        creditLimitAccountMapper.insert(account);
        return account;
    }
}
