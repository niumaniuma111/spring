# tourism-site 源码精读手册

> 适用对象：想逐行看懂这个项目源码的学习者。
> 手册按「正确的阅读顺序」编排，每个文件都有：真实代码片段 → 逐段讲解 → 自测问题。
> 全部代码摘自本项目当前源码，行号可对照原文件。

---

## 0. 全局地图：一次请求的完整旅程

以「登录用户在首页搜索『桂林』」为例，一次请求穿过这些文件：

```
AttractionList.vue          用户点「搜索」→ load()
   ↓ api.attractions(query)
api.js                      请求拦截器自动加 Authorization: Bearer <token>
   ↓ axios GET /api/attractions?keyword=桂林
nginx.conf                  location /api/ → proxy_pass http://backend:8080
   ↓
LoginInterceptor.java       第一道门卫：验 JWT，把 uid 放进 request
   ↓
AttractionController.java   list()：拼 QueryWrapper 条件
   ↓ selectPage()
AttractionMapper.java       MyBatis-Plus 生成 SQL
   ↓
MySQL (attraction / province / city 三张表)
   ↓ 原路返回
Result.ok(data)             统一成 {code:0, msg:"success", data:{list,total,...}}
   ↓
api.js 响应拦截器            code===0 → 直接把 data 吐给页面
   ↓
AttractionList.vue          list.value = data.list → 卡片网格重新渲染
```

**读源码就是沿着这条链反复走。** 每个文件在链上只负责一小段，看懂自己那段就行。

---

# 第一部分 后端源码精读

阅读顺序：启动 → 配置 → 响应格式 → 安全三件套 → 实体 → 数据访问 → 异常 → 业务接口。

## 1. TourismApplication.java — 一切的起点

```java
@SpringBootApplication
public class TourismApplication {
    public static void main(String[] args) {
        SpringApplication.run(TourismApplication.class, args);
    }
}
```

- `@SpringBootApplication` 是三个注解的合成：
  - `@Configuration`：本类是配置类；
  - `@ComponentScan`：扫描 `com.tourism` 包下所有 `@Component/@Service/@RestController`，自动注册成 Bean；
  - `@EnableAutoConfiguration`：按 classpath 自动装配——检测到 `spring-boot-starter-web` 就内嵌 Tomcat，检测到 mysql 驱动就配好数据源。
- 全项目没有一行 `new Controller()`：所有对象由 Spring 容器创建并通过构造器注入（后面每个类的构造器就是证据）。

**自测**：为什么 `AttractionController` 的构造器参数 `AttractionMapper` 不用手动赋值？

## 2. application.yml — 配置文件

```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:mysql}:${DB_PORT:3306}/${DB_NAME:tourism}?...
    username: ${DB_USER:tourism}
    password: ${DB_PASS:tourism123}
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
jwt:
  secret: TourismSiteJwtSecretKey-2026-...
  expire-hours: 72
```

- `${DB_HOST:mysql}` 语法：优先读环境变量 `DB_HOST`，没有就用冒号后的默认值 `mysql`。**这是容器化的关键**——docker-compose 里设了 `DB_HOST: mysql`，本地 IDE 跑就用默认值，一份配置两处通用。
- `map-underscore-to-camel-case: true`：数据库列 `province_id` ↔ Java 属性 `provinceId` 自动映射，实体类才不用写 `@TableField`。
- `jwt.secret` 要求 ≥32 字节，因为 HS256 算法需要；`expire-hours: 72` 即登录 3 天免登。

## 3. Result.java — 全站统一的响应格式

```java
public class Result<T> {
    private int code;      // 0 成功，非 0 失败
    private String msg;
    private T data;

    public static <T> Result<T> ok(T data) { return new Result<>(0, "success", data); }
    public static <T> Result<T> fail(int code, String msg) { return new Result<>(code, msg, null); }
}
```

