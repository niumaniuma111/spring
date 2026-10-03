-- 景点图片上传（v1.0 2026-10-02）：上传的图片二进制存 DB（MEDIUMBLOB，≤5MB）
-- 通过 /api/img/{id} 读取；image 字段存 "/api/img/{id}" 访问路径
-- 注意：已有环境请手动执行：docker exec -i tourism-mysql mysql -uroot -p密码 tourism < 07-image.sql

SET NAMES utf8mb4;
USE tourism;

CREATE TABLE IF NOT EXISTS attraction_image (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '图片ID',
  file_name    VARCHAR(255) NULL COMMENT '原始文件名',
  content_type VARCHAR(100) NOT NULL COMMENT 'MIME 类型',
  data         MEDIUMBLOB  NOT NULL COMMENT '图片二进制',
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='景点上传图片';
