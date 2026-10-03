# AI 行程助手设计文档（AI Travel Assistant）

> 项目：tourism-site（SpringBoot + Vue 前后端分离旅游景点网站）
> 版本：v1.1（实现级设计，含现有代码接入点调研结论 · 2026-09-30）
> 状态：**待审查** —— 审查通过前不写任何代码

---

## 1. 背景与目标

当前网站是**纯展示型**（浏览景点 / 后台管理）。本模块接入 DeepSeek 大模型，新增「AI 行程助手」，实现两个核心能力：

1. **行程规划**：输入目的地 / 天数 / 预算 / 出行偏好，生成多日行程单
2. **景点智能问答**：基于数据库真实景点数据回答旅游相关问题（轻量 RAG）

**升级叙事**（答辩口径）：从"展示型网站"升级为"带智能化服务的综合旅游平台"。

**依赖策略**：后端 Maven 零新增依赖（复用 JDK 17 HttpClient + Spring 自带 SseEmitter）；前端仅新增 `markdown-it` 一个 npm 包。

---

## 2. 功能设计

### 2.1 能力矩阵

| 能力 | 输入 | 输出 | 数据来源 |
|------|------|------|----------|
| 行程规划 | 目的地、天数、预算、出行人群、节奏 | Markdown 多日行程单（景点、顺序、预算估算、贴士） | 数据库景点 + 大模型组织 |
| 景点智能问答 | 自然语言问题 | 回答 + 推荐景点（只推荐库内真实存在的） | 先查库 → 注入上下文 → 大模型润色 |

### 2.2 核心约束（写进 System Prompt，防幻觉）

- 只推荐系统内真实存在的景点，禁止编造景点名/门票价（票价、开放时间以数据库数据为准）
- 数据库没有的目的地可给通用建议，但须注明"该地区暂未收录"
- 回复统一使用 Markdown

### 2.3 轻量 RAG 方案

不引入向量库，采用**结构化数据注入**：

```
用户提问 → 意图分流（规则：含「行程/路线/几天/攻略/规划/日程」→ 规划；否则问答）
        → 检索数据库（问题中含省/市名 → 查该地景点；否则全库按 rating 取 Top20）
        → 组装紧凑清单（名称|城市|等级|票价|简介摘要≤60字）
        → 拼进 Prompt → DeepSeek 流式生成 → SSE 逐字返回 → 全文落库
```

**取舍**：51 个景点量级下，数据库检索 + 上下文注入召回质量足够且零基础设施；景点增长到万级再演进向量检索。答辩可讲清此权衡。

---

## 3. 交互设计

### 3.1 入口

- 前端导航栏新增「AI 助手」→ `/assistant` 独立聊天页
- 需登录（复用现有 JWT + 路由守卫），未登录跳 `/login`

### 3.2 页面布局（AssistantChat.vue）

```
┌─────────────┬──────────────────────────────┐
│ 会话列表     │  消息区（Markdown 渲染）        │
│ + 新建对话   │  [用户] 帮我规划成都三日游       │
│ 成都三日游   │  [AI]   Day1 ... (打字机流式)   │
│ 亲子去哪玩   │                              │
│             ├──────────────────────────────┤
│             │  快捷按钮: 规划行程 | 问景点      │
│             │  [输入框____________] [发送]    │
└─────────────┴──────────────────────────────┘
```

- 左侧：会话列表（按最近更新排序）+ 新建对话 + 删除
- 右侧：消息气泡（用户右/AI 左）、AI 回复 markdown-it 渲染、打字机效果
- 首次进入展示快捷指令：「帮我规划一趟行程」「问答模式：问景点」，点击填充输入框模板

---

## 4. 现有代码接入点（调研结论）

