-- 强制客户端连接字符集为 utf8mb4（容器 locale 为 POSIX 时默认会是 latin1）
SET NAMES utf8mb4;

-- 旅游景点网站 数据库初始化
CREATE DATABASE IF NOT EXISTS tourism DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE tourism;

CREATE TABLE IF NOT EXISTS `user` (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE COMMENT '唯一登录名',
  password VARCHAR(100) NOT NULL COMMENT 'SHA-256 摘要',
  nickname VARCHAR(50) COMMENT '昵称',
  role VARCHAR(10) NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='用户表';

CREATE TABLE IF NOT EXISTS province (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB COMMENT='省份';

CREATE TABLE IF NOT EXISTS city (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  province_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  KEY idx_pid (province_id)
) ENGINE=InnoDB COMMENT='城市';

CREATE TABLE IF NOT EXISTS attraction (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  province_id BIGINT NOT NULL,
  city_id BIGINT NOT NULL,
  level VARCHAR(10) NOT NULL COMMENT '5A/4A/3A/2A/1A',
  image VARCHAR(255) COMMENT '景点图片URL',
  description TEXT COMMENT '景点介绍',
  address VARCHAR(255) COMMENT '详细地址',
  open_time VARCHAR(100) COMMENT '开放时间',
  ticket_price DECIMAL(10,2) COMMENT '参考门票价格',
  rating DECIMAL(3,1) DEFAULT 4.0 COMMENT '综合评分0-5',
  views INT DEFAULT 0 COMMENT '浏览热度',
  status TINYINT DEFAULT 1 COMMENT '1已发布 0下架',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_pid (province_id), KEY idx_cid (city_id), KEY idx_level (level)
) ENGINE=InnoDB COMMENT='景点';

-- 管理员账号 admin / admin123 （SHA-256）
INSERT INTO `user` (username, password, nickname, role, status) VALUES
('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', '系统管理员', 'ADMIN', 1);

-- 前台示例用户 tourist123 / 123456（启用） + tourist456（禁用，用于验证禁用登录拦截）
INSERT INTO `user` (username, password, nickname, role, status) VALUES
('tourist123', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '旅行者小明', 'USER', 1),
('tourist456', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '被封禁的游客', 'USER', 0);
