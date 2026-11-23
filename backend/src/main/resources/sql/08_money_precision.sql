-- ============================================================
-- M11：金额精度对齐（订单总额列扩容）
--
-- 问题：商品单价是 DECIMAL(10,2)，理论上能存 99,999,999.99；
--       而 orders.total_amount 也是 DECIMAL(10,2)，容量只有 8 位整数。
--       单价 × 数量 × 商品数一旦超过 99,999,999.99，下单就会在 INSERT 时
--       报 Out of range value，事务回滚，用户看到的是 500「系统繁忙」。
--
--       这不是理论风险：管理员只要把单价填成 9 位数（接口原先没有上限校验），
--       这件商品就再也无法被购买，而且报错完全看不出原因。
--
-- 方案：
--   1. 订单总额扩到 DECIMAL(14,2)（12 位整数，最大 999,999,999,999.99）；
--   2. 商品单价在 DTO 层限制为 6 位整数（最大 999,999.99，见 ProductSaveRequest#price），
--      按上限推算最坏情况 50 × 999 × 999,999.99 ≈ 4.99e10，安全落在 12 位整数内。
--
--   两个动作必须一起做：只扩列不限价，等于允许一笔 4.99e12 的订单；
--   只限价不扩列，就是现在这个 bug（单价 8 位 × 数量 999 仍然溢出）。
--
-- 执行方式（可重复执行）：
--   mysql -uroot -p --default-character-set=utf8mb4 jianxuan_mall < 08_money_precision.sql
-- ============================================================

USE jianxuan_mall;

-- MODIFY 是「改成这个定义」，重复执行只是把同样的定义再写一遍，不会报错也不会丢数据。
-- 注意必须把 NOT NULL 和 COMMENT 一起写上：MODIFY 会用新定义整体替换旧定义，
-- 漏了 NOT NULL 会悄悄把列改成可空。
ALTER TABLE `orders`
    MODIFY COLUMN `total_amount` decimal(14, 2) NOT NULL COMMENT '服务端算出的实付总额';

-- 验证：精度应为 decimal(14,2)
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'jianxuan_mall'
  AND TABLE_NAME = 'orders'
  AND COLUMN_NAME = 'total_amount';
