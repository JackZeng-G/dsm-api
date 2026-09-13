# DSM API Examples

[English](#english) | [中文](#中文)

---

## 中文

群晖 DSM 7.x REST API 多语言调用示例 + 完整参考手册。覆盖认证、文件操作、用户管理、Docker 管理等常用 API。

📖 **完整 API 参考**：[DSM_API_参考手册.md](DSM_API_参考手册.md) —— 〇 + 十六章：

- 〇、快速入门：前置条件、通用规则、1 分钟连通性验证
- 一~六章 + 十二、十三、十五章：curl/Go/SSH 实测**验证主体**（认证 / API 发现 / 文件 / Docker 项目 / 共享权限 / 用户 / SSH CLI / Web 免登录跳转 / 参考脚本）
- 七~十一章：`synology-api` 封装库源码**反推**（系统与存储 / 系统服务与安全 / 媒体与协作 / 备份与虚拟化 / 目录服务，遇冲突以验证主体为准）
- 十四章：Python 封装库 `synology-api` 用法
- 十六章：常见问题

### 前置条件

1. DSM 控制面板 → 网络 → 启用 HTTPS（端口 5001）
2. DSM 控制面板 → 终端机和 SNMP → 启用 SSH（端口 22，仅 SSH 相关示例需要）
3. 一个 administrators 组的账号

### 快速开始

1. Clone 本项目
2. 选择你使用的语言目录进入
3. 修改脚本中的 `NAS_IP`、`NAS_USER`、`NAS_PASS`
4. 运行

### 目录结构

```
dsm-api/
├── scripts/          # 各语言的 API 封装工具库
│   ├── bash/         # bash: dsm_api.sh（curl 封装函数）
│   ├── python/       # python: dsm_api.py（requests 封装类）+ requirements.txt
│   ├── go/           # go: dsm_api.go（net/http 封装）+ go.mod
│   ├── nodejs/       # nodejs: dsm_api.js（fetch 封装）+ package.json
│   └── java/         # java: DsmApi.java（HttpURLConnection 封装）
├── examples/         # 每个 API 的独立可执行示例
│   ├── bash/         # 01_auth.sh 02_file.sh 03_docker.sh 04_user_share.sh 05_system.sh
│   ├── python/       # 同上，.py
│   ├── go/           # 同上，.go
│   ├── nodejs/       # 同上，.js
│   └── java/         # 命名不同：DsmApiAuth.java DsmApiFile.java DsmApiDocker.java
│                     #           DsmApiUserShare.java DsmApiSystem.java
├── DSM_API_参考手册.md  # 完整 API 参考手册（〇 + 十六章）
├── LICENSE           # MIT
├── .gitignore
└── README.md
```

### API 覆盖

| API | 方法 | 说明 |
|-----|------|------|
| SYNO.API.Info | query | API 发现 / 连通性测试 |
| SYNO.API.Auth | login / logout | 登录 / 登出（自动携带 X-SYNO-TOKEN） |
| SYNO.FileStation.Info | get | 用户信息 |
| SYNO.FileStation.List | list / list_share | 列出目录 / 共享文件夹 |
| SYNO.FileStation.CreateFolder | create | 创建文件夹 |
| SYNO.FileStation.CopyMove | start / status | 复制 / 移动（异步） |
| SYNO.FileStation.Rename | rename | 重命名 |
| SYNO.Docker.Project | list / get / stop / delete / create / build | Docker 项目生命周期（手册 4.1 清理重建） |
| SYNO.Docker.Container | stop | 停止容器（兜底） |
| SYNO.Core.Share.Permission | list | 共享权限查询（示例 04） |
| SYNO.Core.User | list | 用户列表（示例 04） |
| SYNO.Core.System.SystemHealth | get | 系统健康总览（手册 7.1） |
| SYNO.Core.System.Utilization | get | CPU/内存/磁盘利用率（示例 05） |
| SYNO.Core.Storage.Disk | list | 磁盘列表（示例 05） |
| SYNO.Storage.CGI.Smart | get_health_info | SMART 健康（示例 05） |

### 各语言运行方式

以下命令均在**仓库根目录**执行。

**Bash**（示例内部已 `source` 封装库，无需预先 source）:
```bash
export NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password
bash examples/bash/01_auth.sh
```

**Python**:
```bash
pip install -r scripts/python/requirements.txt
NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password python3 examples/python/01_auth.py
```

**Node.js**（`package.json` 无依赖，无需 `npm install`；示例按文件相对路径 require 封装库）:
```bash
NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password node examples/nodejs/01_auth.js
```

**Java**（示例文件是 `DsmApiAuth.java` 这套命名，且声明了 `package com.dsm.examples`，运行时必须带包名前缀）:
```bash
javac -d out scripts/java/DsmApi.java examples/java/DsmApiAuth.java
NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password java -cp out com.dsm.examples.DsmApiAuth
```

**Go**：当前**不可用**，见下方「已知问题」。

> 示例文件：bash / python / go / nodejs 为 `01_auth` / `02_file` / `03_docker` / `04_user_share` / `05_system`，替换文件名即可运行其他示例；java 为 `DsmApiAuth` / `DsmApiFile` / `DsmApiDocker` / `DsmApiUserShare` / `DsmApiSystem`。`04`（用户/权限）、`05`（系统/存储）为**只读查询**，安全可直接跑；`03_docker` 的清理重建流程为破坏性，默认注释不执行。

### 已知问题

1. **Go 示例无法编译**：`scripts/go/go.mod` 把模块根设在 `scripts/go/`，因此该目录下 `dsm_api.go` 的导入路径是 `github.com/example/dsm-api`；而 5 个示例都写成 `github.com/example/dsm-api/scripts/go`，此路径在模块内不存在，`go build` 会转为从 GitHub 拉取该模块并失败：

   ```
   go: finding module for package github.com/example/dsm-api/scripts/go
   fatal: could not read Username for 'https://github.com': terminal prompts disabled
   ```

   修法：把 `go.mod` 移到仓库根（模块名不变），`scripts/go` 的导入路径即恰好为 `.../scripts/go`，示例与封装库同处一个模块。

2. **Java 示例命名与其他语言不一致**：`examples/java/` 用 `DsmApiAuth.java` 等命名，其余四种语言用 `01_auth.*` 等。各示例文件头的用法注释写作 `java -cp ../../scripts/java:. DsmApiAuth`，缺 `com.dsm.examples.` 包名前缀，照抄会报找不到主类。

3. 上文 bash / python / nodejs 的命令已实测通过路径与语法检查；java 命令按源码包声明推导，**无 JDK 环境未能实测**。

### 许可

MIT License. See [LICENSE](LICENSE).

---

## English

Multi-language example scripts + complete reference manual for Synology DSM 7.x REST API. Covers authentication, file operations, user management, Docker management, and more.

📖 **Full API reference**: [DSM_API_参考手册.md](DSM_API_参考手册.md) — section 〇 plus 16 chapters:

- Ch.0 Quick start: prerequisites, common rules, one-minute connectivity check
- Ch.1-6, 12, 13, 15: verified curl/Go/SSH **core** (auth / API discovery / file / Docker project / share permission / user / SSH CLI / web auto-login / reference scripts)
- Ch.7-11: reverse-engineered from the `synology-api` library (system & storage / system services & security / media & collaboration / backup & virtualization / directory services; defer to the verified core on conflict)
- Ch.14: Python `synology-api` wrapper usage
- Ch.16: FAQ

### Prerequisites

1. DSM Control Panel → Network → Enable HTTPS (port 5001)
2. DSM Control Panel → Terminal & SNMP → Enable SSH (port 22, SSH examples only)
3. An account in the administrators group

### Quick Start

1. Clone this repo
2. Navigate to your language directory
3. Edit `NAS_IP`, `NAS_USER`, `NAS_PASS` in the scripts
4. Run

### Languages

| Language | Library | Script |
|----------|---------|--------|
| Bash | curl + jq | `scripts/bash/dsm_api.sh` |
| Python | requests | `scripts/python/dsm_api.py` |
| Go | net/http | `scripts/go/dsm_api.go` |
| Node.js | fetch (built-in) | `scripts/nodejs/dsm_api.js` |
| Java | HttpURLConnection | `scripts/java/DsmApi.java` |

> Examples: `01_auth` / `02_file` / `03_docker` / `04_user_share` / `05_system`. `04` (user/share) & `05` (system/storage) are **read-only** and safe to run; `03_docker` cleanup flow is destructive (commented out by default).

### License

MIT License. See [LICENSE](LICENSE).
