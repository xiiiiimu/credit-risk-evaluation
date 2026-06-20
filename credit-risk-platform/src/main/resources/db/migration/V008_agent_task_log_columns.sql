-- tb_agent_task_log 与 AgentTaskLog 实体对齐（MySQL 5.7 逐列添加，已存在则跳过该条）
ALTER TABLE `tb_agent_task_log` ADD COLUMN `agent_name` varchar(64) DEFAULT NULL COMMENT 'Agent 名称' AFTER `trace_id`;
ALTER TABLE `tb_agent_task_log` ADD COLUMN `call_type` varchar(32) DEFAULT NULL COMMENT 'REMOTE/TOOL/WORKFLOW' AFTER `agent_name`;
ALTER TABLE `tb_agent_task_log` ADD COLUMN `user_id` bigint(20) UNSIGNED DEFAULT NULL COMMENT '用户 ID' AFTER `call_type`;
ALTER TABLE `tb_agent_task_log` ADD COLUMN `error_msg` varchar(512) DEFAULT NULL COMMENT '错误信息' AFTER `success`;
