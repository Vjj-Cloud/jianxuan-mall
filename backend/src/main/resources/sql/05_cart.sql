-- =============================================================
-- M3 购物车：cart_item 表
--   mysql -umall -p jianxuan_mall --default-character-set=utf8mb4 < src/main/resources/sql/05_cart.sql
-- =============================================================

USE jianxuan_mall;

DROP TABLE IF EXISTS `cart_item`;

CREATE TABLE `cart_item` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
  `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品',
  `quantity`   INT NOT NULL DEFAULT 1 COMMENT '数量',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  -- 一个用户对一个商品只能有一行；并发重复插入会被数据库直接挡掉
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车条目';