| 接入点 | 现状 | 本模块如何复用 |
|--------|------|----------------|
| 鉴权 | `LoginInterceptor` 解析 JWT 后 `req.setAttribute("uid", ...)` | Controller 里 `(Long) req.getAttribute("uid")` 直接取当前用户 |
| 拦截注册 | `WebConfig.addInterceptors` 白名单式 addPathPatterns | **需修改**：追加 `"/api/assistant/**"` 到 loginInterceptor |
| 统一返回 | `Result<T>`，`code=0` 成功；前端 axios 拦截器自动剥 data | 新接口沿用 `Result.ok(...)` |
| 实体风格 | MyBatis-Plus 注解 + **手写 getter/setter（项目无 Lombok）** | 新实体同样手写，不引 Lombok |
| Mapper | 纯接口 extends BaseMapper | 新 Mapper 同样两行搞定 |
| 前端 token | `localStorage.user.token`，axios 请求拦截器加 Bearer | 会话管理走 axios；**聊天走原生 fetch**（axios 不支持流式读取响应体） |
| 前端代理 | vite baseURL `/api` | fetch 同样请求 `/api/assistant/chat`，走同一代理 |
| SQL 脚本 | init 目录已有 `01-schema.sql`、`02-data.sql` | 新脚本命名 **`03-assistant.sql`**（注意：非 02，文档 v1.0 笔误已修正） |

---

## 5. 接口设计

统一返回 `Result<T>`（code=0 成功）；聊天接口返回 `text/event-stream`。所有接口走 LoginInterceptor（JWT）。

| 方法 | 路径 | 说明 | 请求 | 响应 data |
|------|------|------|------|-----------|
| POST | `/api/assistant/sessions` | 新建会话 | `{title?}` | `{id, title}` |
| GET | `/api/assistant/sessions` | 我的会话列表 | - | `[{id, title, updatedAt}]` |
| GET | `/api/assistant/sessions/{id}/messages` | 会话历史消息 | - | `[{id, role, content, createdAt}]` |
| DELETE | `/api/assistant/sessions/{id}` | 删除会话（连带消息） | - | 无 |
| POST | `/api/assistant/chat` | **发消息（SSE 流式）** | `{sessionId, message}` | `text/event-stream` |

**SSE 事件协议**：

```
event: delta          ← 增量文本（打字机）
data: {"content":"**Day 1**"}

event: done           ← 流结束，前端解除发送锁定
data: {"messageId":123}

event: error          ← 统一错误出口（key 未配置/超时/限流）
data: {"message":"AI 服务繁忙，请稍后重试"}
```

**校验规则**：sessionId 归属校验（非本人会话→403）；message 非空 ≤2000 字符；单次回复 max_tokens=2048。

---

## 6. 数据库设计（`mysql/init/03-assistant.sql`）

遵循项目约定：utf8mb4_unicode_ci、逻辑外键不加物理 FOREIGN KEY、首行 `SET NAMES utf8mb4`。

```sql
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
```

> ⚠️ 容器 MySQL 只在数据卷为空时执行 init 脚本。**已有环境需手动执行此 SQL**（进入容器 mysql 执行，或 `docker compose down -v` 重建）。

---

## 7. DeepSeek 集成与安全

### 7.1 API 参数

| 项 | 值 |
|----|-----|
| Endpoint | `https://api.deepseek.com/chat/completions`（OpenAI 兼容） |
| Model | `deepseek-chat` |
| stream | `true` |
| temperature | 行程规划 0.7 / 景点问答 0.5 |
| max_tokens | 2048 |

### 7.2 配置注入（application.yml 增量）

```yaml
deepseek:
  api-key: ${DEEPSEEK_API_KEY:}      # 必须环境变量注入，禁止写死
  base-url: https://api.deepseek.com
  model: deepseek-chat
  connect-timeout-seconds: 10
  read-timeout-seconds: 120
```

`docker-compose.yml` backend 服务 environment 增加 `DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:-}`，真实 key 放宿主机 `.env`（**加入 .gitignore**）或启动脚本。

### 7.3 安全红线

