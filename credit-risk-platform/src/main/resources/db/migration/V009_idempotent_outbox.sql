-- V009: Idempotency record status columns + MQ transactional outbox
USE credit;

ALTER TABLE `tb_agent_idempotent_record`
  ADD COLUMN `status` varchar(32) NOT NULL DEFAULT 'SUCCESS' COMMENT 'PROCESSING/SUCCESS/FAILED' AFTER `response_json`,
  ADD COLUMN `error_msg` varchar(512) DEFAULT NULL AFTER `status`,
  ADD COLUMN `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `create_time`;

CREATE TABLE IF NOT EXISTS `tb_mq_outbox_event` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_key` varchar(128) NOT NULL COMMENT '幂等事件键，例如 credit-approval:{taskId}',
  `aggregate_type` varchar(64) NOT NULL COMMENT '例如 CREDIT_APPROVAL_TASK',
  `aggregate_id` varchar(64) NOT NULL COMMENT 'taskId',
  `event_type` varchar(64) NOT NULL COMMENT '例如 CREDIT_APPROVAL_TASK_CREATED',
  `destination` varchar(255) NOT NULL COMMENT 'RocketMQ topic:tag destination',
  `payload_json` text NOT NULL,
  `status` varchar(32) NOT NULL COMMENT 'NEW/SENDING/SENT/FAILED',
  `retry_count` int NOT NULL DEFAULT 0,
  `next_retry_time` datetime DEFAULT NULL,
  `last_error` text DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_key` (`event_key`),
  KEY `idx_status_next_retry_time` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
