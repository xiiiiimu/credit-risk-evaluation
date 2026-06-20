-- Phase 2: Prompt / Rule 配置化
USE credit;

CREATE TABLE IF NOT EXISTS `tb_prompt_config` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `prompt_code` varchar(64) NOT NULL,
  `prompt_content` text NOT NULL,
  `version` int NOT NULL DEFAULT 1,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_code_version` (`prompt_code`, `version`),
  KEY `idx_prompt_enabled` (`prompt_code`, `enabled`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Prompt 配置';

CREATE TABLE IF NOT EXISTS `tb_rule_config` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `rule_code` varchar(64) NOT NULL,
  `rule_content` text NOT NULL,
  `version` int NOT NULL DEFAULT 1,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_code_version` (`rule_code`, `version`),
  KEY `idx_rule_enabled` (`rule_code`, `enabled`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控规则配置';

INSERT INTO `tb_prompt_config` (`prompt_code`, `prompt_content`, `version`, `enabled`) VALUES
('document_review', '你是信贷资料审核助手。仅评估资料完整性，不做审批决定。禁止输出 APPROVED/REJECTED。输出 JSON：{"docComplete":true|false,"missingDocs":["ID_CARD"],"confidence":0.0-1.0,"summary":"摘要","ticketTitle":"工单标题","ticketDescription":"描述"}', 1, 1),
('credit_assessment', '你是信贷征信评估助手。结合征信、法院、收入信号给出结构化评估。禁止终审决定。输出 JSON：eligible、debtRatio、confidence、summary、vote 等字段。', 1, 1),
('anti_fraud', '你是信贷反欺诈摘要助手。仅根据平台规则引擎给出的欺诈分与命中规则生成人类可读摘要。禁止自行编造分数。输出 JSON：fraudLevel、riskSignals、summary、confidence。', 1, 1),
('credit_advisory', '你是信贷授信建议助手。根据前置分析给出建议额度、利率、期限。禁止输出 APPROVED/REJECTED。输出 JSON：suggestAmount、suggestRate、suggestTerm、summary、confidence。', 1, 1),
('llm_repair', '修复以下 JSON 使其符合 schema，只输出合法 JSON，不要 markdown。', 1, 1)
ON DUPLICATE KEY UPDATE `prompt_content` = VALUES(`prompt_content`);

INSERT INTO `tb_rule_config` (`rule_code`, `rule_content`, `version`, `enabled`) VALUES
('FRAUD_SIGNAL_WEIGHTS', '{"ipAbnormal":15,"deviceAbnormal":20,"proxyIp":20,"multiAccountDevice":25,"geoVelocityAbnormal":20,"highFrequencyApply":20,"blacklistHit":50,"highThreshold":70,"mediumThreshold":40}', 1, 1),
('CREDIT_APPROVAL_RULES', '{"fraudRejectThreshold":80,"riskScoreReject":85,"riskScoreManualHigh":70,"riskScoreManualMedium":40}', 1, 1),
('RISK_SCORE_RULES', '{"bureauUnavailableWeight":35,"bureauLow550":30,"bureauLow600":20,"bureauLow650":10,"fraudWeightFactor":0.35,"debtHigh":20,"debtMedium":10}', 1, 1)
ON DUPLICATE KEY UPDATE `rule_content` = VALUES(`rule_content`);
