# 校园自习室预约管理系统（无视觉监控版）

基于 Spring Boot + Vue 的校园自习室座位预约管理系统（本副本**不含实时视觉监控模块**）。

> 本目录为原系统的一份干净副本：已移除视觉识别服务（vision-service）、实时监控页面与管理端视觉入口、后端视觉接口与相关业务逻辑。保留完整的预约业务闭环（离散时段 / 弹性预约 / 签到 / 自动取消 / 违约治理 / 审计）。

---

## 一、功能总览

| 角色 | 功能 |
|---|---|
| 学生 | 注册/登录、浏览自习室、按日期+时段查看座位图、预约/取消/签到、查看我的预约与违约记录 |
| 管理员 | 数据看板（趋势/时段热度/上座率）、用户启停、自习室/座位/时段维护、预约管理与强制取消、操作审计 |

核心机制：

- **两种预约模式**：
  - 离散时段：每天 08:00-22:00 按 30 分钟切分时段（`slot` 表），可多选连续时段批量预约，预约精确到"座位 × 日期 × 时段"；
  - 弹性预约（试点室）：自选开始时间与时长（最短 30 分钟，默认最长 8 小时），区间精确到半小时。
- **防重叠双唯一索引**：`reservation` 表通过生成列条件唯一索引（仅"进行中"状态占坑）同时保证**同座位同时段不重复**、**同用户同时段不超 1 座**；取消/违约/完成后自动释放，可重新预约。
- **违约闭环**：超时未签到（开始后 30 分钟）判违约；违约满 3 次禁约 7 天；弹性预约开始后 60 分钟未签到自动取消释放（不计违约）。
- **审计**：管理员强制取消、启停用户、改座位等关键操作全部写入 `admin_audit_log`。

## 二、技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3 + Vite + Element Plus + Pinia + Vue Router + Axios + ECharts |
| 后端 | Spring Boot 3.3.5 + MyBatis-Plus 3.5.7 + JWT(jjwt 0.12.6) + Spring Security Crypto(BCrypt) |
| 数据库 | MySQL 8.0+（utf8mb4） |

## 三、目录结构

```
自习室预约管理系统-无监控版/
├── docs/
│   └── 设计方案.md            # 系统设计方案（含需求、架构、库表、接口、文献）
├── database/
│   └── init.sql               # 建库建表 + 初始化数据（2 自习室 / 50 座位 / 56 时段）
├── backend/                   # Spring Boot 后端
│   ├── pom.xml
│   ├── target/studyroom-backend-1.0.0.jar   # 已编译产物（无视觉模块）
│   └── src/main/
│       ├── java/com/campus/studyroom/       # common/security/config/entity/mapper/dto/vo/service/controller/task
│       └── resources/application.yml
├── frontend/                  # Vue 3 前端
│   └── src/{api,store,router,views}
├── start_all.bat              # 一键启动（后端 + 前端两个窗口）
├── backend_start.bat          # 单独启动后端
├── frontend_start.bat         # 单独启动前端
└── ai-bridge/                 # 开发期 Claude(DeepSeek) 辅助评审记录（不参与运行）
```

## 四、环境要求

| 软件 | 版本 |
|---|---|
| JDK | 17+（本项目在 JDK 23 编译运行通过） |
| Maven | 3.6+ |
| Node.js | 18+（实测 22） |
| MySQL | 8.0+ |

> 本版无需 Python 环境（视觉服务已移除）。

## 五、快速启动

### 0. 初始化数据库

```bash
mysql -uroot -p < database/init.sql
```

> 数据库连接在 `backend/src/main/resources/application.yml` 配置，**密码无默认值**，启动前必须通过环境变量 `MYSQL_PASSWORD` 注入（见下）。

### 1. 启动后端

