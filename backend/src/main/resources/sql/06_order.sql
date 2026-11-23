-- =============================================================
-- M4 订单：orders（订单主表） + order_item（订单明细，含商品快照）
--   mysql -umall -p jianxuan_mall --default-character-set=utf8mb4 < src/main/resources/sql/06_order.sql
-- =============================================================

USE jianxuan_mall;

DROP TABLE IF EXISTS `order_item`;
DROP TABLE IF EXISTS `orders`;

-- 表名必须是 orders，不能叫 order：ORDER 是 SQL 保留字（ORDER BY），
-- 叫 order 的话每条 SQL 都得写反引号，忘一次就是语法错误。
CREATE TABLE `orders` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`         VARCHAR(32)     NOT NULL COMMENT '业务订单号，对用户展示用',
  `user_id`          BIGINT UNSIGNED NOT NULL,
  `total_amount`     DECIMAL(14,2)   NOT NULL COMMENT '服务端算出的实付总额',
  `status`           VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                     COMMENT 'PENDING/SHIPPED/COMPLETED/CANCELLED',
  `receiver_name`    VARCHAR(50)     NOT NULL DEFAULT '',
  `receiver_phone`   VARCHAR(20)     NOT NULL DEFAULT '',
  `receiver_address` VARCHAR(255)    NOT NULL DEFAULT '',
  `shipped_at`       DATETIME        NULL COMMENT '发货时间，未发货为 NULL',
  `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  -- 订单号唯一：同一秒内生成的订单号如果撞了，插入会被数据库挡下，不会出现两张同号订单
  UNIQUE KEY `uk_order_no` (`order_no`),
  -- 用户查自己的订单：WHERE user_id = ? AND status = ?
  KEY `idx_user_status` (`user_id`, `status`),
  -- 后台按状态筛选 + 按时间排序：WHERE status = ? ORDER BY created_at DESC
  KEY `idx_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';

-- 明细表存的是「下单那一刻」的商品名/封面/价格快照。
-- 商品之后改名、改价、甚至被删除，历史订单看到的仍然是下单时的样子。
-- 这不是冗余设计错误，订单是历史凭证，必须能还原当时的事实。
CREATE TABLE `order_item` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`     BIGINT UNSIGNED NOT NULL,
  `product_id`   BIGINT UNSIGNED NOT NULL,
  `product_name` VARCHAR(100)    NOT NULL COMMENT '下单时商品名快照',
  `cover`        VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '下单时封面快照',
  `price`        DECIMAL(10,2)   NOT NULL COMMENT '下单时单价快照',
  `quantity`     INT             NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细（含商品快照）';
