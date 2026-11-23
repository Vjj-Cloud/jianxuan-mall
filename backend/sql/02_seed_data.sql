-- ============================================================
-- 演示种子数据
-- 对应前端登录页的「演示账号」，让 clone 下来的项目开箱即可登录
-- 密码均为 BCrypt 哈希（cost=10），明文见注释
-- 可重复执行：已存在则更新密码与角色
-- ============================================================

USE jianxuan_mall;

INSERT INTO `user` (username, password, nickname, role) VALUES
    -- 明文 user123
    ('user',  '$2a$10$xeTTOi5uF5NUi0GlX3TvyubfK9BnjgtHNUCIW7R.CWWX.jc1V7geG', '测试用户', 'USER'),
    -- 明文 admin123
    ('admin', '$2a$10$Wqprx4v4YTnIBss6mou/y.DfKOWdbQsL2dVYfAI/s6Q1LsNFTZLPK', '管理员',   'ADMIN')
AS new
ON DUPLICATE KEY UPDATE
    password = new.password,
    nickname = new.nickname,
    role     = new.role;

SELECT id, username, nickname, role FROM `user` ORDER BY id;