- 泛型 `T` 让同一个类装任何数据：`Result<List<Province>>`、`Result<Map<String,Object>>`。
- 静态工厂方法 `ok()/fail()` 比构造器可读性好。
- **设计约定**：业务码 `code` 和 HTTP 状态码解耦。本项目连拦截器拒绝时都返回 HTTP 200，靠 body 里的 401/403 让前端统一处理（见第 6 节和 api.js）。

**自测**：前端怎么判断一次请求成没成功？（只看 `body.code === 0`）

## 4. JwtUtil.java — 发令牌和验令牌

```java
public JwtUtil(@Value("${jwt.secret}") String secret, @Value("${jwt.expire-hours}") long expireHours) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expireMs = expireHours * 3600_000L;
}

public String createToken(Long userId, String username, String role) {
    return Jwts.builder()
            .subject(username)
            .claim("uid", userId)
            .claim("role", role)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expireMs))
            .signWith(key)
            .compact();
}

public Claims parse(String token) {
    try {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    } catch (Exception e) {
        return null;          // 任何失败：过期/篡改/格式错，统一返回 null
    }
}
```

- token 里只放三样有用信息：`uid`、`role`、用户名（subject）。**服务端不存 token**，所以这是无状态认证。
- `parse()` 失败返回 null 而不是抛异常——调用方（拦截器）只要判空，代码更干净。
- 注意：JWT 只是「证明你是谁」，权限判断（是不是 ADMIN）在拦截器里做。

## 5. LoginInterceptor / AdminInterceptor — 两道门卫

```java
public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
    String auth = req.getHeader("Authorization");
    Claims claims = (auth != null && auth.startsWith("Bearer "))
            ? jwtUtil.parse(auth.substring(7)) : null;
    if (claims == null) {
        resp.setStatus(200);
        resp.getWriter().write(om.writeValueAsString(Result.fail(401, "请先登录后浏览")));
        return false;                       // 拦下，不再进 Controller
    }
    req.setAttribute("uid", claims.get("uid", Long.class));   // 身份传给下游
    req.setAttribute("username", claims.getSubject());
    return true;
}
```

- 执行时机：Controller 方法**之前**。返回 false = 请求到此为止。
- `Bearer ` 前缀是 HTTP 认证的行业惯例，`substring(7)` 把它去掉再验。
- `req.setAttribute(...)`：拦截器和 Controller 之间传值的官方通道。
- `AdminInterceptor` 逻辑相同，多一步：

```java
if (!"ADMIN".equals(claims.get("role", String.class))) {
    return reject(resp, 403, "无管理员权限");
}
```

## 6. WebConfig.java — 把门卫派到岗位

```java
registry.addInterceptor(adminInterceptor)
        .addPathPatterns("/api/admin/**")
        .excludePathPatterns("/api/admin/auth/login");   // 登录接口必须放行
registry.addInterceptor(loginInterceptor)
        .addPathPatterns("/api/attractions", "/api/attractions/*", "/api/provinces", "/api/cities");
```

- `/api/admin/**`：双星匹配多级路径；`/api/attractions/*`：单星只匹配一级（所以详情 `/api/attractions/5` 也被拦）。
- **为什么登录接口要 exclude**：登录时还没有 token，拦截了就死循环。
- `addCorsMappings` 允许跨域——开发期前端(5173)直连后端(8080)时需要；生产走 Nginx 同源，其实用不上，留着是保险。

