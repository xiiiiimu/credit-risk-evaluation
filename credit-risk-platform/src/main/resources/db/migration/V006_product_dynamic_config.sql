-- Phase 7: 产品规则动态化 + 材料要求配置
USE credit;

ALTER TABLE `tb_credit_product`
  ADD COLUMN `product_type` varchar(32) DEFAULT 'CONSUMER_CREDIT' COMMENT '产品类型' AFTER `name`,
  ADD COLUMN `supported_terms_json` varchar(128) DEFAULT '[6,12,24,36]' COMMENT '支持期限 JSON 数组' AFTER `term_months`,
  ADD COLUMN `description` varchar(512) DEFAULT NULL COMMENT '产品描述' AFTER `status`;

CREATE TABLE IF NOT EXISTS `tb_product_rule_config` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) UNSIGNED NOT NULL,
  `rule_code` varchar(64) NOT NULL DEFAULT 'PRODUCT_APPROVAL_RULES',
  `rule_content_json` mediumtext NOT NULL,
  `version` int NOT NULL DEFAULT 1,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_rule_version` (`product_id`, `rule_code`, `version`),
  KEY `idx_product_rule_enabled` (`product_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品级审批规则配置';

CREATE TABLE IF NOT EXISTS `tb_product_material_requirement` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) UNSIGNED NOT NULL,
  `document_type` varchar(32) NOT NULL,
  `required` tinyint(1) NOT NULL DEFAULT 1,
  `min_months` int DEFAULT NULL,
  `min_confidence` decimal(5,4) DEFAULT 0.8000,
  `description` varchar(256) DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_material` (`product_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品材料要求';

INSERT INTO `tb_product_rule_config` (`product_id`, `rule_code`, `rule_content_json`, `version`, `enabled`)
SELECT p.id, 'PRODUCT_APPROVAL_RULES',
  '{"riskLevelAmountRatio":{"LOW":1.0,"MEDIUM":0.6,"HIGH":0.0},"riskLevelRateAdjustment":{"LOW":0.0,"MEDIUM":1.2,"HIGH":0.0},"rejectThreshold":80,"manualReviewThreshold":60,"maxDebtIncomeRatio":0.5}',
  1, 1
FROM `tb_credit_product` p
WHERE NOT EXISTS (
  SELECT 1 FROM `tb_product_rule_config` c WHERE c.product_id = p.id AND c.rule_code = 'PRODUCT_APPROVAL_RULES'
);

INSERT INTO `tb_product_material_requirement` (`product_id`, `document_type`, `required`, `min_months`, `min_confidence`, `description`, `enabled`)
SELECT p.id, 'ID_CARD', 1, NULL, 0.8500, '身份证', 1 FROM `tb_credit_product` p
WHERE NOT EXISTS (SELECT 1 FROM `tb_product_material_requirement` m WHERE m.product_id = p.id AND m.document_type = 'ID_CARD');

INSERT INTO `tb_product_material_requirement` (`product_id`, `document_type`, `required`, `min_months`, `min_confidence`, `description`, `enabled`)
SELECT p.id, 'BANK_STATEMENT', 1, 6, 0.8000, '近6个月银行流水', 1 FROM `tb_credit_product` p
WHERE NOT EXISTS (SELECT 1 FROM `tb_product_material_requirement` m WHERE m.product_id = p.id AND m.document_type = 'BANK_STATEMENT');
