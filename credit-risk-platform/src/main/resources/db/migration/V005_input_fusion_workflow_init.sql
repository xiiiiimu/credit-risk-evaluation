-- Phase 6: 结构化字段 + 材料 OCR + Workflow INIT + 扩展审计
USE credit;

ALTER TABLE `tb_credit_async_task`
  ADD COLUMN `structured_application_json` mediumtext COMMENT '结构化申请字段 JSON' AFTER `content`,
  ADD COLUMN `user_narrative_json` mediumtext COMMENT '用户自然语言说明 JSON' AFTER `structured_application_json`,
  ADD COLUMN `uploaded_documents_json` mediumtext COMMENT '上传材料元数据 JSON' AFTER `user_narrative_json`;

ALTER TABLE `tb_credit_application`
  ADD COLUMN `structured_application_json` mediumtext COMMENT '结构化申请字段 JSON' AFTER `content`,
  ADD COLUMN `user_narrative_json` mediumtext COMMENT '用户自然语言说明 JSON' AFTER `structured_application_json`,
  ADD COLUMN `uploaded_documents_json` mediumtext COMMENT '上传材料元数据 JSON' AFTER `user_narrative_json`,
  ADD COLUMN `unified_risk_context_json` mediumtext COMMENT '融合后的风控上下文 JSON' AFTER `uploaded_documents_json`;

-- workflow status 支持 INIT（新建记录默认 INIT，消费前 CAS -> RUNNING）
ALTER TABLE `tb_workflow`
  MODIFY COLUMN `status` varchar(32) NOT NULL DEFAULT 'INIT'
    COMMENT 'INIT/PENDING/RUNNING/SUCCESS/FAILED/MANUAL_REVIEW';