## 7. MybatisPlusConfig.java — 一个 Bean 换一个功能

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    return interceptor;
}
```

- 没有这 4 行，`selectPage()` 不会拼 `LIMIT`，会一次查出全表再「假装分页」。
- 学习点：MyBatis-Plus 的分页、乐观锁、防全表更新等都是「拦截器插件」机制。

## 8. entity/ — 实体四件套

```java
@TableName("`user`")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;   // SHA-256 摘要，不是明文
    private String role;       // USER / ADMIN
    private Integer status;    // 1 启用 0 禁用
}
```

- `@TableName` 实体↔表名绑定；`` `user` `` 加反引号是因为 `user` 在 MySQL 里是保留字。
- `@TableId(type = IdType.AUTO)`：主键自增，insert 后 **MyBatis-Plus 自动把数据库生成的 id 回填到对象**（AuthController 注册后直接 `u.getId()` 就有值）。
- `Attraction` 是核心表，字段即产品需求：`level`(5A~1A)、`ticketPrice`、`rating`、`views`、`status`(1 发布/0 下架)。
- `get/set` 手写而不是 Lombok：教学项目为了让你看清「Bean 属性约定」。

## 9. mapper/ — BaseMapper 的威力

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {}   // 空接口 = 全套 CRUD
```

```java
@Mapper
public interface AttractionMapper extends BaseMapper<Attraction> {
    @Update("UPDATE attraction SET views = views + 1 WHERE id = #{id}")
    int incrementViews(Long id);
}
```

- 继承 `BaseMapper<T>` 免费获得 `insert / deleteById / updateById / selectById / selectPage / selectCount / selectList ...`，一条 SQL 都没写。
- 唯一手写的 `incrementViews`：**「读-改-写」不能在 Java 里做**（并发会丢更新），自增必须交给 SQL 一条语句原子完成。
- `@Mapper` 让 MyBatis 生成动态代理实现类并注册进 Spring。

## 10. exception/ — 业务异常体系

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("服务器内部错误", e);
        return Result.fail(500, "服务器内部错误：" + e.getMessage());
    }
}
```

- Controller 里到处是 `throw new BizException("用户名已被注册")`——**业务错误用异常表达**，省掉层层 if-return，代码只写「正常流程」。
- `@RestControllerAdvice` 拦截**所有** Controller 抛出的异常并转成 `Result`；未知异常记日志（排查用）但对用户只说人话。

## 11. AuthController.java — 注册与登录（重点）

```java
public record RegisterDTO(String username, String password, String confirmPassword, String nickname) {}
```

- `record` 是 Java 17 的不可变数据类，一行顶一个 DTO 类。

注册主流程（节选）：

```java
if (dto.username().length() < 3 || dto.username().length() > 20)
    throw new BizException("用户名长度需为 3-20 个字符");
if (!dto.password().equals(dto.confirmPassword()))
    throw new BizException("两次输入的密码不一致");
Long cnt = userMapper.selectCount(new QueryWrapper<User>().eq("username", dto.username()));
if (cnt > 0) throw new BizException("用户名已被注册，请重新输入");
u.setPassword(sha256(dto.password()));
userMapper.insert(u);
```

- 校验全在后端做——**前端校验只是体验，后端校验才是安全**（别人可以绕过页面直接 curl）。
- `QueryWrapper.eq("username", x)` 生成 `WHERE username = ?`，预编译防注入。

登录主流程：

```java
User u = userMapper.selectOne(new QueryWrapper<User>().eq("username", dto.username()));
if (u == null || !u.getPassword().equals(sha256(dto.password())))
    throw new BizException("用户名或密码错误");        // 故意不说哪个错，防枚举
