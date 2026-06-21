package com.credit.credit.mq;

import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.credit.mq.audit.CreditApprovalMqAuditService;
import com.credit.credit.mq.config.RocketMqApprovalProperties;
import com.credit.credit.mq.producer.CreditApprovalTaskProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreditApprovalTaskProducerTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private RocketMqApprovalProperties properties;
    @Mock
    private CreditApprovalMqAuditService mqAuditService;
    @Mock
    private CreditAsyncTaskMapper creditAsyncTaskMapper;

    @InjectMocks
    private CreditApprovalTaskProducer producer;

    private CreditAsyncTask task;

    @BeforeEach
    void setUp() {
        task = new CreditAsyncTask();
        task.setId(10L);
        task.setWorkflowId("wf-10");
        task.setUserId(1L);
        task.setProductId(2L);
        task.setTraceId("trace-10");
        when(properties.destination()).thenReturn("credit-approval-task-topic:credit-apply");
        when(properties.getSendTimeoutMs()).thenReturn(3000);
    }

    @Test
    void send_success() throws Exception {
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);
        sendResult.setMsgId("msg-1");
        doReturn(sendResult).when(rocketMQTemplate).syncSend(anyString(), any(Message.class), anyLong());

        assertTrue(producer.send(task));
        verify(mqAuditService).record(
                eq(CreditApprovalMqAuditService.EVENT_MQ_SEND_SUCCESS),
                any(), eq(true), anyLong(), any(), any());
    }

    @Test
    void send_failed() {
        doThrow(new RuntimeException("broker down"))
                .when(rocketMQTemplate).syncSend(anyString(), any(Message.class), anyLong());

        assertFalse(producer.send(task));
        verify(mqAuditService).record(
                eq(CreditApprovalMqAuditService.EVENT_MQ_SEND_FAILED),
                any(), eq(false), anyLong(), eq("broker down"), any());
    }
}
