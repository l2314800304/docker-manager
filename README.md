# Docker Manager

> Docker Compose 运行监控系统 —— 实时可视化管理你的 Docker Compose 服务

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3.5-green)
![License](https://img.shields.io/badge/License-MIT-yellow)

---

## 目录

- [项目介绍](#项目介绍)
- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
  - [环境要求](#环境要求)
  - [本地开发](#本地开发)
  - [构建生产包](#构建生产包)
- [部署说明](#部署说明)
  - [Docker 部署](#docker-部署)
  - [JAR 直接运行](#jar-直接运行)
  - [Docker Compose 部署](#docker-compose-部署)
- [配置说明](#配置说明)
  - [应用配置](#应用配置)
  - [Docker 连接配置](#docker-连接配置)
  - [JWT 认证配置](#jwt-认证配置)
- [API 文档](#api-文档)
  - [认证相关](#认证相关)
  - [项目管理](#项目管理)
  - [容器管理](#容器管理)
  - [日志与监控](#日志与监控)
  - [文件系统](#文件系统)
  - [镜像与更新](#镜像与更新)
  - [系统与审计](#系统与审计)
  - [WebSocket](#websocket)
- [使用说明](#使用说明)
  - [首次登录](#首次登录)
  - [Dashboard](#dashboard)
  - [Compose 项目列表](#compose-项目列表)
  - [项目详情](#项目详情)
  - [容器详情](#容器详情)
  - [用户管理](#用户管理)
- [架构设计](#架构设计)
- [常见问题](#常见问题)

---

## 项目介绍

Docker Manager 是一个面向 Docker Compose 用户的 Web 监控系统。它通过 docker-java 客户端直连宿主机 Docker daemon，自动发现所有 docker-compose 项目及其服务，提供**实时状态监控**、**日志流查看**、**资源使用统计**、**文件系统浏览**、**镜像版本更新**等全方位管理能力。

系统采用前后端分离架构，前端 Vue3 SPA 打包后嵌入 Spring Boot JAR 中，实现**单文件部署**，开箱即用。

---

## 功能特性

| 功能模块 | 说明 |
|---------|------|
| **Dashboard 总览** | 统计卡片（项目数/运行/停止/总容器）、项目列表、资源使用概览、操作日志 |
| **Compose 项目发现** | 自动扫描宿主机所有 docker-compose 项目，按 label 分组聚合 |
| **容器管理** | 启动/停止/重启容器，查看详细信息（镜像、端口、Digests） |
| **实时日志** | WebSocket 推送容器日志流，支持搜索过滤、自动滚动、stderr/stdout 区分 |
| **资源监控** | 实时 CPU/内存/网络/磁盘 IO 统计，60 秒历史趋势图 |
| **文件系统浏览** | 浏览容器内文件目录，查看文件内容，支持目录导航 |
| **镜像更新** | 选择新 tag → 拉取镜像 → 重启服务，支持回滚 |
| **用户认证** | JWT 无状态认证，登录/注册/修改密码 |
| **用户管理** | 管理员可查看/禁用/删除用户，重置密码 |
| **审计日志** | 记录所有容器操作（重启/停止/启动/更新）的历史 |
| **状态追踪** | 记录容器状态变更（RUNNING → STOPPED 等）历史 |
| **健康检查** | Docker 连接状态检测，Header 实时显示 |

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|-----|------|------|
| Java | 17 | 运行时 |
| Spring Boot | 3.4.1 | Web 框架 |
| Spring Security | - | JWT 认证授权 |
| Spring Data JPA | - | 数据持久化 |
| Spring WebSocket | - | 实时日志/Stats 推送 |
| docker-java | 3.4.1 | Docker API 客户端 |
| H2 Database | - | 嵌入式轻量数据库 |
| jjwt | 0.12.6 | JWT Token 生成/验证 |
| Jackson | - | JSON 序列化 |
| Lombok | - | 代码简化 |

### 前端

| 技术 | 版本 | 说明 |
|-----|------|------|
| Vue | 3.5 | 前端框架 |
| Vite | 8.0 | 构建工具 |
| TypeScript | 6.0 | 类型安全 |
| Element Plus | 2.14 | UI 组件库 |
| Pinia | 3.0 | 状态管理 |
| Vue Router | 5.0 | 路由管理 |
| Axios | 1.17 | HTTP 客户端 |

---

## 项目结构

```
docker-manager/
├── Dockerfile                    # Docker 多阶段构建文件
├── pom.xml                       # Maven 项目配置
├── build.sh / build.ps1          # 构建脚本（Linux/Windows）
├── src/main/java/com/dockermanager/
│   ├── DockerManagerApplication.java   # 应用入口
│   ├── auth/                             # 认证模块
│   │   ├── AuthController.java          #   登录/注册/用户管理 API
│   │   ├── AuthService.java             #   认证业务逻辑
│   │   ├── JwtService.java              #   JWT 生成/验证
│   │   ├── JwtAuthenticationFilter.java #   JWT 过滤器
│   │   ├── SecurityConfig.java          #   Spring Security 配置
│   │   ├── User.java                    #   用户实体
│   │   └── UserRepository.java          #   用户 Repository
│   ├── config/                           # 配置模块
│   │   ├── DockerClientConfig.java      #   Docker 客户端配置
│   │   ├── WebConfig.java               #   CORS + SPA 路由转发
│   │   ├── WebSocketConfig.java         #   WebSocket 配置
│   │   └── ResourceCleanupManager.java  #   优雅关闭资源清理
│   ├── controller/                       # REST API 控制器
│   │   ├── ContainerController.java     #   容器 CRUD + 操作
│   │   ├── ProjectController.java       #   Compose 项目查询
│   │   ├── LogController.java           #   历史日志查询
│   │   ├── StatsController.java         #   资源统计
│   │   ├── FileSystemController.java    #   文件系统浏览
│   │   ├── ImageController.java         #   镜像 Tag + 服务更新
│   │   ├── HealthController.java        #   系统健康检查
│   │   ├── AuditLogController.java      #   审计日志查询
│   │   ├── StatusHistoryController.java #   状态变更历史
│   │   └── GlobalExceptionHandler.java  #   全局异常处理
│   ├── docker/                           # Docker 交互层
│   │   ├── DockerClientService.java     #   Docker API 封装
│   │   ├── ComposeProjectDiscovery.java #   Compose 项目发现
│   │   ├── ContainerLogStreamer.java    #   日志流采集
│   │   ├── ContainerStatsCollector.java #   Stats 采集
│   │   ├── ContainerFileSystemAccessor.java # 文件系统访问
│   │   └── ContainerLifecycleManager.java   # 容器生命周期管理
│   ├── model/                            # 数据模型
│   │   ├── dto/                         #   数据传输对象
│   │   ├── entity/                      #   JPA 实体
│   │   └── enums/                       #   枚举
│   ├── repository/                       # JPA Repository
│   ├── service/                          # 业务逻辑层
│   └── websocket/                        # WebSocket 处理器
│       ├── LogWebSocketHandler.java     #   日志 WS 推送
│       └── StatsWebSocketHandler.java   #   Stats WS 推送
├── frontend/                             # Vue3 前端项目
│   ├── src/
│   │   ├── api/index.ts                # API 请求封装
│   │   ├── stores/auth.ts              # 认证状态管理
│   │   ├── router/index.ts             # 路由 + 导航守卫
│   │   ├── views/                       # 页面视图
│   │   │   ├── LoginView.vue           #   登录/注册页
│   │   │   ├── DashboardView.vue       #   仪表盘
│   │   │   ├── ProjectListView.vue     #   项目列表
│   │   │   ├── ProjectDetailView.vue   #   项目详情
│   │   │   ├── ContainerDetailView.vue #   容器详情
│   │   │   └── UserManagementView.vue  #   用户管理
│   │   └── App.vue                      # 布局框架
│   └── vite.config.ts                  # Vite 构建配置
└── src/main/resources/
    └── application.yml                 # 应用配置文件
```

---

## 快速开始

### 环境要求

| 工具 | 最低版本 | 说明 |
|-----|---------|------|
| Java | 17+ | 后端运行时 |
| Maven | 3.8+ | 后端构建 |
| Node.js | 20.19+ 或 22.12+ | 前端构建 |
| Docker | 20+ | 运行时（宿主机需运行 Docker） |

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
# 前端运行在 http://localhost:5173，API 自动代理到后端 8080
```

**3. 后端开发**

```bash
# 确保 Docker 已运行
mvn spring-boot:run
# 后端运行在 http://localhost:8080
```

> 开发模式下，前端和后端分别运行，Vite dev server 通过 proxy 转发 `/api` 和 `/ws` 请求到后端。

### 构建生产包

**方式一：使用构建脚本（推荐）**

```bash
# Linux / macOS
chmod +x build.sh
./build.sh

# Windows PowerShell
.\build.ps1
```

支持参数：
- `--skip-frontend` / `-SkipFrontend` — 跳过前端构建
- `--skip-tests` / `-SkipTests` — 跳过测试

**方式二：手动构建**

```bash
# 1. 构建前端
cd frontend
npm ci
npm run build-only

# 2. 拷贝到 Spring Boot 静态资源目录
cp -r dist/ ../src/main/resources/static/
cd ..

# 3. Maven 打包
mvn clean package -DskipTests -B
```

**方式三：仅 Maven（需要 npm 在 PATH 中）**

```bash
mvn clean package -DskipTests -B
# Maven 会自动执行 npm ci + npm run build-only + 拷贝 dist
```

产出文件：`target/docker-manager-1.0.0-SNAPSHOT.jar`

---

## 部署说明

### Docker 部署

```bash
# 构建镜像
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

**关键参数说明：**

| 参数 | 说明 |
|-----|------|
| `-v /var/run/docker.sock:/var/run/docker.sock` | 挂载 Docker socket，容器内访问宿主机 Docker |
| `-v docker-manager-data:/app/data` | 持久化 H2 数据库文件 |
| `--restart unless-stopped` | 异常退出自动重启 |

> **Windows 用户**：Docker socket 路径为 `//./pipe/docker_engine`，使用 Docker Desktop 时默认已映射。

### JAR 直接运行

```bash
# 确保 Docker daemon 正在运行
java -jar target/docker-manager-1.0.0-SNAPSHOT.jar

# 自定义配置
java -jar target/docker-manager-1.0.0-SNAPSHOT.jar \
  --server.port=9090 \
  --docker.connection.type=tcp \
  --docker.connection.tcp-host=tcp://192.168.1.100:2375
```

### Docker Compose 部署

创建 `docker-compose.yml`：

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
      - DOCKER_CONNECTION_TYPE=socket
      - JWT_SECRET=your-custom-secret-key-at-least-256-bits
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8080/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  dm-data:
```

```bash
docker compose up -d --build
```

---

## 配置说明

配置文件位于 `src/main/resources/application.yml`，支持通过环境变量或命令行参数覆盖。

### 应用配置

```yaml
server:
  port: 8080                    # 服务端口
  shutdown: graceful            # 优雅关闭

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s  # 关闭超时
  datasource:
    url: jdbc:h2:file:./data/dockermanager;AUTO_SERVER=TRUE  # H2 数据库路径
    username: sa                # 数据库用户名
    password:                   # 数据库密码（默认空）
  h2:
    console:
      enabled: true             # H2 Web 控制台
      path: /h2-console         # 控制台路径
```

### Docker 连接配置

```yaml
docker:
  connection:
    type: socket                              # 连接方式：socket 或 tcp
    socket-path: npipe:////./pipe/docker_engine  # Windows named pipe
    # socket-path: unix:///var/run/docker.sock   # Linux socket
    tcp-host: tcp://localhost:2375            # TCP 模式地址
    tls-enabled: false                        # 是否启用 TLS
    cert-path:                                # TLS 证书路径
```

**支持的 Docker Socket 路径：**

| 系统 | 路径 |
|-----|------|
| Linux | `unix:///var/run/docker.sock` |
| Windows (Docker Desktop) | `npipe:////./pipe/docker_engine` |
| macOS | `unix:///var/run/docker.sock` |

### JWT 认证配置

```yaml
jwt:
  secret: your-secret-key-at-least-256-bits-long  # JWT 签名密钥（生产环境务必修改）
  expiration: 86400000                             # Token 有效期（毫秒，默认 24 小时）
```

---

## API 文档

所有 API 以 `/api` 为前缀，除 `/api/health` 和 `/api/auth/**` 外均需 JWT 认证。

请求头：`Authorization: Bearer <token>`

### 认证相关

| 方法 | 路径 | 说明 | 认证 |
|-----|------|------|-----|
| POST | `/api/auth/register` | 用户注册 | ❌ |
| POST | `/api/auth/login` | 用户登录 | ❌ |
| GET | `/api/auth/profile` | 获取个人信息 | ✅ |
| POST | `/api/auth/change-password` | 修改密码 | ✅ |
| GET | `/api/auth/admin/users` | 用户列表（管理员） | ✅ |
| POST | `/api/auth/admin/users/{id}/toggle` | 启用/禁用用户 | ✅ |
| DELETE | `/api/auth/admin/users/{id}` | 删除用户 | ✅ |
| POST | `/api/auth/admin/users/{id}/reset-password` | 重置密码 | ✅ |

### 项目管理

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/projects` | 获取所有 Compose 项目 |
| GET | `/api/projects/{name}` | 获取项目详情 |
| POST | `/api/projects/{name}/refresh` | 刷新项目信息 |

### 容器管理

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/containers` | 获取所有容器 |
| GET | `/api/containers/{id}` | 获取容器详情 |
| GET | `/api/containers/{id}/inspect` | Docker inspect 信息 |
| POST | `/api/containers/{id}/restart` | 重启容器 |
| POST | `/api/containers/{id}/stop` | 停止容器 |
| POST | `/api/containers/{id}/start` | 启动容器 |
| GET | `/api/containers/{id}/history` | 容器状态变更历史 |

### 日志与监控

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/containers/{id}/logs?tail=200&since=` | 历史日志（文本） |
| GET | `/api/containers/{id}/stats` | 当前 Stats 快照 |
| GET | `/api/stats/summary` | 所有运行容器 Stats |

### 文件系统

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/containers/{id}/fs?path=/` | 列出目录 |
| GET | `/api/containers/{id}/fs/file?path=` | 读取文件内容 |
| GET | `/api/containers/{id}/fs/download?path=` | 下载文件 |

### 镜像与更新

| 方法 | 路径 | 说明 |
|-----|------|------|
| GET | `/api/images/{name}/tags` | 查询可用 Tag（Docker Hub API） |
| POST | `/api/projects/{p}/services/{s}/update` | 更新服务镜像版本 |
| GET | `/api/tasks/{taskId}/status` | 查询更新任务状态 |

### 系统与审计

| 方法 | 路径 | 说明 | 认证 |
|-----|------|------|-----|
| GET | `/api/health` | 系统健康检查 | ❌ |
| GET | `/api/audit?limit=50` | 操作审计日志 | ✅ |
| GET | `/api/projects/{name}/history` | 项目状态变更历史 | ✅ |

### WebSocket

| 路径 | 说明 |
|------|------|
| `ws://host/ws/logs/{containerId}` | 实时日志流推送 |
| `ws://host/ws/stats/{containerId}` | 实时 Stats 推送 |

WebSocket 连接无需认证，消息格式为 JSON：

```json
// 日志消息
{"streamType": "stdout|stderr", "line": "日志内容"}

// Stats 消息
{"containerId": "...", "cpu": {"percent": 1.5}, "memory": {"usage": 104857600, "limit": 1073741824, "percent": 9.8}, "network": {"rxBytesPerSec": 1024, "txBytesPerSec": 512}}
```

---

## 使用说明

### 首次登录

1. 打开浏览器访问 `http://localhost:8080`
2. 系统自动跳转到登录页面
3. 使用默认管理员账号登录：
   - **用户名**：`admin`
   - **密码**：`admin123`
4. 建议首次登录后立即修改默认密码

### Dashboard

Dashboard 是系统的首页，包含以下区域：

- **统计卡片**：显示 Compose 项目数、运行中容器数、已停止容器数、总容器数（数字动画效果）
- **项目列表**（左侧）：展示所有 Compose 项目及其服务状态，每个项目有健康度进度条
- **资源使用**（右侧）：显示所有运行中容器的 CPU/内存/网络实时数据
- **操作日志**（底部）：展示最近的容器操作记录（重启/停止/启动/更新）

数据每 30 秒自动刷新，右上角显示倒计时。

### Compose 项目列表

- 以卡片形式展示所有 docker-compose 项目
- 每张卡片显示：项目名、工作目录、服务数、运行/停止状态
- 点击卡片进入项目详情
- 点击服务行直接跳转到容器详情

### 项目详情

- 项目基本信息描述表
- 服务列表表格：服务名、状态、镜像、端口、Digests、运行时间
- 点击行跳转到对应容器详情

### 容器详情

容器详情是最核心的页面，包含 4 个 Tab：

**基本信息 Tab**
- 容器 ID、名称、服务名、所属项目
- 镜像信息（名称、ID、Digests）
- 端口映射、创建时间、工作目录
- 操作按钮：重启、停止、启动、更新版本

**日志 Tab**
- WebSocket 实时日志流推送
- 支持关键词搜索过滤
- 自动滚动开关
- stdout（白色）和 stderr（红色）区分显示
- 最多保留 3000 条日志

**监控 Tab**
- CPU 使用率、内存使用、网络上下行实时数据
- CPU 和内存 60 秒历史趋势柱状图
- 超过 80% 阈值红色警告

**文件系统 Tab**
- 浏览容器内文件目录结构
- 显示权限、所有者、大小、修改时间
- 点击文件查看内容
- 支持目录导航和上级目录返回

### 用户管理

仅管理员角色可见，功能包括：

- 查看所有用户列表（用户名、昵称、角色、状态、创建时间、最后登录）
- 启用/禁用用户
- 重置用户密码
- 删除用户（管理员账号不可删除）

---

## 架构设计

```
┌──────────────────────────────────────────────────────┐
│                    Browser (Vue3 SPA)                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐  │
│  │Dashboard │ │ Projects │ │Container │ │ Users  │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └───┬────┘  │
│       │ REST API   │            │ WebSocket   │      │
└───────┼────────────┼────────────┼─────────────┼──────┘
        │            │            │             │
┌───────▼────────────▼────────────▼─────────────▼──────┐
│              Spring Boot 3.4.1                       │
│  ┌─────────────────────────────────────────────┐     │
│  │         Spring Security + JWT Filter         │     │
│  └──────────────────┬──────────────────────────┘     │
│  ┌──────────┐ ┌─────┴─────┐ ┌──────────┐ ┌────────┐ │
│  │ REST API │ │ WebSocket │ │ Service  │ │  H2 DB │ │
│  │Controller│ │  Handler  │ │   Layer  │ │(嵌入式) │ │
│  └────┬─────┘ └─────┬─────┘ └────┬─────┘ └────────┘ │
│       │             │            │                    │
│  ┌────▼─────────────▼────────────▼──────────────────┐│
│  │              docker-java Client                   ││
│  └──────────────────────┬───────────────────────────┘│
└─────────────────────────┼────────────────────────────┘
                          │ Docker API
                    ┌─────▼─────┐
                    │  Docker   │
                    │  Daemon   │
                    └───────────┘
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

如果在容器内运行，需要挂载 Docker socket：
```bash
-v /var/run/docker.sock:/var/run/docker.sock
```

### Q: 前端页面访问 404

确保前端已构建并拷贝到 `src/main/resources/static/` 目录。使用构建脚本或 Maven 插件会自动完成此步骤。

### Q: 如何修改默认管理员密码？

1. 登录后点击右上角头像 → 修改密码
2. 或通过用户管理页面重置密码
3. 直接修改 H2 数据库：访问 `http://localhost:8080/h2-console`

### Q: 镜像 Tag 查询返回空

镜像 Tag 查询通过 Docker Hub API 获取，需要：
- 服务器能访问外网 `hub.docker.com`
- 镜像名称为 Docker Hub 上的公开镜像
- 私有仓库暂不支持自动查询，可手动输入 Tag

### Q: WebSocket 连接断开后不会自动重连

前端已实现指数退避自动重连（1s → 2s → 4s → ... → 最大 30s）。如果持续断开，请检查：
- 网络是否稳定
- 容器是否仍在运行
- 服务端日志是否有错误

### Q: 如何对接远程 Docker？

修改 `application.yml` 使用 TCP 模式：

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
3. **启用 HTTPS**：通过反向代理（Nginx）配置 SSL
4. **修改默认密码**：首次登录后立即修改 admin 密码
5. **数据库持久化**：将 H2 数据文件挂载到持久卷
6. **限制 CORS**：生产环境配置具体的 allowedOrigins

---

## License

MIT