1. API key 绝不硬编码进源码/文档/git，仅环境变量注入
2. key 未配置时聊天接口发 `event: error`（"AI 服务未配置"），不抛 500
3. 超时：连接 10s / 读取 120s；HttpClient 异常统一转 `event: error`
4. 输入 ≤2000 字符；历史上下文只带**最近 10 条**消息（控制 token 成本）

---

## 8. 提示词设计

### 8.1 System Prompt（通用角色，两模式共用）

```
你是「游中国」旅游网站的 AI 行程助手。规则：
1. 只推荐下方【景点资料】中列出的景点，禁止编造景点名称与门票价格；
   票价与开放时间必须与资料一致，资料未给出的信息回答"以景区公示为准"。
2. 若用户想去的目的地不在资料中，可给通用建议，并注明"该地区暂未收录，建议补充资料后再来查询"。
3. 用简体中文，回复用 Markdown（行程单用标题+列表，可加表格），语气友好专业。
4. 行程规划需考虑：景点地理位置就近串联、节奏适中（每天 2-3 个点）、预算合理分配。
```

### 8.2 行程规划 User Prompt 模板

```
【用户需求】目的地：{provinceName}/{cityName 或不限}；天数：{days}天；预算：{budget}；出行人群：{companion}；其他要求：{extra}

【景点资料】(名称|城市|等级|票价|简介摘要)
九寨沟|阿坝州|5A|169|世界自然遗产，高山湖泊群...
...（最多 20 条，按 rating desc）

请为用户生成 {days} 天的行程单。
```

### 8.3 问答 Prompt

```
【用户问题】{question}

【检索到的相关景点】...（同上格式，无命中则写"无"）

请基于资料回答；资料不足以回答的部分明确说明。
```

### 8.4 会话标题

首条用户消息截取前 20 字作 title，不额外调模型。

---

## 9. 文件改动清单（审查重点）

### 新增（后端 8 个 + SQL 1 个）

| # | 文件 | 要点 |
|---|------|------|
| 1 | `mysql/init/03-assistant.sql` | §6 DDL |
| 2 | `entity/ChatSession.java` | @TableName("chat_session")，手写 getter/setter |
| 3 | `entity/ChatMessage.java` | 同上 |
| 4 | `mapper/ChatSessionMapper.java` | extends BaseMapper |
| 5 | `mapper/ChatMessageMapper.java` | extends BaseMapper |
| 6 | `config/DeepSeekProperties.java` | @ConfigurationProperties(prefix="deepseek") + @Component |
| 7 | `service/DeepSeekService.java` | HttpClient 流式调用 + SSE 分块解析（骨架见 §10.1） |
| 8 | `service/AssistantContextService.java` | 意图分流 / 查库组上下文 / 历史窗口裁剪（骨架见 §10.2） |
| 9 | `controller/AssistantController.java` | 5 个接口 + SseEmitter（骨架见 §10.3） |

### 修改（7 个）

| # | 文件 | 改动 |
|---|------|------|
| 10 | `backend/src/main/resources/application.yml` | 末尾追加 deepseek 配置块（§7.2） |
| 11 | `docker-compose.yml` | backend environment 追加 `DEEPSEEK_API_KEY` 透传 |
| 12 | `config/WebConfig.java` | loginInterceptor 追加拦截 `"/api/assistant/**"` |
| 13 | `frontend/src/router.js` | 追加 `/assistant` 路由 + 守卫加 `to.path.startsWith('/assistant')` 需登录 |
| 14 | `frontend/src/api.js` | 追加 4 个会话管理方法；聊天另导出 `chatStream()`（原生 fetch 实现） |
| 15 | `frontend/src/App.vue` | 导航栏加「AI 助手」链接 |
| 16 | `frontend/package.json` | 新增依赖 `markdown-it` |

### 前端新增（1 个）

| # | 文件 | 要点 |
|---|------|------|
| 17 | `frontend/src/views/AssistantChat.vue` | 聊天主页：会话列表 + 消息区 + fetch 流式解析 + markdown-it 渲染 + 打字机 |

