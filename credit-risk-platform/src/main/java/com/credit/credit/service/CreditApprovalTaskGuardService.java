package com.credit.credit.service;

import com.credit.credit.entity.CreditAsyncTask;
import org.springframework.stereotype.Service;

@Service
public class CreditApprovalTaskGuardService {

    public boolean isTerminal(String status) {
        return CreditAsyncTask.SUCCESS.equals(status)
                || CreditAsyncTask.MANUAL_REVIEW.equals(status)
                || CreditAsyncTask.FAILED.equals(status);
    }

    public boolean canConsume(String status) {
        return CreditAsyncTask.PENDING.equals(status)
                || CreditAsyncTask.MQ_SENT.equals(status)
                || CreditAsyncTask.MQ_SEND_FAILED.equals(status)
                || CreditAsyncTask.FAILED.equals(status)
                || CreditAsyncTask.RUNNING.equals(status);
    }
}