```

- 前台登录 `/auth/login` 只放 `USER` 且 `status==1`；管理员走 `/admin/auth/login` 只放 `ADMIN`——**同一个账号体系，两个入口，角色分流**。
- `buildLoginData` 里 `jwtUtil.createToken(...)` 签发 token，连同用户信息一起返回给前端。

## 12. AttractionController.java — 前台核心（全文级精读）

列表接口的骨架：

```java
QueryWrapper<Attraction> qw = new QueryWrapper<>();
qw.eq("status", 1);                              // 前台只看已发布
if (StringUtils.hasText(keyword)) {
    String kw = keyword.trim();
    List<Long> hitProvinceIds = provinceMapper.selectList(
            new QueryWrapper<Province>().like("name", kw)).stream().map(Province::getId)...;
    List<Long> hitCityIds = cityMapper.selectList(
            new QueryWrapper<City>().like("name", kw)).stream().map(City::getId)...;
    qw.and(w -> {
        w.like("name", kw).or().like("address", kw);
        if (!hitProvinceIds.isEmpty()) w.or().in("province_id", hitProvinceIds);
        if (!hitCityIds.isEmpty())     w.or().in("city_id", hitCityIds);
    });
}
```

- 「搜『桂林』命中龙脊梯田」的实现：**先用关键词去省表/市表里捞 id，再把 id 集合并进景点查询条件**。
- `qw.and(w -> ...)` 嵌套是**精髓**：生成的 SQL 是 `status = 1 AND (name LIKE ? OR address LIKE ? OR ...)`。如果不嵌套，第一个 OR 会把 `status=1` 冲掉，下架景点也会被搜出来。
- 四路命中：景点名 / 详细地址 / 省名 / 市名。

筛选与排序：

```java
if (provinceId != null) qw.eq("province_id", provinceId);
if (cityId != null)     qw.eq("city_id", cityId);
if (StringUtils.hasText(level)) qw.eq("level", level);
if ("rating".equals(sort)) qw.orderByDesc("rating", "views");
else if ("views".equals(sort)) qw.orderByDesc("views", "rating");
else qw.orderByAsc("id");

Page<Attraction> p = attractionMapper.selectPage(new Page<>(page, size), qw);
```

- 条件全部「有才加」，这就是动态查询的 Builder 模式。
- 双字段排序：评分相同时比浏览量，避免并列时乱序。

详情接口：

```java
Attraction a = attractionMapper.selectById(id);
if (a == null || a.getStatus() == null || a.getStatus() != 1)
    throw new BizException("景点不存在或已下架");
attractionMapper.incrementViews(id);      // SQL 原子自增
a.setViews(a.getViews() == null ? 1 : a.getViews() + 1);   // 返回给前端的数 +1
```

`toVO()`：把实体转成带 `provinceName/cityName` 的 Map 返回——前端不想再查省市接口，后端就帮忙拼好。
> 进阶点：`toVO` 对每条记录都查两次库（N+1 问题），51 条数据就是 102 次查询。学完可以自己优化成批量查 + 内存组装。

## 13. admin/ 四个 Controller

**AdminAttractionController** — 增删改查 + 服务端校验：

```java
City city = cityMapper.selectById(a.getCityId());
if (city == null || !city.getProvinceId().equals(a.getProvinceId()))
    throw new BizException("所属城市与省份不匹配");
if (!List.of("5A","4A","3A","2A","1A").contains(a.getLevel()))
    throw new BizException("景点等级只能是 5A/4A/3A/2A/1A");
```

**AdminRegionController** — 删除保护（先查引用再删）：

```java
Long cnt = attractionMapper.selectCount(new QueryWrapper<Attraction>().eq("province_id", id));
if (cnt > 0) throw new BizException("该省份下存在 " + cnt + " 条景点数据，不能删除...");
```

**AdminUserController** — `qw.eq("role","USER")` 管理员不出现在列表里；`u.setPassword(null)` 密码永不出库。

**StatsController** — 纯组装：总数 + 5 个等级各 count 一次 + 每个省 count 一次，喂给前端画图。

---

# 第二部分 前端源码精读

## 1. 骨架三件套：index.html → main.js → App.vue

```js
createApp(App).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
```

- `use(router)`：全站路由能力；`use(ElementPlus, {locale})`：组件库 + 中文文案（分页、日期选择器才会显示中文）。
- `App.vue` 全部内容只有 `<router-view />` + 全局 reset 样式——它只是个壳。

## 2. vite.config.js — 开发期代理

```js
server: {
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true }
  }
}
```

- `npm run dev` 时（5173 端口），`/api` 开头的请求被 Vite 转发到 8080——**开发期没有 Nginx，代理由 Vite 充当**。
- 生产环境这条配置不生效，改由 `nginx.conf` 的 `proxy_pass` 干同样的活。**同一件事，两套环境两个实现**，这是前端工程化的常见模式。

## 3. router.js — 路由表与守卫

```js
const routes = [
  { path: '/', component: () => import('./views/AttractionList.vue') },   // 懒加载
  ...
  { path: '/admin', component: AdminLayout,
    children: [
      { path: 'attractions', component: AttractionManage },
      ...
    ]}
]

