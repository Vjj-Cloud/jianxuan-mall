-- 清理压测脚本（benchmark.py）留下的数据
--
-- 用法：
--   mysql -umall -p jianxuan_mall --default-character-set=utf8mb4 < scripts/cleanup_bench.sql
--
-- 压测订单的收货人固定是「压测」，据此识别。

-- 1. 先删订单明细（子表先删，避免留下孤儿记录）
DELETE oi FROM order_item oi
  JOIN orders o ON oi.order_id = o.id
 WHERE o.receiver_name = '压测';

-- 2. 再删订单主表
DELETE FROM orders WHERE receiver_name = '压测';

-- 3. 压测商品。应用里的删除是逻辑删除（deleted=1），
--    但压测数据不需要保留痕迹，这里直接物理删除。
DELETE FROM product WHERE name LIKE '【压测】%';

-- 核对：下面三条应该都返回 0
SELECT '残留订单明细' AS 项, COUNT(*) AS 条数
  FROM order_item oi JOIN orders o ON oi.order_id = o.id
 WHERE o.receiver_name = '压测'
UNION ALL
SELECT '残留订单', COUNT(*) FROM orders WHERE receiver_name = '压测'
UNION ALL
SELECT '残留压测商品', COUNT(*) FROM product WHERE name LIKE '【压测】%';
