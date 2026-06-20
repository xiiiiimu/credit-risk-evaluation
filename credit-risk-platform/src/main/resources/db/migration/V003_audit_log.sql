-- Phase 4: 审计日志
USE credit;

CREATE TABLE IF NOT EXISTS `tb_audit_log` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `workflow_id` varchar(64) DEFAULT NULL,
  `trace_id` varchar(64) DEFAULT NULL,
  `node_name` varchar(64) DEFAULT NULL,
  `call_type` varchar(32) NOT NULL COMMENT 'LLM / TOOL',
  `prompt_version` int DEFAULT NULL,
  `rule_version` varchar(128) DEFAULT NULL,
  `request_json` text,
  `response_json` text,
  `token_count` int DEFAULT 0,
  `cost_time_ms` int DEFAULT 0,
  `success` tinyint(1) NOT NULL DEFAULT 1,
  `error_msg` varchar(512) DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_workflow` (`workflow_id`, `create_time`),
  KEY `idx_audit_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM/Tool 审计日志';
