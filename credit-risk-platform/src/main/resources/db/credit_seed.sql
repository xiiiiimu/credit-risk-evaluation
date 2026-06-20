-- credit 演示 seed 数据
-- 执行：mysql -u root -p credit < src/main/resources/db/credit_seed.sql

USE credit;

SET NAMES utf8mb4;

INSERT INTO `tb_user` (`id`, `phone`, `password`, `nick_name`, `role`)
SELECT 1, '13800000001', '', '低风险用户', 'USER'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `tb_user` WHERE `id` = 1);

INSERT INTO `tb_user` (`id`, `phone`, `password`, `nick_name`, `role`)
SELECT 2, '13800000002', '', '高风险用户', 'USER'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `tb_user` WHERE `id` = 2);

INSERT INTO `tb_user` (`id`, `phone`, `password`, `nick_name`, `role`)
SELECT 999, '13900000000', '', '运营管理员', 'ADMIN'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `tb_user` WHERE `id` = 999);

INSERT INTO `tb_user_memory` (`user_id`, `risk_level`, `complaint_count7d`)
SELECT 1, 'LOW', 0 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `tb_user_memory` WHERE `user_id` = 1);

INSERT INTO `tb_user_memory` (`user_id`, `risk_level`, `complaint_count7d`)
SELECT 2, 'MEDIUM', 2 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `tb_user_memory` WHERE `user_id` = 2);

INSERT INTO `tb_user_memory` (`user_id`, `risk_level`)
SELECT 999, 'LOW' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `tb_user_memory` WHERE `user_id` = 999);

INSERT INTO `tb_credit_product` (`product_code`, `name`, `min_amount`, `max_amount`, `interest_rate`, `term_months`, `daily_quota`, `status`)
SELECT 'CONSUMER_LOAN_01', '个人消费贷', 5000.00, 200000.00, 0.0650, 12, 100, 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `tb_credit_product` WHERE `product_code` = 'CONSUMER_LOAN_01');

INSERT INTO `tb_credit_product` (`product_code`, `name`, `min_amount`, `max_amount`, `interest_rate`, `term_months`, `daily_quota`, `status`)
SELECT 'CONSUMER_LOAN_HOT', '热门消费贷', 5000.00, 300000.00, 0.0580, 24, 500, 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `tb_credit_product` WHERE `product_code` = 'CONSUMER_LOAN_HOT');

-- 演示 content 关键字：
-- low_risk / high_risk device_abnormal / mcp_timeout / ip_abnormal proxy multi_account
