package com.credit.credit.mq.outbox;

import com.credit.credit.entity.CreditAsyncTask;
import com.credit.credit.mapper.CreditAsyncTaskMapper;
import com.credit.credit.mq.audit.CreditApprovalMqAuditService;
import com.credit.credit.mq.config.RocketMqApprovalProperties;
import com.credit.credit.mq.message.CreditApprovalTaskMessage;
import com.credit.credit.mq.producer.CreditApprovalTaskProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MqOutboxPublisherTest {

    @Mock
    private MqOutboxEventMapper mqOutboxEventMapper;
    @Mock
    private CreditAsyncTaskMapper creditAsyncTaskMapper;
    @Mock
    private CreditApprovalTaskProducer creditApprovalTaskProducer;
    @Mock
    private CreditApprovalMqAuditService mqAuditService;
    @Mock
    private MqOutboxService mqOutboxService;
    @Mock
    private RocketMqApprovalProperties properties;

    @InjectMocks
    private MqOutboxPublisher publisher;

    private MqOutboxEvent event;
    private CreditAsyncTask task;

    @BeforeEach
    void setUp() {
        event = new MqOutboxEvent();
        event.setId(1L);
        event.setRetryCount(0);
        event.setPayloadJson("{\"taskId\":10}");

        task = new CreditAsyncTask();
        task.setId(10L);
        task.setWorkflowId("wf-10");
        task.setStatus(CreditAsyncTask.PENDING);

        CreditApprovalTaskMessage message = CreditApprovalTaskMessage.builder().taskId(10L).build();
        when(mqOutboxService.parsePayload(event)).thenReturn(message);
        when(mqOutboxEventMapper.claimSending(1L)).thenReturn(1);
        when(creditAsyncTaskMapper.selectById(10L)).thenReturn(task);
        when(creditApprovalTaskProducer.send(task)).thenReturn(true);
    }

    @Test
    void publishOne_success_marksSentAndTaskMqSent() {
        publisher.publishOne(event);

        assertEquals(MqOutboxStatus.SENT, event.getStatus());
        assertEquals(CreditAsyncTask.MQ_SENT, task.getStatus());
        verify(mqOutboxEventMapper).updateById(event);
        verify(creditAsyncTaskMapper).updateById(task);
    }

    @Test
    void publishOne_claimFailed_skipsSend() {
        when(mqOutboxEventMapper.claimSending(1L)).thenReturn(0);

        publisher.publishOne(event);

        verify(creditApprovalTaskProducer, never()).send(any());
    }
}
