# 基于SpringBoot和Vue的前后端分离旅游景点网站

前后端分离 + 数据展示类项目（生产实习）。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、SpringBoot 3.2、Spring MVC、MyBatis-Plus、JWT |
| 前端 | Vue 3、Vite 5、Axios、Element Plus、ECharts |
| 数据库 | MySQL 8.0 |
| 部署 | Docker Compose 一键启动（mysql / backend / frontend 三容器） |

## 快速启动

```bash
docker compose up -d --build
```

启动后访问：

- 前台游客端：<http://localhost>
- 后台管理端：<http://localhost/admin/login>
- 后端 API：<http://localhost:8080/api/attractions>
- MySQL（宿主机）：`localhost:33061`，root / root123456

## 预置账号

| 端 | 账号 | 密码 | 说明 |
|---|---|---|---|
| 后台管理端 | admin | admin123 | 系统初始化分配，不能注册 |
| 前台 | tourist123 | 123456 | 示例用户（启用） |
| 前台 | tourist456 | 123456 | 示例用户（禁用，用于验证禁用登录拦截） |

## 功能清单

前台游客端：注册（用户名唯一校验）、登录、景点分页列表、按省市筛选（省→市联动）、
关键词模糊搜索、景点分级查看（5A~1A）、综合评分/浏览热度排序、景点详情（自动累计浏览量）。
**前台内容为登录可见：景点列表/详情/省市数据接口均已做登录校验（JWT），未登录访问将被拦截并跳转登录页。**

后台管理端：管理员登录、景点管理（条件查询/新增/修改/删除）、省市管理（CRUD，被景点引用时禁止删除并提示）、
用户管理（按用户名查询、禁用/启用）、数据统计（景点总数、各等级分布饼图、各省数量柱状图）。

## 目录结构

```
tourism-site/
├── docker-compose.yml      # 一键编排
├── backend/                # SpringBoot 后端（Dockerfile 多阶段构建）
├── frontend/               # Vue3 前端（node 构建 → nginx 运行，反代 /api）
└── mysql/init/             # 建表脚本 + 45 条真实景点种子数据 + 预置账号
```

> 说明：种子数据中的景点名称、地址为真实景点，门票价格、评分等数值仅供演示。