router.beforeEach((to) => {
  const needLogin = to.path === '/' || to.path.startsWith('/detail')
  if (needLogin && !getLocalUser()) return '/login'
  if (to.path.startsWith('/admin') && to.path !== '/admin/login') {
    const user = getLocalUser()
    if (!user || user.role !== 'ADMIN') return '/admin/login'
  }
})
```

- `() => import(...)` 路由懒加载：访问到该页才下载对应 js，首屏更快。
- 嵌套路由：`/admin` 是外壳（AdminLayout），子路径渲染在它的 `<router-view/>` 里。
- 守卫返回一个路径字符串 = 重定向。前台守卫查「有没有登录」，后台守卫多查「role 是不是 ADMIN」。
- 注意这是**前端体验层拦截**，真正的防线是后端拦截器（直接 curl 依然会被 401 挡住）。

## 4. api.js — 全站唯一的出口

```js
const api = axios.create({ baseURL: '/api', timeout: 15000 })

api.interceptors.request.use((cfg) => {
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  if (user && user.token) cfg.headers.Authorization = 'Bearer ' + user.token
  return cfg
})

api.interceptors.response.use((resp) => {
  const body = resp.data
  if (body.code !== 0) {
    if (body.code === 401 || body.code === 403) {
      ElMessage.error(body.msg || '请先登录')
      if (path.startsWith('/admin')) router.push('/admin/login')
      else if (body.code === 401 && path !== '/login') router.push('/login')
    } else {
      ElMessage.error(body.msg || '请求失败')
    }
    return Promise.reject(new Error(body.msg))
  }
  return body.data          // ★ 直接剥掉 Result 外壳，页面拿到的就是 data
}, (err) => { ElMessage.error('网络异常，请稍后重试'); return Promise.reject(err) })
```

- **请求拦截器**：每次请求自动从 localStorage 取 token 拼头——登录态注入只写这一处。
- **响应拦截器**：错误提示、跳转、剥壳三件事全站统一。页面里 `await api.attractions(query)` 直接拿到业务数据，感知不到 Result 的存在。
- 文件尾部导出的对象（`register/login/attractions/.../stats`）就是后端所有接口的镜像清单，**把它当接口文档读**。

## 5. AttractionList.vue — 首页（重点）

```js
const query = reactive({ page: 1, size: 6, keyword: '', provinceId: null, cityId: null, level: '', sort: '' })

