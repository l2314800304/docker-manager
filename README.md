# Docker Manager

> Docker Compose 运行监控系统 —— 基于 DDD + 六边形架构的实时可视化管理平台

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3.5-green)
![Architecture](https://img.shields.io/badge/Architecture-DDD%20%2B%20Hexagonal-orange)
![License](https://img.shields.io/badge/License-MIT-yellow)

---

## 目录

- [项目介绍](#项目介绍)
- [架构设计](#架构设计)
- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [部署说明](#部署说明)
- [配置说明](#配置说明)
- [API 文档](#api-文档)
- [使用说明](#使用说明)
- [构建与开发](#构建与开发)
- [常见问题](#常见问题)

---

## 项目介绍

Docker Manager 是一个面向 Docker Compose 用户的 **Web 监控系统**。它通过 docker-java 客户端直连宿主机 Docker daemon，自动发现所有 docker-compose 项目及其服务，提供**实时状态监控**、**日志流查看**、**资源使用统计**、**文件系统浏览**、**镜像版本更新**等全方位管理能力。

系统采用 **DDD（领域驱动设计）+ 六边形架构（Ports & Adapters）** 进行分层，通过 Maven 多模块构建，前端 Vue3 SPA 打包后嵌入 Spring Boot JAR，最终产出**单个可执行 JAR 文件**，开箱即用。

---

## 架构设计

### 六边形架构（Ports & Adapters）

```
┌─────────────────────────────────────────────────────────────────┐
│                      Starter (启动层)                            │
│   Spring Boot 入口 + WebConfig + application.yml + 前端资源       │
│   依赖: infrastructure                                          │
├─────────────────────────────────────────────────────────────────┤
│                Infrastructure (基础设施层 / Adapters)             │
│   ┌─────────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐  │
│   │ Docker 适配器│ │JPA 持久化 │ │ REST API │ │ Security 适配器│  │
│   │ (Bridge类)   │ │(Repo+Mapper)│(Controller)│ (JWT/Filter)  │  │
│   └─────────────┘ └──────────┘ └──────────┘ └───────────────┘  │
│   依赖: application, domain, Spring Boot, docker-java, jjwt     │
├─────────────────────────────────────────────────────────────────┤
│                Application (应用层 / Use Cases)                  │
│   ┌───────────────────────┐  ┌──────────────────────────────┐   │
│   │  应用服务 (AppService)  │  │  出站端口 (Outbound Port)     │   │
│   │  ContainerAppService   │  │  DockerAdapterPort           │   │
│   │  AuthAppService        │  │  UserRepositoryPort          │   │
│   └───────────────────────┘  │  AuditRepositoryPort ...     │   │
│                               └──────────────────────────────┘   │
│   依赖: domain, spring-context                                   │
├─────────────────────────────────────────────────────────────────┤
│                   Domain (领域层 / Core)                         │
│   ┌──────────┐ ┌──────────┐ ┌──────┐ ┌─────────────────────┐   │
│   │ 实体      │ │ DTO      │ │ 枚举  │ │ 入站端口 (Inbound)   │   │
│   │ User      │ │Container │ │State │ │ DockerOperationPort │   │
│   │ AuditLog  │ │InfoDTO   │ │Action│ │ AuthenticationPort  │   │
│   │StatusRec. │ │StatsDTO..│ │      │ │                     │   │
│   └──────────┘ └──────────┘ └──────┘ └─────────────────────┘   │
│   依赖: 无 (纯 Java, 仅 Lombok + JPA注解API)                     │
└─────────────────────────────────────────────────────────────────┘
```

### 依赖方向（依赖倒置原则）

```
Starter → Infrastructure → Application → Domain
 (组装)      (适配器实现)     (编排+端口)    (核心模型)
```

- **Domain** 不依赖任何其他模块（零框架依赖）
- **Application** 仅依赖 Domain + spring-context
- **Infrastructure** 依赖 Application + Domain + 所有第三方框架
- **Infrastructure** 实现 Application 定义的出站端口接口（依赖倒置）
- **Starter** 是最终打包入口，依赖 Infrastructure（传递依赖所有层）

### 端口（Port）设计

| 类型 | 位置 | 接口 | 职责 |
|------|------|------|------|
| **入站端口** | `domain.port.inbound` | `DockerOperationPort` | 容器/项目/Stats/文件系统/镜像/健康检查 |
| | | `AuthenticationPort` | 登录/注册/用户管理 |
| | | `HostOperationPort` | 宿主机管理/指标采集/连接测试 |
| | | `AlertManagementPort` | 告警规则/告警记录/通知测试 |
| **出站端口** | `application.port.outbound` | `DockerAdapterPort` | Docker API 操作契约 |
| | | `UserRepositoryPort` | 用户持久化契约 |
| | | `AuditRepositoryPort` | 审计日志持久化契约 |
| | | `StatusRecordRepositoryPort` | 状态记录持久化契约 |
| | | `HostRepositoryPort` | 宿主机持久化契约 |
| | | `AlertRepositoryPort` | 告警规则/记录持久化契约 |
| | | `HostMetricsPort` | 宿主机指标采集契约 |
| | | `NotificationPort` | 告警通知发送契约（钉钉等） |
| | | `JwtPort` | JWT 令牌生成/验证契约 |
| | | `PasswordEncoderPort` | 密码编码契约 |

---

## 功能特性

| 功能模块 | 说明 |
|---------|------|
| **Dashboard 总览** | 统计卡片（动画数字）、项目列表（健康度进度条）、资源使用概览、操作日志 |
| **多宿主机支持** | 同时对接多个 Docker 宿主机，每台独立 DockerClient，支持动态添加/删除/启用/禁用 |
| **宿主机监控** | CPU 使用率、内存占用、磁盘占用、容器统计、Docker 版本等实时指标采集 |
| **磁盘分区监控** | 采集宿主机所有物理分区的存储空间占用率（设备名、挂载点、总量/已用/可用），通过 df 命令精确采集 |
| **Compose 项目发现** | 自动扫描宿主机所有 docker-compose 项目，按 label 分组聚合 |
| **容器管理** | 启动/停止/重启容器，查看详细信息（镜像、端口、Digests） |
| **实时日志** | WebSocket 推送容器日志流，支持搜索过滤、自动滚动、stderr/stdout 区分 |
| **资源监控** | 实时 CPU/内存/网络/磁盘 IO 统计，60 秒历史趋势柱状图 |
| **文件系统浏览** | 浏览容器内文件目录，查看文件内容，支持目录导航 |
| **镜像更新** | 选择新 tag → Docker Hub 查询 → 拉取镜像 → 重启服务，支持失败回滚 |
| **钉钉告警** | 自定义告警规则（CPU/内存/磁盘阈值），定时检查，WebHook 推送，支持加签验证 |
| **告警管理** | 规则 CRUD、冷却期防重复、告警历史记录、通知测试 |
| **用户认证** | JWT 无状态认证，登录/注册/修改密码，Token 自动续期 |
| **用户管理** | 管理员可查看/禁用/删除用户，重置密码 |
| **审计日志** | 记录所有容器操作（重启/停止/启动/更新）的审计历史 |
| **状态追踪** | 记录容器状态变更（RUNNING → STOPPED 等）历史 |
| **健康检查** | Docker 连接状态实时检测，Header 呼吸灯指示 |
| **优雅关闭** | 应用停止时自动关闭所有 WebSocket 连接和 Stats 采集器 |

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|-----|------|------|
| Java | 17 | 运行时 |
| Spring Boot | 3.4.1 | Web 框架 + 自动配置 |
| Spring Security | - | JWT 无状态认证授权 |
| Spring Data JPA | - | 数据持久化（H2） |
| Spring WebSocket | - | 实时日志/Stats 推送 |
| docker-java | 3.4.1 | Docker Engine API 客户端 |
| H2 Database | - | 嵌入式轻量数据库 |
| jjwt | 0.12.6 | JWT Token 生成/验证 (HMAC-SHA256) |
| Jackson | - | JSON 序列化/反序列化 |
| Lombok | - | 样板代码消除 |

### 前端

| 技术 | 版本 | 说明 |
|-----|------|------|
| Vue | 3.5 | 渐进式前端框架 |
| Vite | 8.0 | 下一代构建工具 |
| TypeScript | 6.0 | 类型安全 |
| Element Plus | 2.14 | 企业级 UI 组件库 |
| Pinia | 3.0 | 新一代状态管理 |
| Vue Router | 5.0 | 路由管理（History 模式） |
| Axios | 1.17 | HTTP 客户端（拦截器自动附加 JWT） |

---

## 项目结构

```
docker-manager/                                  # 父 POM (Maven reactor)
├── pom.xml                                      # 父级: modules + dependencyManagement
├── Dockerfile                                   # Docker 多阶段构建
├── build.sh / build.ps1                         # 本地构建脚本
│
├── docker-manager-domain/                       # 📦 领域层 (13 files)
│   └── src/main/java/com/dockermanager/domain/
│       ├── entity/          User, AuditLog, StatusRecord
│       ├── enums/           ContainerState, AuditAction
│       ├── dto/             ContainerInfoDTO, ComposeProjectDTO, ContainerStatsDTO,
│       │                    FileEntryDTO, LogEntryDTO, ImageUpdateRequest
│       └── port/inbound/    DockerOperationPort, AuthenticationPort
│
├── docker-manager-application/                  # 📦 应用层 (8 files)
│   └── src/main/java/com/dockermanager/application/
│       ├── port/outbound/   DockerAdapterPort, UserRepositoryPort, AuditRepositoryPort,
│       │                    StatusRecordRepositoryPort, PasswordEncoderPort, JwtPort
│       └── service/         ContainerAppService, AuthAppService
│
├── docker-manager-infrastructure/               # 📦 基础设施层 (33 files)
│   └── src/main/java/com/dockermanager/infrastructure/
│       ├── adapter/docker/  DockerAdapterImpl + internal/ (DockerClientBridge,
│       │                    ComposeProjectBridge, LogStreamBridge, StatsBridge,
│       │                    FileSystemBridge, LifecycleBridge)
│       ├── adapter/persistence/  JPA Repositories (3) + Adapters (3)
│       ├── adapter/security/     JwtAdapter, PasswordEncoderAdapter,
│       │                         JwtAuthenticationFilter, SecurityConfig
│       ├── adapter/web/          REST Controllers (10) + WebSocket Handlers (2)
│       │                         + GlobalExceptionHandler
│       └── config/               DockerClientConfig, WebSocketConfig,
│                                 ResourceCleanupManager
│
├── docker-manager-starter/                      # 📦 启动层 (2 files → 最终 JAR)
│   └── src/main/
│       ├── java/com/dockermanager/
│       │   ├── DockerManagerApplication.java    # 应用入口 (@SpringBootApplication)
│       │   └── config/WebConfig.java            # CORS + SPA 路由转发
│       └── resources/
│           └── application.yml                  # 应用配置
│
└── frontend/                                    # 📦 Vue3 前端 (11 files)
    ├── src/
    │   ├── api/index.ts                         # API 请求封装 + 拦截器
    │   ├── stores/auth.ts                       # Pinia 认证状态管理
    │   ├── router/index.ts                      # 路由 + 导航守卫
    │   ├── views/                               # 页面视图
    │   │   ├── LoginView.vue                    #   登录/注册页
    │   │   ├── DashboardView.vue                #   仪表盘
    │   │   ├── ProjectListView.vue              #   项目列表
    │   │   ├── ProjectDetailView.vue            #   项目详情
    │   │   ├── ContainerDetailView.vue          #   容器详情 (4 Tab)
    │   │   └── UserManagementView.vue           #   用户管理
    │   └── App.vue                              # 布局框架 (侧边栏+Header)
    └── vite.config.ts                           # Vite 构建配置 (分包策略)
```

---

## 快速开始

### 环境要求

| 工具 | 最低版本 | 说明 |
|-----|---------|------|
| Java | 17+ | 后端运行时 |
| Maven | 3.8+ | 多模块构建 |
| Node.js | 20.19+ 或 22.12+ | 前端构建 |
| Docker | 20+ | 运行时（宿主机需运行 Docker daemon） |

### 本地开发

**1. 克隆项目**

```bash
git clone <repo-url>
cd docker-manager
```

**2. 前端开发（热更新）**

```bash
cd frontend
npm install
npm run dev
# 前端运行在 http://localhost:5173
# Vite 自动代理 /api 和 /ws 请求到后端 localhost:8080
```

**3. 后端开发**

```bash
# 确保 Docker daemon 正在运行
mvn clean compile -pl docker-manager-starter -am
mvn spring-boot:run -pl docker-manager-starter
# 后端运行在 http://localhost:8080
```

> **说明**：`-pl docker-manager-starter -am` 表示只构建 starter 模块及其依赖模块。

### 构建生产包

**方式一：使用构建脚本（推荐）**

```bash
# Linux / macOS
chmod +x build.sh && ./build.sh

# Windows PowerShell
.\build.ps1
```

**方式二：手动构建**

```bash
# 1. 构建前端
cd frontend && npm ci && npm run build-only && cd ..

# 2. 拷贝到 starter 模块的 static 目录
cp -r frontend/dist/ docker-manager-starter/src/main/resources/static/

# 3. Maven 多模块打包
mvn clean package -DskipTests -B
```

**产出文件**：`docker-manager-starter/target/docker-manager-starter-1.0.0-SNAPSHOT.jar`

---

## 部署说明

### Docker 部署

```bash
# 构建镜像（多阶段：前端构建 → 后端构建 → 运行时）
docker build -t docker-manager:latest .

# 运行容器
docker run -d \
  --name docker-manager \
  -p 8080:8080 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v docker-manager-data:/app/data \
  --restart unless-stopped \
  docker-manager:latest
```

| 参数 | 说明 |
|-----|------|
| `-v /var/run/docker.sock:...` | 挂载 Docker socket（Linux/macOS） |
| `-v docker-manager-data:/app/data` | 持久化 H2 数据库文件 |

### JAR 直接运行

```bash
java -jar docker-manager-starter/target/docker-manager-starter-1.0.0-SNAPSHOT.jar

# 自定义配置
java -jar docker-manager-starter/target/docker-manager-starter-1.0.0-SNAPSHOT.jar \
  --server.port=9090 \
  --docker.connection.type=tcp \
  --docker.connection.tcp-host=tcp://192.168.1.100:2375
```

### Docker Compose 部署

```yaml
version: '3.8'
services:
  docker-manager:
    build: .
    container_name: docker-manager
    ports:
      - "8080:8080"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
      - dm-data:/app/data
    restart: unless-stopped
    environment:
      - JWT_SECRET=your-custom-secret-at-least-256-bits-long
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8080/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3
volumes:
  dm-data:
```

---

## 配置说明

配置文件位于 `docker-manager-starter/src/main/resources/application.yml`。

### 应用配置

```yaml
server:
  port: 8080                    # 服务端口
  shutdown: graceful            # 优雅关闭
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
  datasource:
    url: jdbc:h2:file:./data/dockermanager;AUTO_SERVER=TRUE
  h2:
    console:
      enabled: true             # H2 Web 控制台（生产环境建议关闭）
      path: /h2-console
```

### Docker 连接配置

```yaml
docker:
  connection:
    type: socket                              # 连接方式: socket 或 tcp
    socket-path: npipe:////./pipe/docker_engine  # Windows Docker Desktop
    # socket-path: unix:///var/run/docker.sock   # Linux / macOS
    tcp-host: tcp://localhost:2375            # TCP 模式地址
    tls-enabled: false                        # 是否启用 TLS
    cert-path:                                # TLS 证书路径
```

### JWT 认证配置

```yaml
jwt:
  secret: your-secret-key-at-least-256-bits-long  # 签名密钥（生产环境务必修改！）
  expiration: 86400000                             # Token 有效期（毫秒，默认 24 小时）
```

### 告警配置

```yaml
alert:
  check-interval: 60000    # 告警检查间隔（毫秒），默认 60 秒
  enabled: true            # 是否启用告警检查
```

**告警规则支持的指标类型：**

| 指标类型 | 说明 | 单位 |
|---------|------|------|
| `HOST_CPU` | 宿主机 CPU 使用率 | % |
| `HOST_MEMORY` | 宿主机内存使用率 | % |
| `HOST_DISK` | 宿主机磁盘使用率 | % |
| `CONTAINER_CPU` | 容器 CPU 使用率 | % |
| `CONTAINER_MEMORY` | 容器内存使用率 | % |
| `CONTAINER_STOPPED` | 容器停止运行 | - |

**钉钉 WebHook 配置：**

1. 在钉钉群中添加自定义机器人，获取 WebHook URL
2. 安全设置选择「加签」，复制密钥
3. 通过 API 创建告警规则：

```json
POST /api/alerts/rules
{
  "name": "CPU使用率过高",
  "hostId": 1,
  "metricType": "HOST_CPU",
  "threshold": 80,
  "compareOperator": "GT",
  "notifyType": "DINGTALK",
  "notifyTarget": "https://oapi.dingtalk.com/robot/send?access_token=xxx",
  "dingtalkSecret": "SECxxx",
  "cooldownSeconds": 300
}
```

---

## API 文档

所有 API 以 `/api` 为前缀，除标注外均需 JWT 认证（`Authorization: Bearer <token>`）。

### 认证（公开）

| 方法 | 路径 | 说明 |
|-----|------|------|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| GET | `/api/auth/profile` | 获取个人信息 |
| POST | `/api/auth/change-password` | 修改密码 |

### 用户管理（管理员）

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/auth/admin/users` | 用户列表 |
| POST | `/api/auth/admin/users/{id}/toggle` | 启用/禁用 |
| DELETE | `/api/auth/admin/users/{id}` | 删除用户 |
| POST | `/api/auth/admin/users/{id}/reset-password` | 重置密码 |

### 项目管理

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/projects` | 所有 Compose 项目 |
| GET | `/api/projects/{name}` | 项目详情 |
| POST | `/api/projects/{name}/refresh` | 刷新项目 |

### 容器管理

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/containers` | 所有容器 |
| GET | `/api/containers/{id}` | 容器详情 |
| GET | `/api/containers/{id}/inspect` | Docker inspect |
| POST | `/api/containers/{id}/restart` | 重启 |
| POST | `/api/containers/{id}/stop` | 停止 |
| POST | `/api/containers/{id}/start` | 启动 |
| GET | `/api/containers/{id}/history` | 状态变更历史 |

### 日志与监控

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/containers/{id}/logs` | 历史日志（文本） |
| GET | `/api/containers/{id}/stats` | Stats 快照 |
| GET | `/api/stats/summary` | 所有容器 Stats |

### 文件系统

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/containers/{id}/fs?path=/` | 列出目录 |
| GET | `/api/containers/{id}/fs/file?path=` | 读取文件 |

### 镜像与更新

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/images/{name}/tags` | 查询可用 Tag（Docker Hub API） |
| POST | `/api/projects/{p}/services/{s}/update` | 更新镜像版本（异步） |
| GET | `/api/tasks/{taskId}/status` | 查询更新任务状态 |

### 宿主机管理

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/hosts` | 所有宿主机列表 |
| GET | `/api/hosts/{id}` | 宿主机详情 |
| POST | `/api/hosts` | 添加宿主机 |
| PUT | `/api/hosts/{id}` | 更新宿主机 |
| DELETE | `/api/hosts/{id}` | 删除宿主机 |
| POST | `/api/hosts/{id}/test` | 测试连接 |
| GET | `/api/hosts/{id}/metrics` | 宿主机资源指标 |
| GET | `/api/hosts/metrics` | 所有宿主机指标汇总 |

### 告警管理

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/alerts/rules` | 所有告警规则 |
| POST | `/api/alerts/rules` | 添加告警规则 |
| PUT | `/api/alerts/rules/{id}` | 更新告警规则 |
| DELETE | `/api/alerts/rules/{id}` | 删除告警规则 |
| GET | `/api/alerts/records?limit=50` | 告警记录 |
| GET | `/api/alerts/records/rule/{ruleId}` | 按规则查询告警记录 |
| POST | `/api/alerts/test-notification` | 测试钉钉通知 |

### 系统与审计

| 方法 | 路径 | 说明 | 认证 |
|-----|------|------|-----|
| GET | `/api/health` | 系统健康检查 | ❌ 公开 |
| GET | `/api/audit?limit=50` | 操作审计日志 | ✅ |
| GET | `/api/projects/{name}/history` | 项目状态变更历史 | ✅ |

### WebSocket（无需认证）

| 路径 | 说明 |
|------|------|
| `ws://host/ws/logs/{containerId}` | 实时日志流推送 |
| `ws://host/ws/stats/{containerId}` | 实时 Stats 推送 |

WebSocket 消息格式：
```json
// 日志: {"streamType": "stdout|stderr", "line": "日志内容"}
// Stats: {"containerId": "...", "cpu": {"percent": 1.5}, "memory": {...}, "network": {...}}
```

---

## 使用说明

### 首次登录

1. 打开浏览器访问 `http://localhost:8080`
2. 系统自动跳转到登录页面
3. 使用默认管理员账号：**admin / admin123**
4. ⚠️ 建议首次登录后立即修改默认密码

### Dashboard

- **统计卡片**：Compose 项目数、运行中容器、已停止容器、总容器数（数字动画效果）
- **项目列表**（左侧）：每个项目显示健康度进度条和服务状态
- **资源使用**（右侧）：运行中容器的 CPU/内存/网络实时数据
- **操作日志**（底部）：最近容器操作记录
- 数据每 30 秒自动刷新，右上角显示倒计时

### 容器详情（4 个 Tab）

| Tab | 功能 |
|-----|------|
| **基本信息** | 容器 ID、镜像、端口、Digests + 操作按钮（重启/停止/启动/更新版本） |
| **日志** | WebSocket 实时日志流，搜索过滤，stdout（白）/stderr（红）区分 |
| **监控** | CPU/内存/网络实时数据 + 60 秒历史趋势柱状图 |
| **文件系统** | 容器内目录浏览、文件查看、目录导航 |

---

## 构建与开发

### Maven 多模块命令

```bash
# 全量构建（4 个模块）
mvn clean package -DskipTests

# 仅编译（不打包）
mvn clean compile

# 仅构建 starter 及其依赖
mvn clean package -pl docker-manager-starter -am -DskipTests

# 运行测试
mvn test

# 跳过前端构建
./build.sh --skip-frontend --skip-tests
```

### 模块依赖关系

```
docker-manager-starter
  └── docker-manager-infrastructure
        ├── docker-manager-application
        │     └── docker-manager-domain
        └── docker-manager-domain
```

---

## 常见问题

### Q: 启动时报 "Failed to connect to Docker"

确保 Docker daemon 正在运行，并检查 `application.yml` 中的连接配置：
```yaml
# Linux / macOS
docker.connection.socket-path: unix:///var/run/docker.sock
# Windows Docker Desktop
docker.connection.socket-path: npipe:////./pipe/docker_engine
```

### Q: 前端页面访问 404

确保前端已构建并拷贝到 `docker-manager-starter/src/main/resources/static/`。使用构建脚本或 Maven 插件会自动完成此步骤。

### Q: Maven 编译报 "package does not exist"

确保从**项目根目录**执行 `mvn clean package`，Maven reactor 会按依赖顺序构建 4 个模块。

### Q: 如何对接远程 Docker？

```yaml
docker:
  connection:
    type: tcp
    tcp-host: tcp://192.168.1.100:2375
    tls-enabled: true        # 建议启用 TLS
    cert-path: /path/to/certs
```

### Q: 生产环境安全建议

1. **修改 JWT 密钥**：`jwt.secret` 设为高强度随机字符串
2. **关闭 H2 控制台**：`spring.h2.console.enabled: false`
3. **启用 HTTPS**：通过 Nginx 反向代理配置 SSL
4. **修改默认密码**：首次登录后立即修改 admin 密码
5. **数据持久化**：将 H2 数据文件挂载到持久卷
6. **限制 CORS**：生产环境配置具体的 `allowedOrigins`

---

## License

MIT
