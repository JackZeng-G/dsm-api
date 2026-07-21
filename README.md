# DSM API Examples

[English](#english) | [中文](#中文)

---

## 中文

群晖 DSM 7.x REST API 多语言调用示例。覆盖认证、文件操作、用户管理、Docker 管理等常用 API。

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
│   ├── python/       # python: dsm_api.py（requests 封装类）
│   ├── go/           # go: dsm_api.go（net/http 封装）
│   ├── nodejs/       # nodejs: dsm_api.js（fetch 封装）
│   └── java/         # java: DsmApi.java（HttpURLConnection 封装）
├── examples/         # 每个 API 的独立可执行示例
│   ├── bash/
│   ├── python/
│   ├── go/
│   ├── nodejs/
│   └── java/
├── LICENSE           # MIT
└── README.md
```

### API 覆盖

| API | 方法 | 说明 |
|-----|------|------|
| SYNO.API.Info | query | API 发现 / 连通性测试 |
| SYNO.API.Auth | login / logout | 登录 / 登出 |
| SYNO.FileStation.Info | get | 用户信息 |
| SYNO.FileStation.List | list / list_share | 列出目录 / 共享文件夹 |
| SYNO.FileStation.CreateFolder | create | 创建文件夹 |
| SYNO.FileStation.CopyMove | start / status | 复制 / 移动（异步） |
| SYNO.FileStation.Rename | rename | 重命名 |
| SYNO.Docker.Project | list / create | Docker 项目管理 |
| SYNO.Docker.Container | stop | 停止容器 |
| SYNO.Core.Share.Permission | list / set | 共享权限 |
| SYNO.Core.User | list | 用户列表 |

### 各语言运行方式

**Bash**:
```bash
export NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password
source scripts/bash/dsm_api.sh
./examples/bash/01_auth.sh
```

**Python**:
```bash
pip install -r scripts/python/requirements.txt
NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password python3 examples/python/01_auth.py
```

**Go**:
```bash
cd scripts/go && go mod tidy
NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password go run ../examples/go/01_auth.go
```

**Node.js**:
```bash
cd scripts/nodejs && npm install
NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password node ../examples/nodejs/01_auth.js
```

**Java**:
```bash
javac examples/java/01_auth.java -d out && java -cp out DsmApiAuth
```

### 许可

MIT License. See [LICENSE](LICENSE).

---

## English

Multi-language example scripts for Synology DSM 7.x REST API. Covers authentication, file operations, user management, Docker management, and more.

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

### License

MIT License. See [LICENSE](LICENSE).