---

## 10. 关键实现骨架（代码级审查材料）

### 10.1 DeepSeekService —— 流式调用

```java
@Service
public class DeepSeekService {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(props.getConnectTimeoutSeconds())).build();
    private static final Pattern DATA_PREFIX = Pattern.compile("^data:\\s*");

    /**
     * 流式对话：delta 通过回调吐出，返回聚合全文
     * @param messages [{role, content}...]（已含 system 与裁剪后历史）
     */
    public String chatStream(List<Map<String, String>> messages, double temperature,
                             Consumer<String> onDelta) throws Exception {
        if (!StringUtils.hasText(props.getApiKey())) throw new BizException("AI 服务未配置");
        Map<String, Object> body = Map.of(
                "model", props.getModel(), "messages", messages,
                "stream", true, "temperature", temperature, "max_tokens", 2048);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(props.getReadTimeoutSeconds()))
                .header("Authorization", "Bearer " + props.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body))).build();

        StringBuilder full = new StringBuilder();
        HttpResponse<Stream<String>> resp = client.send(req, HttpResponse.BodyHandlers.ofLines());
        resp.body().forEach(line -> {
            if (!line.startsWith("data:")) return;
            String payload = DATA_PREFIX.matcher(line).replaceFirst("");
            if ("[DONE]".equals(payload)) return;
            String delta = om.readTree(payload).path("choices").path(0)
                             .path("delta").path("content").asText("");
            if (!delta.isEmpty()) { full.append(delta); onDelta.accept(delta); }
        });
        return full.toString();
    }
}
```

要点：`BodyHandlers.ofLines()` 天然按行读流；OpenAI SSE 格式逐行解析，`[DONE]` 结束。

### 10.2 AssistantContextService —— 上下文组装

```java
@Service
public class AssistantContextService {
    /** 意图分流：命中关键词 → 行程规划 */
    public boolean isPlanning(String q) {
        return q.matches(".*(行程|路线|几天|攻略|规划|日程).*");
    }

    /** 检索景点：问题含省/市名则查该地，否则全库 rating Top20 */
    public List<Attraction> search(String question) {
        String hitProvince = provinceMapper.selectList(null).stream()
                .map(Province::getName).filter(question::contains).findFirst().orElse(null);
        // ... 城市同理；有命中按 province_id/city_id 过滤，否则全库
        // qw.eq("status",1).orderByDesc("rating").last("limit 20")
    }

    /** 组装最终 messages：system + 最近10条历史 + 本次user消息（含景点资料注入） */
    public List<Map<String, String>> buildMessages(Long sessionId, String question, boolean planning) { ... }
}
```

### 10.3 AssistantController —— SSE 端点

```java
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @PostMapping("/chat")
    public SseEmitter chat(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long uid = (Long) req.getAttribute("uid");          // LoginInterceptor 注入
        Long sessionId = Long.valueOf(body.get("sessionId").toString());
        String message = body.get("message").toString().trim();
        checkLength(message); checkOwner(uid, sessionId);   // 2000字上限 + 归属校验

        SseEmitter emitter = new SseEmitter(180_000L);
        chatMessageMapper.insert(new ChatMessage(sessionId, "user", message)); // 先落库

        pool.execute(() -> {
            try {
                boolean planning = ctx.isPlanning(message);
                List<Map<String, String>> msgs = ctx.buildMessages(sessionId, message, planning);
                String full = deepSeek.chatStream(msgs, planning ? 0.7 : 0.5,
                        delta -> send(emitter, "delta", Map.of("content", delta)));
                ChatMessage saved = chatMessageMapper.insert(
                        new ChatMessage(sessionId, "assistant", full));
                send(emitter, "done", Map.of("messageId", saved.getId()));
            } catch (Exception e) {
                send(emitter, "error", Map.of("message", friendly(e)));
            } finally { emitter.complete(); }
        });
        return emitter;
    }
}
```

