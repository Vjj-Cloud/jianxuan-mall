-- ============================================================
-- M1 · 用户表
-- 执行方式（在 backend 目录下）：
--   mysql -umall -p jianxuan_mall --default-character-set=utf8mb4 \
--     < src/main/resources/sql/01_user.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS `user` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `username`   VARCHAR(50)     NOT NULL                COMMENT '登录名，唯一',
  `password`   VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 哈希（60 位，留 100 有余量）',
  `nickname`   VARCHAR(50)     NOT NULL DEFAULT ''     COMMENT '昵称，注册不传则用用户名',
  `role`       VARCHAR(20)     NOT NULL DEFAULT 'USER' COMMENT '角色：USER 普通用户 / ADMIN 管理员',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户';
