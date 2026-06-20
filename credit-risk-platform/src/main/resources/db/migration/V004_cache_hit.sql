-- Phase 5: 审计日志增加 cache_hit 标记
USE credit;

ALTER TABLE `tb_audit_log`
  ADD COLUMN `cache_hit` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否命中缓存' AFTER `success`;