```bash
cd backend
# 先注入三个环境变量（值按你的实际环境填写，不入代码）
export MYSQL_PASSWORD=你的MySQL密码
export JWT_SECRET=一段随机长字符串（生产环境必改）
export STUDYROOM_ADMIN_PASSWORD=初始管理员密码
mvn spring-boot:run
```

> Windows PowerShell 下用 `$env:MYSQL_PASSWORD="你的MySQL密码"` 形式设置；三个变量**没有默认值**，缺失会导致启动失败（这是刻意的：凭据只经环境变量注入，代码中零密钥）。

- 首次启动自动创建管理员账号：**admin**（密码取 `STUDYROOM_ADMIN_PASSWORD` 环境变量）
- 服务监听 `http://localhost:8080`

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

- 访问 `http://localhost:5173`
- 学生账号：注册页自建；管理员：admin（密码为启动时注入的 `STUDYROOM_ADMIN_PASSWORD`）

### 3. 一键启动（Windows）

双击 `start_all.bat`：自动打开后端 + 前端两个窗口，约 20 秒后访问 `http://localhost:5173`。

## 六、换机迁移（新电脑从零部署）

适用：答辩前换设备、换开发机。**不用拷贝整个文件夹**，从 GitHub 拉代码 + 配环境即可。

**6.1 软件清单**

| 软件 | 版本要求 | 安装要点 |
|---|---|---|
| JDK | 17+ | 装完配置 `JAVA_HOME` 环境变量 |
| Maven | 3.6+ | 用 IDEA 内置 Maven 最省事；独立安装见 6.3 |
| Node.js | 18+ | 装完 `node -v` 验证 |
| MySQL | 8.0+ | 可用 phpStudy / XAMPP 集成包或官方安装包 |
| Git | 任意 | 拉代码用 |

**6.2 拉代码**

```bash
git clone https://github.com/guihuafentou/studyroom-reservation-system-novision.git
cd studyroom-reservation-system-novision
```

**6.3 独立安装 Maven（不用 IDEA 时）**

1. 官网下载 `apache-maven-3.9.x` 二进制 zip 解压到任意目录；
2. 编辑解压目录下 `conf/settings.xml`，在 `<mirrors>` 内加阿里云镜像（国内拉依赖快，实测必需）：
   ```xml
   <mirror>
     <id>aliyun</id>
     <mirrorOf>central</mirrorOf>
     <url>https://maven.aliyun.com/repository/central</url>
   </mirror>
   ```
3. 本项目 `pom.xml` 已内置 JDK 23 所需的编译配置（maven-compiler-plugin 3.14.0 + lombok 注解处理），新电脑**无需手动改任何配置**，直接 `mvn` 即可。

**6.4 初始化数据库**

```bash
mysql -uroot -p < database/init.sql
```

- 脚本自动创建 `studyroom` 库（utf8mb4）+ 表 + 2 自习室 / 50 座位 / 56 时段；
- **注意**：若 MySQL 来自 phpStudy 等集成包且未注册为 Windows 服务，需先手动启动，例如：
  ```powershell
  Start-Process "D:\phpstudy_pro\Extensions\MySQL8.0.12\bin\mysqld.exe"
  ```
  路径以你本机实际安装位置为准，启动成功后再执行导入命令。

**6.5 设置环境变量（关键：代码无默认值）**

Windows PowerShell（三个缺一都会启动失败，这是刻意的——凭据只经环境变量注入，代码零密钥）：

```powershell
$env:MYSQL_PASSWORD="你的MySQL密码"          # 必须与 6.4 中 root 的密码一致
$env:JWT_SECRET="不少于32位的随机长字符串"    # 生产/答辩环境务必用随机值
$env:STUDYROOM_ADMIN_PASSWORD="首次启动的管理员初始密码"
```

**6.6 启动后端**

```bash
cd backend
mvn spring-boot:run
```

