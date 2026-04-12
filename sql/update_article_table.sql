-- 修复 article 表，添加缺失的字段
use ai_passage_creator;

-- 添加 userDescription 字段
alter table article 
add column userDescription text null comment '用户补充描述' after topic;

-- 添加 enabledImageMethods 字段
alter table article 
add column enabledImageMethods json null comment '允许的配图方式列表（JSON 格式）' after userDescription;

-- 添加 style 字段
alter table article 
add column style varchar(50) null comment '文章风格：tech/emotional/educational/humorous' after enabledImageMethods;

-- 添加 titleOptions 字段
alter table article 
add column titleOptions json null comment '标题方案列表（JSON 格式）' after subTitle;

-- 添加 phase 字段
alter table article 
add column phase varchar(50) null comment '当前阶段' after status;
