-- AI 行程助手模块：会话与消息表（v1.0 2026-09-30）
-- 注意：容器 MySQL 仅在数据卷为空时自动执行 init 脚本；
--       已有环境请手动执行：docker exec -i <mysql容器> mysql -uroot -proot123456 tourism < 03-assistant.sql

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS chat_session (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  user_id     BIGINT       NOT NULL COMMENT '用户ID(逻辑外键 user.id)',
  title       VARCHAR(64)  NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话会话';

CREATE TABLE IF NOT EXISTS chat_message (
  id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  session_id  BIGINT   NOT NULL COMMENT '会话ID(逻辑外键 chat_session.id)',
  role        VARCHAR(16) NOT NULL COMMENT 'user|assistant',
  content     TEXT     NOT NULL COMMENT '消息内容(Markdown)',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话消息';
