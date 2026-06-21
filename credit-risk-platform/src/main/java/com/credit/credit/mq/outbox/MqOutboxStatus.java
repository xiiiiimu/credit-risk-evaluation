package com.credit.credit.mq.outbox;

public final class MqOutboxStatus {

    public static final String NEW = "NEW";
    public static final String SENDING = "SENDING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";

    private MqOutboxStatus() {
    }
}
