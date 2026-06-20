package com.credit.credit.mq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "credit.mq")
public class RocketMqApprovalProperties {

    private boolean enabled = true;
    private String nameServer = "127.0.0.1:9876";
    private String producerGroup = "credit-approval-producer";
    private String consumerGroup = "credit-approval-task-consumer";
    private String topic = "credit-approval-task-topic";
    private String tag = "credit-apply";
    private int sendTimeoutMs = 3000;
    private int producerRetryTimes = 2;
    private int consumerThreadMin = 4;
    private int consumerThreadMax = 16;
    private int maxReconsumeTimes = 16;
    private String dlqTopic = "%DLQ%credit-approval-task-consumer";
    private String dlqConsumerGroup = "credit-approval-task-dlq-consumer";

    public String destination() {
        return topic + ":" + tag;
    }

    public String dlqTopic() {
        return dlqTopic != null ? dlqTopic : "%DLQ%" + consumerGroup;
    }
}
