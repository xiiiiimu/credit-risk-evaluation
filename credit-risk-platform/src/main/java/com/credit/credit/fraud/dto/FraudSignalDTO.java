package com.credit.credit.fraud.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FraudSignalDTO {

    private Long userId;
    private Long applicationId;
    private boolean ipAbnormal;
    private boolean deviceAbnormal;
    private boolean proxyIp;
    private boolean multiAccountDevice;
    private boolean geoVelocityAbnormal;
    private boolean highFrequencyApply;
    private boolean blacklistHit;
    private String riskLevel;
    private double confidence;
}