要点：聊天在独立线程池执行（SSE 长连接不占请求线程）；异常统一走 `event: error`；流结束聚合全文落库。

### 10.4 前端流式解析（api.js 新增 chatStream）

```js
export function chatStream(sessionId, message, { onDelta, onDone, onError }) {
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  return fetch('/api/assistant/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + user.token },
    body: JSON.stringify({ sessionId, message })
  }).then(async (resp) => {
    const reader = resp.body.getReader(); const decoder = new TextDecoder(); let buf = ''
    while (true) {
      const { done, value } = await reader.read(); if (done) break
      buf += decoder.decode(value, { stream: true })
      const events = buf.split('\n\n'); buf = events.pop()          // 半包留到下一轮
      for (const ev of events) {
        const name = (ev.match(/event: (\w+)/) || [])[1]
        const data = JSON.parse((ev.match(/data: (.*)/) || [])[1] || '{}')
        if (name === 'delta') onDelta(data.content)
        else if (name === 'done') onDone(data.messageId)
        else if (name === 'error') onError(data.message)
      }
    }
  })
}
```

要点：EventSource 不支持 POST，故用 fetch + ReadableStream；按 `\n\n` 切分事件、半包缓冲；delta 直接追加到当前 AI 消息 → 打字机效果；AI 回复用 `markdown-it` 渲染。

---

## 11. 开发计划（4 个里程碑）

| 阶段 | 内容 | 验收 |
|------|------|------|
| M1 数据库+配置 | `03-assistant.sql`、application.yml、docker-compose、.env(.gitignore) | 表创建成功；key 注入生效 |
| M2 后端 | 实体/Mapper → DeepSeekService → ContextService → Controller → WebConfig | curl 调 SSE 接口看到 delta 流 |
| M3 前端 | AssistantChat.vue + 路由 + api.js + App.vue + markdown-it | 打字机效果、会话增删查、历史回看 |
| M4 集成 | `docker compose build backend && up -d backend`、手动执行 SQL、端到端联调 | 规划/问答双模式各跑通一条链路 |

---

## 12. 答辩亮点清单

1. **SSE 全链路流式**：DeepSeek 流 → HttpClient ofLines 解析 → SseEmitter 转发 → fetch ReadableStream 打字机
2. **轻量 RAG**：数据库检索 + 结构化上下文注入，答得出"为什么不用向量库"
3. **提示词工程**：System Prompt 强约束防幻觉
4. **会话持久化**：多轮对话完整数据链路
5. **零依赖增重**：Maven 零新增、前端仅 markdown-it，靠 JDK/Spring 自带能力完成 LLM 集成

---

## 13. 风险与对策

| 风险 | 对策 |
|------|------|
| 容器内访问 DeepSeek 需外网 | 本地开发无碍；断网演示用录屏兜底 |
| 已有数据卷不执行新 init 脚本 | M4 手动执行 SQL（§6 注释） |
| 模型偶发不遵守"只推荐库内景点" | Prompt 强约束；后续可加后校验 |
| token 费用 | max_tokens=2048、历史窗口 10 条、输入 2000 字上限 |

---

## 14. 审查确认清单（请逐项确认）

- [ ] 功能范围：行程规划 + 景点问答，不做游记生成/个性化推荐（后期可加）
- [ ] 入口：仅独立聊天页 `/assistant`，不做悬浮球/详情页侧边栏
- [ ] 文件清单 §9（新增 9 + 修改 7 = 16 个文件）无异议
- [ ] SSE 走 POST + fetch（不用 EventSource），事件协议 §5 无异议
- [ ] SQL 脚本命名 `03-assistant.sql`（v1.0 的 02 笔误已修正）
- [ ] 上下文策略：历史窗口最近 10 条、景点资料 Top20、输入 2000 字上限
- [ ] key 通过 `DEEPSEEK_API_KEY` 环境变量注入，不落盘不进 git
- [ ] Java 17 + 固定 8 线程池处理流式回复（不引虚拟线程/WebFlux）
