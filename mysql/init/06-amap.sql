-- 高德地图功能（v1.0 2026-10-01）：景点坐标 + 城市 adcode
-- 注意：容器 MySQL 仅在数据卷为空时自动执行 init 脚本；
--       已有环境请手动执行：docker exec -i tourism-mysql mysql -uroot -p密码 tourism < 06-amap.sql

SET NAMES utf8mb4;
USE tourism;

ALTER TABLE attraction
    ADD COLUMN lng DECIMAL(10,6) NULL COMMENT '经度(高德坐标系GCJ-02)' AFTER status,
    ADD COLUMN lat DECIMAL(10,6) NULL COMMENT '纬度(高德坐标系GCJ-02)' AFTER lng;

ALTER TABLE city
    ADD COLUMN adcode VARCHAR(10) NULL COMMENT '高德城市区划码(天气查询用)' AFTER name;
