-- Phase 1: Workflow 状态持久化 + Checkpoint + 节点执行链路
USE credit;

CREATE TABLE IF NOT EXISTS `tb_workflow` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `workflow_id` varchar(64) NOT NULL COMMENT '全链路唯一业务ID',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/MANUAL_REVIEW',
  `current_node` varchar(64) DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT 0,
  `trace_id` varchar(64) DEFAULT NULL,
  `task_id` bigint(20) DEFAULT NULL,
  `application_id` bigint(20) DEFAULT NULL,
  `result_json` mediumtext COMMENT 'Agent 成功结果快照',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_id` (`workflow_id`),
  KEY `idx_workflow_status` (`status`),
  KEY `idx_workflow_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Workflow 主表';

CREATE TABLE IF NOT EXISTS `tb_workflow_node` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `workflow_id` varchar(64) NOT NULL,
  `node_name` varchar(64) NOT NULL,
  `agent_name` varchar(64) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/SKIPPED',
  `input_json` mediumtext,
  `output_json` mediumtext,
  `error_code` varchar(64) DEFAULT NULL,
  `error_msg` varchar(512) DEFAULT NULL,
  `start_time` timestamp NULL DEFAULT NULL,
  `end_time` timestamp NULL DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT 0,
  `cost_time_ms` int DEFAULT NULL,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_workflow_node` (`workflow_id`, `node_name`),
  KEY `idx_workflow_node_time` (`workflow_id`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Workflow 节点执行记录';

CREATE TABLE IF NOT EXISTS `tb_workflow_checkpoint` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `workflow_id` varchar(64) NOT NULL,
  `current_node` varchar(64) DEFAULT NULL,
  `state_json` mediumtext,
  `history_json` mediumtext,
  `retry_count` int NOT NULL DEFAULT 0,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_checkpoint_workflow` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LangGraph State Checkpoint';