- 或先打包再跑：`mvn -DskipTests package` → `java -jar target\studyroom-backend-1.0.0.jar`；
- 首次启动自动创建管理员 **admin**（密码 = `STUDYROOM_ADMIN_PASSWORD`），监听 `http://localhost:8080`。

**6.7 启动前端**

```bash
cd frontend
npm install
npm run dev
```

- 访问 `http://localhost:5173`。

**6.8 换机必做的一件事（最容易踩坑）**

- **重新注册测试账号**：`init.sql` 只含基础数据，**不含测试学生和预约记录**（新库是空的）。演示前在前端注册页自建学生账号。

**6.9 验证清单（全部通过 = 迁移成功）**

- [ ] 注册学生账号 → 登录成功
- [ ] 自习室列表可见 → 选择日期+时段 → 预约座位成功
- [ ] "我的预约"可见 → 签到/取消正常
- [ ] 管理员 admin 登录 → 数据看板有数据

## 七、默认账号与配置

| 项 | 值 | 修改位置 |
|---|---|---|
| 管理员 | admin / 由环境变量注入（无默认值） | 启动前设置 `STUDYROOM_ADMIN_PASSWORD` |
| MySQL | root / 由环境变量注入（无默认值） | 启动前设置 `MYSQL_PASSWORD` |
| JWT 密钥 | 由环境变量注入（无默认值） | 启动前设置 `JWT_SECRET`（生产必改） |

## 八、主要接口

| 模块 | 接口 | 说明 |
|---|---|---|
| 认证 | POST `/api/auth/register` `/login` GET `/api/auth/me` | 注册 / 登录(JWT) / 当前用户 |
| 自习室 | GET `/api/rooms` `/api/rooms/{id}` `/api/rooms/{id}/slots` `/api/rooms/{id}/seats?date=&slotId=` | 列表 / 详情 / 时段 / 座位状态 |
| 预约 | POST `/api/reservations` GET `/api/reservations/mine` PUT `/api/reservations/{id}/cancel` `/sign` | 预约 / 我的 / 取消 / 签到 |
| 管理 | `/api/admin/**` | 用户、自习室、座位、时段、预约、审计、统计 |

完整接口定义与请求/响应示例见 `docs/设计方案.md` §6。

## 九、参考文献

设计文档引用的核心文献（均已联网核实，真实可查）：

1. M. Jones, J. Bradley, N. Sakimura, "JSON Web Token (JWT)," RFC 7519, IETF, 2015, DOI: 10.17487/RFC7519 —— 登录令牌标准
2. 郭慧敏 等, 《基于SpringBoot+微信小程序的自习室座位预约系统》, 《电脑编程技巧与维护》2026(5):38-40,65 —— 同类系统设计参考
3. 《手机端自习室预约系统的设计与实现》, 万方, 2024 —— 同类系统设计参考
4. 霍春阳, 《Vue.js设计与实现》, 人民邮电出版社, 2022, ISBN 978-7-115-58386-4 —— 前端响应式原理
5. Spring Boot / Vue.js / MyBatis-Plus 官方在线文档

## 十、备注

- 本副本由原系统（含视觉识别）剥离实时监控模块而来，业务逻辑（弹性预约、超时自动取消、违约闭环、审计）与原版完全一致。
- 本版**无视觉监控**：管理员后台不含"视觉监控"页；学生选座页的"刷新实时状态"已改为普通"刷新"（重新查询预约状态）；后端定时任务在"超时未签到"时按规则直接判违约，无视觉兜底与离座释放。
- 若后续需要恢复视觉识别，请使用原项目（含 vision-service 的完整版），或按 GitHub 仓库 `guihuafentou/studyroom-reservation-system`（main 分支）中的 `vision-service/` 目录补回。
- 生产部署建议：关闭 Swagger（未引入）、更换 JWT 密钥（环境变量 `JWT_SECRET`）、管理员初始密码用环境变量 `STUDYROOM_ADMIN_PASSWORD` 覆盖、数据库独立账号。
