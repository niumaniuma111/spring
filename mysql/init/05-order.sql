-- 门票订单模块（v1.0 2026-10-01）：ticket_order 订单表
-- 状态机：0待支付 → 1已支付 | 2已取消(用户) | 3已关闭(15分钟超时)
-- 注意：容器 MySQL 仅在数据卷为空时自动执行 init 脚本；
--       已有环境请手动执行：docker exec -i tourism-mysql mysql -uroot -p密码 tourism < 05-order.sql

SET NAMES utf8mb4;
USE tourism;

CREATE TABLE IF NOT EXISTS ticket_order (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  order_no      VARCHAR(32)   NOT NULL COMMENT '订单号 T+时间戳+随机6位',
  user_id       BIGINT        NOT NULL COMMENT '用户ID(逻辑外键 user.id)',
  attraction_id BIGINT        NOT NULL COMMENT '景点ID(逻辑外键 attraction.id)',
  quantity      INT           NOT NULL DEFAULT 1 COMMENT '票数(1-5)',
  unit_price    DECIMAL(10,2) NOT NULL COMMENT '下单时票价快照(元)',
  total_amount  DECIMAL(10,2) NOT NULL COMMENT '订单总额(元)',
  visit_date    DATE          NOT NULL COMMENT '游玩日期',
  status        TINYINT       NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已取消 3超时关闭',
  paid_at       DATETIME      NULL COMMENT '支付时间',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user (user_id),
  KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门票订单';
