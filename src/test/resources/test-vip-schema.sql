-- VIP 测试账号专用 schema
-- 注意：不能 DROP 共享库中的其他表（skill_execution/publish_schedule 等），
-- 否则会破坏共享 H2 内存库中其他 @SpringBootTest 的 schema。
-- 这里仅确保 user / article 表存在（IF NOT EXISTS），并在缺失列时补充。

CREATE TABLE IF NOT EXISTS user
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    userAccount  VARCHAR(256) NOT NULL,
    userPassword VARCHAR(512) NOT NULL,
    userName     VARCHAR(256),
    userAvatar   VARCHAR(1024),
    userProfile  VARCHAR(512),
    userRole     VARCHAR(256) NOT NULL DEFAULT 'user',
    quota        INT NOT NULL DEFAULT 5,
    editTime     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    createTime   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updateTime   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    vipTime      TIMESTAMP,
    isDelete     TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_account UNIQUE (userAccount)
);

CREATE TABLE IF NOT EXISTS article
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    taskId              VARCHAR(64),
    userId              BIGINT NOT NULL,
    topic               VARCHAR(512),
    userDescription     TEXT,
    enabledImageMethods TEXT,
    style               VARCHAR(64),
    mainTitle           VARCHAR(512),
    subTitle            VARCHAR(512),
    titleOptions        TEXT,
    outline             TEXT,
    content             TEXT,
    fullContent         TEXT,
    coverImage          VARCHAR(2048),
    images              TEXT,
    status              VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    phase               VARCHAR(64),
    errorMessage        TEXT,
    createTime          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completedTime       TIMESTAMP,
    updateTime          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    isDelete            TINYINT NOT NULL DEFAULT 0
);
