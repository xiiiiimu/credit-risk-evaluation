package com.credit.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "credit.agent.risk")
public class AgentRiskProperties {

  /** HIGH 衰减至 MEDIUM 所需连续无异常天数 */
  private int highDecayDays = 14;

  /** MEDIUM 衰减至 LOW 所需连续无异常天数 */
  private int mediumDecayDays = 7;

  /** 自动补偿：7 天内投诉次数上限 */
  private int autoMaxComplaint7d = 1;

  /** 自动补偿：30 天内补偿次数上限 */
  private int autoMaxCompensation30d = 1;

  /** 自动补偿：商家责任置信度下限 */
  private double autoMinConfidence = 0.85;
}
