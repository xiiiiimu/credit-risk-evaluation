-- credit 独立信贷风控库完整 schema（一次性建库建表）
-- 执行：mysql -u root -p < src/main/resources/db/credit_schema.sql

CREATE DATABASE IF NOT EXISTS credit
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE credit;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_user` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `phone` varchar(20) DEFAULT NULL,
  `password` varchar(128) DEFAULT NULL,
  `nick_name` varchar(64) DEFAULT NULL,
  `role` varchar(16) DEFAULT 'USER' COMMENT 'USER/ADMIN/MERCHANT',
  `icon` varchar(255) DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表-信贷MVP最小副本';

CREATE TABLE IF NOT EXISTS `tb_user_memory` (
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `complaint_count` int NOT NULL DEFAULT 0,
  `compensation_count` int NOT NULL DEFAULT 0,
  `compensation_amount_total` bigint NOT NULL DEFAULT 0,
  `risk_level` varchar(16) NOT NULL DEFAULT 'LOW',
  `complaint_count7d` int NOT NULL DEFAULT 0,
  `distinct_shop_count7d` int NOT NULL DEFAULT 0,
  `compensation_count30d` int NOT NULL DEFAULT 0,
  `compensation_rejected30d` int NOT NULL DEFAULT 0,
  `preferred_category` varchar(64) DEFAULT NULL,
  `last_complaint_time` timestamp NULL DEFAULT NULL,
  `last_incident_time` timestamp NULL DEFAULT NULL,
  `risk_level_since` timestamp NULL DEFAULT NULL,
  `clean_days` int NOT NULL DEFAULT 0,
  `platform_contact_flag` tinyint(1) NOT NULL DEFAULT 0,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_agent_session` (
  `id` varchar(64) NOT NULL,
  `user_id` bigint(20) UNSIGNED DEFAULT NULL,
  `scene` varchar(32) NOT NULL DEFAULT 'credit',
  `title` varchar(128) DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_scene` (`user_id`, `scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_agent_message` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) NOT NULL,
  `role` varchar(16) NOT NULL,
  `content` text NOT NULL,
  `shop_ids` varchar(256) DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_agent_task_log` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) DEFAULT NULL,
  `trace_id` varchar(64) DEFAULT NULL,
  `tool_name` varchar(64) NOT NULL,
  `request_json` text,
  `response_json` text,
  `success` tinyint(1) NOT NULL DEFAULT 1,
  `cost_ms` int(11) DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_trace` (`trace_id`),
  KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_agent_workflow_trace` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `workflow_id` varchar(64) NOT NULL,
  `trace_id` varchar(64) DEFAULT NULL,
  `node_name` varchar(64) NOT NULL,
  `thought` text,
  `action` varchar(512) DEFAULT NULL,
  `observation` text,
  `decision` varchar(512) DEFAULT NULL,
  `tool_name` varchar(64) DEFAULT NULL,
  `tool_input` text,
  `tool_output` text,
  `latency_ms` int DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT 0,
  `error_msg` varchar(512) DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_workflow_time` (`workflow_id`, `create_time`),
  KEY `idx_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_agent_idempotent_record` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `scope` varchar(64) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `request_hash` varchar(64) DEFAULT NULL,
  `response_json` text,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_key` (`scope`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_credit_product` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_code` varchar(32) NOT NULL,
  `name` varchar(128) NOT NULL,
  `min_amount` decimal(12,2) NOT NULL DEFAULT 1000.00,
  `max_amount` decimal(12,2) NOT NULL DEFAULT 500000.00,
  `interest_rate` decimal(6,4) NOT NULL DEFAULT 0.0500,
  `term_months` int NOT NULL DEFAULT 12,
  `daily_quota` int NOT NULL DEFAULT 100,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code` (`product_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_credit_application` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `application_no` varchar(32) NOT NULL,
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `product_id` bigint(20) UNSIGNED NOT NULL,
  `apply_amount` decimal(12,2) NOT NULL,
  `apply_term` int NOT NULL DEFAULT 12,
  `purpose` varchar(32) DEFAULT NULL,
  `content` text NOT NULL,
  `document_status` varchar(16) DEFAULT 'PENDING',
  `verified_documents` tinyint(1) NOT NULL DEFAULT 0,
  `status` varchar(24) NOT NULL DEFAULT 'SUBMITTED',
  `ai_summary` varchar(512) DEFAULT NULL,
  `ai_analysis_json` text,
  `ai_suggestion_json` text,
  `platform_decision_json` text,
  `agent_suggestion` varchar(32) DEFAULT NULL,
  `final_decision` varchar(24) DEFAULT NULL,
  `suggested_amount` decimal(12,2) DEFAULT NULL,
  `suggested_rate` decimal(6,4) DEFAULT NULL,
  `approved_amount` decimal(12,2) DEFAULT NULL,
  `approved_rate` decimal(6,4) DEFAULT NULL,
  `risk_level` varchar(16) DEFAULT NULL,
  `risk_score` int DEFAULT NULL,
  `hit_rules_json` text,
  `decision_reason_json` text,
  `consensus_json` text,
  `bureau_unavailable` tinyint(1) DEFAULT 0,
  `conflict_detected` tinyint(1) DEFAULT 0,
  `income_debt_ratio` decimal(6,4) DEFAULT NULL,
  `document_score` decimal(4,3) DEFAULT NULL,
  `min_agent_confidence` decimal(4,3) DEFAULT NULL,
  `agent_conflicts_json` text,
  `fraud_score` int DEFAULT NULL,
  `ticket_id` bigint(20) DEFAULT NULL,
  `session_id` varchar(64) DEFAULT NULL,
  `workflow_id` varchar(64) DEFAULT NULL,
  `idempotency_key` varchar(64) DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_application_no` (`application_no`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_status` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_credit_async_task` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `product_id` bigint(20) UNSIGNED NOT NULL,
  `apply_amount` decimal(12,2) NOT NULL,
  `apply_term` int NOT NULL DEFAULT 12,
  `purpose` varchar(32) DEFAULT NULL,
  `content` text NOT NULL,
  `session_id` varchar(64) DEFAULT NULL,
  `trace_id` varchar(64) DEFAULT NULL,
  `workflow_id` varchar(64) DEFAULT NULL,
  `idempotency_key` varchar(64) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `application_id` bigint(20) DEFAULT NULL,
  `error_msg` varchar(512) DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_idempotency` (`idempotency_key`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_credit_advisory` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `application_id` bigint(20) UNSIGNED NOT NULL,
  `ticket_id` bigint(20) UNSIGNED DEFAULT NULL,
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `suggest_amount` decimal(12,2) DEFAULT NULL,
  `suggest_rate` decimal(6,4) DEFAULT NULL,
  `suggest_term` int DEFAULT NULL,
  `agent_consensus_json` text,
  `agent_suggestion` varchar(32) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_application` (`application_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_manual_review_ticket` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `application_id` bigint(20) UNSIGNED NOT NULL,
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `title` varchar(128) NOT NULL,
  `description` text,
  `reason` varchar(256) DEFAULT NULL,
  `priority` varchar(16) NOT NULL DEFAULT 'MEDIUM',
  `status` varchar(16) NOT NULL DEFAULT 'OPEN',
  `handler_id` bigint(20) DEFAULT NULL,
  `resolve_note` varchar(512) DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_application` (`application_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_credit_bureau_query` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `application_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `query_type` varchar(32) NOT NULL DEFAULT 'CREDIT_REPORT',
  `request_hash` varchar(64) NOT NULL,
  `result_summary_json` text,
  `mcp_trace_id` varchar(64) DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_hash` (`request_hash`),
  KEY `idx_user` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_credit_limit_account` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `product_id` bigint(20) UNSIGNED NOT NULL,
  `application_id` bigint(20) UNSIGNED NOT NULL,
  `approved_amount` decimal(12,2) NOT NULL,
  `used_amount` decimal(12,2) NOT NULL DEFAULT 0.00,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `expire_time` timestamp NULL DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_credit_review_feedback` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `application_id` bigint(20) UNSIGNED NOT NULL,
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `agent_suggestion` varchar(32) DEFAULT NULL,
  `java_decision` varchar(24) DEFAULT NULL,
  `human_decision` varchar(24) DEFAULT NULL,
  `risk_score` int DEFAULT NULL,
  `hit_rules_json` text,
  `review_reason` varchar(512) DEFAULT NULL,
  `reviewed_by` bigint(20) DEFAULT NULL,
  `reviewed_at` timestamp NULL DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_application` (`application_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tb_credit_workflow_trace` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `application_id` bigint(20) DEFAULT NULL,
  `task_id` bigint(20) DEFAULT NULL,
  `workflow_id` varchar(64) NOT NULL,
  `trace_id` varchar(64) DEFAULT NULL,
  `node_name` varchar(64) NOT NULL,
  `latency_ms` int DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'SUCCESS',
  `error_message` varchar(512) DEFAULT NULL,
  `tool_calls_json` text,
  `mcp_latency_ms` int DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_workflow` (`workflow_id`),
  KEY `idx_application` (`application_id`),
  KEY `idx_app_task` (`application_id`, `task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
