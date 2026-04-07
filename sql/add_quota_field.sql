-- 为用户表添加 quota 字段
use ai_passage_creator;

ALTER TABLE user 
ADD COLUMN quota INT DEFAULT 10 NOT NULL COMMENT '剩余配额' AFTER userRole;

-- 更新现有用户的配额
UPDATE user SET quota = 10 WHERE userRole = 'admin';
UPDATE user SET quota = 5 WHERE userRole = 'user';
