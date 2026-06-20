package com.credit.credit.fraud;

import com.credit.credit.fraud.dto.FraudSignalDTO;

/**
 * 外部安全风控信号接入层：底层 IP/设备识别由外部服务完成，本系统只消费结构化信号。
 */
public interface FraudSignalClient {

    FraudSignalDTO fetchSignals(Long userId, Long applicationId, String contentHint);
}
