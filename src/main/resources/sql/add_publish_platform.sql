-- 多平台分发：publish_schedule 加平台适配字段
ALTER TABLE publish_schedule ADD COLUMN platform varchar(32) DEFAULT 'wechat' NOT NULL;
ALTER TABLE publish_schedule ADD COLUMN content_title varchar(256) NULL;
ALTER TABLE publish_schedule ADD COLUMN adapter_output JSON NULL;
ALTER TABLE publish_schedule ADD COLUMN methodology_name varchar(64) DEFAULT 'default' NULL;
CREATE INDEX idx_ps_platform ON publish_schedule(platform);
