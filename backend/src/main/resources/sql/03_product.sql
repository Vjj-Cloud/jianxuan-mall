-- =============================================================
-- M2-1 商品模块建表：category（分类） + product（商品）
-- 执行方式必须带字符集参数，否则中文会变成 ????
--   mysql -umall -p jianxuan_mall --default-character-set=utf8mb4 < src/main/resources/sql/03_product.sql
-- =============================================================

USE jianxuan_mall;

DROP TABLE IF EXISTS `product`;
DROP TABLE IF EXISTS `category`;

CREATE TABLE `category` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(50) NOT NULL COMMENT '分类名',
  `sort`       INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类';

CREATE TABLE `product` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `category_id`    BIGINT UNSIGNED NOT NULL COMMENT '所属分类',
  `name`           VARCHAR(100) NOT NULL COMMENT '商品名',
  `subtitle`       VARCHAR(200) NOT NULL DEFAULT '' COMMENT '卖点副标题',
  `price`          DECIMAL(10,2) NOT NULL COMMENT '售价（元）',
  `original_price` DECIMAL(10,2) NOT NULL COMMENT '划线价（元）',
  `stock`          INT NOT NULL DEFAULT 0 COMMENT '库存',
  `sales`          INT NOT NULL DEFAULT 0 COMMENT '销量',
  `status`         TINYINT NOT NULL DEFAULT 1 COMMENT '1 上架 / 0 下架',
  `cover`          VARCHAR(500) NOT NULL DEFAULT '' COMMENT '封面图 URL',
  `description`    TEXT NULL COMMENT '详情文案',
  `deleted`        TINYINT NOT NULL DEFAULT 0 COMMENT '0 正常 / 1 已删除（逻辑删除）',
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_category` (`status`, `category_id`),
  KEY `idx_sales` (`sales`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品';