async function load() {
  loading.value = true
  try {
    const data = await api.attractions(query)
    list.value = data.list
    total.value = Number(data.total)
  } finally { loading.value = false }
}
```

- **设计模式：所有筛选控件只改 `query`，唯一的数据出口是 `load()`**。搜索框回车、等级切换、排序切换、分页……全部殊途同归调 `load()`，状态永远只有一份。
- `query` 直接整体传给后端（空值后端自动忽略），前后端参数一一对应。

省市联动：

```js
async function onProvinceChange(pid) {
  query.cityId = null                              // 换省必须清空市，否则会查出脏组合
  if (pid) cities.value = await api.cities(pid)
  else cities.value = []
  query.page = 1                                   // 新筛选从第 1 页看起
  load()
}
```

- `reset()` 用 `Object.assign(query, {...})` 整体重置——reactive 对象不能直接换引用。

## 6. AttractionDetail.vue — 详情页

```js
onMounted(async () => {
  a.value = await api.attractionDetail(route.params.id)   // 从 URL /detail/:id 取参
  ...
})
```

- `route.params.id` 对应路由 `/detail/:id`；刷新页面也能直接进入（Nginx `try_files` 兜底回 index.html，见第三部分）。
- 每次进详情后端浏览量 +1，页面显示的是 +1 后的值。

## 7. Login.vue / Register.vue

```js
async function submit() {
  await formRef.value.validate()            // 校验不过会 throw，直接中断
  const data = await api.login(form)
  localStorage.setItem('user', JSON.stringify(data))   // ★ token 的家
  router.push('/')
}
```

- Element Plus 表单：`rules` 声明式校验；`validate()` 返回 Promise，失败自动中断提交。
- **`localStorage.user` 是全站登录态的唯一来源**：api.js 读它带 token，router.js 读它做守卫，页面读它显示昵称。删掉它 = 退出登录（三个页面里都有 `logout()` 干这事）。
- Register 的自定义校验器和失焦查重：

```js
const validateConfirm = (_rule, value, callback) => {
  if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}
async function checkUsername() {
  if (!form.username || form.username.length < 3) return
  const ok = await api.checkUsername(form.username)   // 后端 GET /auth/check-username
  if (!ok) ElMessage.warning('该用户名已被注册，请重新输入')
}
```

## 8. admin 系列页面

- **AdminLogin.vue**：与 Login.vue 几乎一样，区别只在 `api.adminLogin`（走 `/admin/auth/login`，后端校验 ADMIN 角色）。
- **AdminLayout.vue**：后台外壳。`el-menu` 开 `router` 属性后，`index="/admin/attractions"` 点击即路由跳转；中间 `<router-view/>` 渲染四个管理页。
- **AttractionManage.vue**（后台最复杂的一页）：

```js
const dialog = reactive({ visible: false, isEdit: false })   // 一个对话框复用新增和编辑

async function openEdit(row) {
  dialog.isEdit = true
  const detail = await api.adminAttraction(row.id)     // 先拉详情再回填表单
  Object.assign(form, detail)
  formCities.value = await api.cities(detail.provinceId)  // 编辑时城市下拉要先备好
  dialog.visible = true
}

async function save() {
  await formRef.value.validate()
  if (dialog.isEdit) await api.updateAttraction(form.id, form)
  else await api.addAttraction(form)
  dialog.visible = false
  load()                                               // 改完刷新列表
}
```

  - `isEdit` 一个标志让「新增/编辑」共用一个对话框、一个 save()；删除用 `ElMessageBox.confirm` 二次确认。
- **RegionManage.vue**：左侧省列表、右侧该省城市列表；`dialog.type` 区分当前操作的是省还是市，save() 里四合一路由到不同 api。
- **UserManage.vue**：`toggle(row, status)` 确认后调 `changeUserStatus`，禁用/启用一个开关搞定。
- **StatsView.vue** — ECharts 标准三步 + 两个生命周期细节：

```js
onMounted(async () => {
  stats.value = await api.stats()
  render()                                   // init + setOption 画饼图和柱状图
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)   // ★ 解绑，防止内存泄漏
  levelInstance?.dispose()                          // ★ 销毁实例
})
```

---

# 第三部分 构建与部署源码

## docker-compose.yml

```yaml
mysql:
  ports: ["33061:3306"]                       # 宿主机 33061 → 容器 3306，避开本机 3306
  volumes:
    - mysql-data:/var/lib/mysql               # ① 数据持久化：删容器数据还在
    - ./mysql/init:/docker-entrypoint-initdb.d:ro   # ② 首次启动自动执行初始化 SQL
  healthcheck:
    test: ["CMD", "mysqladmin", "ping", ...]
