package com.credit.credit.service;

import com.credit.credit.entity.CreditApplication;
import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.enums.ApplicationStatus;
import com.credit.credit.mapper.CreditApplicationMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.UUID;

/**
 * 在调用 Python Agent 前创建草稿申请，以便 workflow trace 携带精确 application_id。
 */
@Service
public class CreditDraftApplicationService {

    @Resource
    private CreditApplicationMapper creditApplicationMapper;

    public CreditApplication createDraft(CreditAsyncTask task) {
        CreditApplication app = new CreditApplication();
        app.setApplicationNo("CA" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        app.setUserId(task.getUserId());
        app.setProductId(task.getProductId());
        app.setApplyAmount(task.getApplyAmount());
        app.setApplyTerm(task.getApplyTerm());
        app.setPurpose(task.getPurpose());
        app.setContent(task.getContent());
        app.setSessionId(task.getSessionId());
        app.setWorkflowId(task.getWorkflowId());
        app.setIdempotencyKey(task.getIdempotencyKey());
        app.setStatus(ApplicationStatus.ANALYZING);
        app.setDocumentStatus("PENDING");
        app.setVerifiedDocuments(false);
        creditApplicationMapper.insert(app);
        return app;
    }
}
