-- ============================================================
-- M9：下单幂等
--
-- 问题：用户手抖点两次，或前端请求超时后重试，会生成两笔订单、扣两次库存。
--       现有代码唯一的防护是「防超卖」（stock >= ? 条件更新），它保证不超卖，
--       但保证不了「同一次购买意图只产生一笔订单」。
--
-- 方案：给订单表加一列客户端幂等键 + (user_id, request_id) 唯一索引。
--       唯一索引是数据层的强保证——无论应用层怎么并发、Redis 是否可用，
--       同一个用户的同一个请求最多只能落一行。
--
-- 执行方式：
--   mysql -uroot -p --default-character-set=utf8mb4 jianxuan_mall < 07_order_idempotent.sql
-- ============================================================

USE jianxuan_mall;

-- 1. 加列。
--    NOT NULL DEFAULT '' 而不是允许 NULL：MySQL 的唯一索引对 NULL 不做去重，
--    如果允许 NULL，漏传 requestId 的记录会全部绕过唯一约束，等于没有保护。
ALTER TABLE `orders`
    ADD COLUMN `request_id` varchar(64) NOT NULL DEFAULT '' COMMENT '客户端幂等键，同一次购买意图的唯一标识' AFTER `status`;

-- 2. 回填历史数据。
--    加唯一索引之前必须先回填，否则所有历史订单的 request_id 都是 ''，
--    (user_id, '') 会立刻冲突——同一个用户只能留下一张历史订单，其余全部建索引失败。
--    用 legacy-{id} 保证每条都不同。
UPDATE `orders` SET `request_id` = CONCAT('legacy-', `id`) WHERE `request_id` = '';

-- 3. 唯一索引。
--    顺序是 (user_id, request_id) 而不是反过来：查询永远带 user_id（当前登录用户），
--    唯一性判定也只在同一用户内。反过来写的话，不同用户用了同一个 requestId 会互相冲突。
ALTER TABLE `orders`
    ADD UNIQUE KEY `uk_user_request` (`user_id`, `request_id`);

-- 4. 验证
SELECT COUNT(*) AS total,
       COUNT(DISTINCT CONCAT(user_id, ':', request_id)) AS distinct_key,
       SUM(request_id = '') AS empty_key
FROM `orders`;