backend:
  depends_on:
    mysql:
      condition: service_healthy              # 等 mysql 真正可用才启动 backend
```

- ② 是「免手工建库」的关键：目录里的 SQL 只在**数据卷为空（首次启动）**时执行。
- `service_healthy`：`depends_on` 默认只等容器启动不等服务就绪，healthcheck + condition 才是可靠的启动顺序。

## backend/Dockerfile — 两阶段构建

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build          # 阶段一：Maven + JDK 编译
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline -q || true
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests -q

FROM eclipse-temurin:17-jre-jammy                   # 阶段二：只带 JRE 的瘦镜像
COPY --from=build /build/target/*.jar app.jar
```

- 依赖先 `go-offline` 再拷源码：源码变了依赖层仍走缓存，构建快。
- 运行镜像用 JRE 不用 JDK：体积减半，也更安全。

## frontend/Dockerfile + nginx.conf

```dockerfile
FROM node:22-alpine AS build
RUN npm config set registry https://registry.npmmirror.com && npm install
COPY . .
RUN npm run build
FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html   # 只带走静态产物
```

```nginx
location / {
    try_files $uri $uri/ /index.html;   # SPA 刷新不 404：找不到文件回 index.html 交给路由
}
location /api/ {
    proxy_pass http://backend:8080/api/;   # 容器名即域名（compose 内置 DNS）
}
```

- `backend` 不是 IP，是 compose 里服务名——容器互相访问靠 Docker 内置 DNS。
- 前后端同源（都从 80 出去），所以生产环境没有跨域问题。

---

# 第四部分 学完自测 20 问

1. `@SpringBootApplication` 包含哪三个注解，各干什么？
2. `${DB_HOST:mysql}` 什么含义？为什么容器内外都能用同一份 yml？
3. `Result` 为什么要有 `code` 业务码，而不是只用 HTTP 状态码？
4. JWT 里存了什么？服务端为什么不需要存 token？
5. 两个拦截器分别在拦什么路径？为什么 `/api/admin/auth/login` 要排除？
6. 拦截器怎么把用户身份传给 Controller？
7. 删掉 `MybatisPlusConfig` 会发生什么？
8. `user` 表名为什么要加反引号？
9. 为什么 `incrementViews` 必须用 SQL 自增而不是 Java 里 `views+1` 再 update？
10. `BizException` + 全局处理器比 if-return 好在哪？
11. 注册接口做了哪些后端校验？为什么前端校验了还要后端校验？
12. 登录失败为什么统一说「用户名或密码错误」？
13. 搜「桂林」命中「龙脊梯田」的 SQL 逻辑是怎样的？`qw.and(w -> ...)` 不嵌套会怎样？
14. 详情页浏览量 +1 的完整链路经过哪些文件？
15. api.js 的两个拦截器各干两件什么事？页面为什么感知不到 Result 外壳？
16. token 存在哪？退出登录实际发生了什么？
17. router 守卫和后端拦截器是什么关系？只留一个行不行？
18. 首页所有筛选操作是怎么共用一个 `load()` 的？换省时为什么清空市？
19. compose 里 `healthcheck` + `condition: service_healthy` 解决什么问题？
20. Nginx `try_files ... /index.html` 解决什么问题？

---

# 建议节奏（总 8~10 天）

| 阶段 | 内容 | 天数 |
|---|---|---|
| 1 | 第 1–7 节（骨架 + 安全三件套） | 2 |
| 2 | 第 8–10 节（实体/Mapper/异常） | 1 |
| 3 | 第 11–12 节（Auth + Attraction 精读） | 2 |
| 4 | 第 13 节 + 前端 1–4 节 | 1.5 |
| 5 | 前端 5–8 节 | 1.5 |
| 6 | 第三部分 + 20 问自测 | 1 |

**读法口诀：每次只沿一条请求链读；读不懂的行先记下来继续走，走完一条链回头再消灭存疑清单。**
