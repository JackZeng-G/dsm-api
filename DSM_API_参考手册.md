# DSM API 参考手册

> 群晖 DSM 7.x REST API + SSH CLI 工具速查手册。从零开始，每个 API 均附可执行 curl 示例。
> 更新日期：2026-07-22

---

## 〇、快速入门

### 前置条件

在 DSM 控制面板中完成以下配置：

1. **开启 HTTPS**：控制面板 → 网络 → DSM 设置 → 启用 HTTPS（端口 5001）
2. **开启 SSH**：控制面板 → 终端机和 SNMP → 启用 SSH 服务（端口 22）
3. **准备账号**：一个属于 administrators 组的 DSM 账号（具备 SSH sudo 权限）
4. **自签名证书**：若无正式证书，代码中需跳过证书校验

### 通用规则

| 项目 | 说明 |
|------|------|
| API 基址 | `https://<NAS_IP>:5001` |
| 认证方式 | `_sid` URL 参数 + `X-SYNO-TOKEN` 请求头 |
| 会话超时 | 30 分钟无活动过期 |
| 响应格式 | `{"success":true/false, "data":..., "error":{"code":...}}` |
| 错误码 | 100=未知, 101=参数错, 102=API 不存在, 105=权限不足, 119=会话过期 |

### 1 分钟验证

```bash
# 设置你的 NAS 信息
export NAS="https://192.168.1.10:5001"
export USER="admin"
export PASS="password"

# 测试连通性
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.API.Info&version=1&method=query&query=SYNO.API.Auth"

# 登录获取 sid
curl -sk "$NAS/webapi/auth.cgi?api=SYNO.API.Auth&version=6&method=login&account=$USER&passwd=$PASS&format=sid&enable_syno_token=yes&session=FileStation"
```

> `-k` 跳过证书校验，`-s` 静默模式。看到 `"success":true` 即表示连通成功。

---

## 一、认证：SYNO.API.Auth

### 登录

两种调用入口等价，`auth.cgi` GET 兼容中文用户名：

```bash
# 方式一：auth.cgi GET（推荐，兼容中文）
curl -sk "$NAS/webapi/auth.cgi?api=SYNO.API.Auth&version=6&method=login&account=$USER&passwd=$PASS&format=sid&enable_syno_token=yes&session=FileStation"

# 方式二：entry.cgi POST
curl -sk -X POST "$NAS/webapi/entry.cgi" \
  -d "api=SYNO.API.Auth&version=6&method=login&account=$USER&passwd=$PASS&format=sid&enable_syno_token=yes&session=FileStation"
```

**OTP 二次验证登录**：添加 `&otp_code=<6位验证码>`。

**成功响应**：

```json
{
  "success": true,
  "data": {
    "sid": "mRtBEYhQW9VnTCWsygf04Wk...",
    "synotoken": "hbYrJIFoLv7lg"
  }
}
```

**错误响应**：

| code | 含义 | 响应示例 |
|------|------|----------|
| 400 | 账号密码错 | `{"success":false,"error":{"code":400}}` |
| 401 | 账号已禁用 | `{"success":false,"error":{"code":401}}` |
| 403 | 需要 OTP | `{"success":false,"error":{"code":403}}` |
| 404 | OTP 码错 | `{"success":false,"error":{"code":404}}` |

### 会话过期检测

过期请求返回 `{"success":false,"error":{"code":119}}`，此时需重新登录获取新 sid。

### 登出

```bash
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.API.Auth&version=6&method=logout&_sid=$SID"
```

---

## 二、API 发现：SYNO.API.Info

用于测试连通性或查询某 API 是否可用。

```bash
# 查询特定 API
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.API.Info&version=1&method=query&query=SYNO.API.Auth"

# 列出所有可用 API
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.API.Info&version=1&method=query&query=all"
```

响应中 `data` 为各 API 的最小/最大版本号和路径信息。

---

## 三、文件操作

> 以下请求均需携带 `&_sid=<SID>`，为简洁以 `$SID` 表示。

### 3.1 用户信息 — SYNO.FileStation.Info

```bash
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.FileStation.Info&version=2&method=get&_sid=$SID"
```

响应：

```json
{
  "success": true,
  "data": {
    "is_manager": true,
    "username": "admin"
  }
}
```

`is_manager=true` 表示当前用户是 NAS 管理员。

### 3.2 创建文件夹 — SYNO.FileStation.CreateFolder

```bash
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.FileStation.CreateFolder&version=2&method=create&folder_path=/data&name=newfolder&force_parent=true&_sid=$SID"
```

参数：`folder_path` 父目录，`name` 文件夹名，`force_parent=true` 自动创建父目录。

### 3.3 列出内容 — SYNO.FileStation.List

```bash
# 列出所有共享文件夹
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.FileStation.List&version=2&method=list_share&_sid=$SID"

# 列出指定目录内容
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.FileStation.List&version=2&method=list&folder_path=/data&_sid=$SID"

# 附加权限和所有者信息
curl -sk '$NAS/webapi/entry.cgi?api=SYNO.FileStation.List&version=2&method=list&folder_path=/data&additional=["perm","owner"]&_sid=$SID'
```

list_share 响应中 `data.shares` 为共享文件夹数组，每个含 `name`、`path`、`is_dir` 等字段。

### 3.4 复制/移动 — SYNO.FileStation.CopyMove

异步操作：`start` → 获取 `taskid` → `status` 轮询。

```bash
# 发起复制（异步）
curl -sk -X POST "$NAS/webapi/entry.cgi" \
  -d "api=SYNO.FileStation.CopyMove&version=3&method=start" \
  -d 'path=["/data/src_folder"]' \
  -d "dest_folder_path=/data/dest_folder" \
  -d "overwrite=false" \
  -d "remove_src=false" \
  -d "_sid=$SID"

# 轮询进度
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.FileStation.CopyMove&version=3&method=status&taskid=<taskid>&_sid=$SID"
```

| 参数 | 说明 |
|------|------|
| path | JSON 字符串数组，源路径 |
| dest_folder_path | 目标目录 |
| overwrite | `true` 覆盖 / `false` 跳过 |
| remove_src | `true`=移动 / `false`=复制 |

start 响应：
```json
{"success":true,"data":{"taskid":"copy_xxxxx"}}
```

status 响应（`finished:true` 表示完成）：
```json
{"success":true,"data":{"finished":true,"total":100,"progress":100}}
```

### 3.5 重命名 — SYNO.FileStation.Rename

```bash
curl -sk -X POST "$NAS/webapi/entry.cgi" \
  -d "api=SYNO.FileStation.Rename&version=2&method=rename" \
  -d "path=/data/old_name" \
  -d "name=new_name" \
  -d "_sid=$SID"
```

---

## 四、Docker 管理：SYNO.Docker

全部 GET 方法。典型部署流程：list → 找到旧项目 → stop 容器 → delete 项目 → create 新项目 → build。

```bash
# 列出所有 Docker 项目
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=list&_sid=$SID"

# 创建项目（path=物理路径, share_path=共享路径）
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=create&name=myapp&path=/volume1/docker/myapp&share_path=/docker/myapp&_sid=$SID"

# 停止容器
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Docker.Container&version=1&method=stop&name=<容器名>&_sid=$SID"

# 删除项目（同时删除容器和映像）
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=delete&id=<项目ID>&_sid=$SID"

# 构建并启动
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=build&id=<项目ID>&_sid=$SID"
```

**API 速查**：

| API | 方法 | 关键参数 |
|-----|------|----------|
| SYNO.Docker.Project v1 | list | — |
| SYNO.Docker.Project v1 | create | name, path, share_path |
| SYNO.Docker.Project v1 | delete | id |
| SYNO.Docker.Project v1 | build | id |
| SYNO.Docker.Container v1 | stop | name |

---

## 五、共享权限：SYNO.Core.Share.Permission

> ⚠️ 仅支持**共享文件夹级别**。传子目录路径会报 `{"success":false,"error":{"code":402}}`。子目录权限须走 SSH + synoacltool（第七章）。

```bash
# 查询共享文件夹的权限列表
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Core.Share.Permission&version=1&method=list&name=data&offset=0&limit=50&action=enum&is_unite_permission=false&with_inherit=false&user_group_type=local_user&_sid=$SID"

# 设置权限
curl -sk '$NAS/webapi/entry.cgi?api=SYNO.Core.Share.Permission&version=1&method=set&name=data&user_group_type=local_user&permissions=[{"name":"testuser","is_readonly":false,"is_writable":true,"is_deny":false,"is_custom":false}]&_sid=$SID'
```

permissions 元素字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 用户名或群组名 |
| is_readonly | bool | 只读 |
| is_writable | bool | 可写 |
| is_deny | bool | 拒绝访问 |
| is_custom | bool | 自定义权限 |

---

## 六、用户管理：SYNO.Core.User

```bash
# 列出系统用户
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Core.User&version=1&method=list&_sid=$SID"
```

> 完整 CRUD 需管理员会话 + JSON 请求体。无批量接口，批量操作需循环调用。

---

## 七、SSH CLI 工具

### 7.1 SSH 连接（Go）

前置检查：在 DSM 控制面板确认 SSH 已启用，且账号在 administrators 组。

```go
import (
    "bytes"
    "golang.org/x/crypto/ssh"
)

config := &ssh.ClientConfig{
    User: "admin",
    Auth: []ssh.AuthMethod{ssh.Password("password")},
    HostKeyCallback: ssh.InsecureIgnoreHostKey(),
    Timeout:         10 * time.Second,
}
client, err := ssh.Dial("tcp", "192.168.1.251:22", config)

// 执行命令 — 只取 stdout，避免 stderr 的 "Could not chdir" 污染
session, _ := client.NewSession()
var buf bytes.Buffer
session.Stdout = &buf
session.Run("command")
output := buf.String()
```

> SSH 账号若无 home 目录，stderr 会输出 `Could not chdir to home directory` 警告，必须用 stdout-only，不要使用 CombinedOutput。

### 7.2 synoacltool — 子目录 ACL

**路径**：`/usr/syno/bin/synoacltool`

**前置条件**：共享文件夹已启用 Windows ACL 模式（控制面板 → 共享文件夹 → 编辑 → 权限 → 启用 Windows ACL）。

```bash
# 查询目录 ACL
synoacltool -get /volume1/data/project

# 添加读写（f=文件继承 d=目录继承）
synoacltool -add /volume1/data/project "user:zhangsan:allow:rwxpdDaARWcCo:fd--"

# 添加只读
synoacltool -add /volume1/data/project "user:zhangsan:allow:r--pdDaARWcCo:fd--"

# 添加穿越（可导航但不可读内容）
synoacltool -add /volume1/data "user:zhangsan:allow:r-x----------:---n"

# 删除指定索引的条目
synoacltool -del /volume1/data/project 0
```

**权限字符串**：

| 类型 | 字符串 | 说明 |
|------|--------|------|
| 读写 | `rwxpdDaARWcCo` | 完全控制，`fd--` 继承 |
| 只读 | `r--pdDaARWcCo` | 只读，`fd--` 继承 |
| 穿越 | `r-x----------` | 仅导航，`---n` 不继承 |

**ACL 条目格式**：`user:<用户名>:<allow|deny>:<14位权限>:<4位继承>`

继承标志：`f`=文件、`d`=目录、`-`=不继承。如 `fd--` 表示文件+目录均继承。

**权限位明细**：

| 位 | 权限 | 位 | 权限 |
|----|------|----|------|
| r | 读取 | w | 写入 |
| x | 执行 | p | 删除 |
| d | 删除子项 | D | 读取属性 |
| a | 写入属性 | A | 读取扩展属性 |
| R | 写入扩展属性 | W | 读取权限 |
| c | 写入权限 | C | 取得所有权 |
| o | 同步 | | |

**查询返回格式**：

```
ACL version: 1
Archive: is_inherit,has_ACL,is_support_ACL
Owner: [admin(user)]
---------------------
     [0] user:zhangsan:allow:rwxpdDaARWcCo:fd-- (level:0)
     [1] group:administrators:allow:rwxpdDaARWc--:fd-- (level:2)
```

- `[0]`、`[1]` 是删除时用的索引号
- `Owner` 行不是条目，删除时从 `[0]` 开始

### 7.3 synouser — 用户管理（需 sudo）

**路径**：`/usr/syno/sbin/synouser`

```bash
# 创建用户（LANG 确保中文姓名不乱码）
env LANG=en_US.UTF-8 sudo -S synouser --add <用户名> <密码> "<全名>" 0 <邮箱> 0

# 删除用户
sudo -S synouser --del <用户名>

# 修改密码
sudo -S synouser --setpw <用户名> <新密码>
```

参数：`synouser --add <用户名> <密码> <全名> <过期:0/1> <邮箱> <权限:0普通/1管理>`

### 7.4 synogroup — 群组管理（需 sudo）

**路径**：`/usr/syno/sbin/synogroup`

```bash
# 查询群组（含成员列表）
sudo -S synogroup --get <群组名>

# 创建群组并指定初始成员
sudo -S synogroup --add <群组名> <初始成员>

# 添加成员
sudo -S synogroup --memberadd <群组名> <用户名>

# 重设成员列表（即移除某成员时）
sudo -S synogroup --member <群组名> <成员1> <成员2> ...
```

成员输出格式：`N:[用户名]`（N 为数字序号）。

### 7.5 sudo 密码传递（Go）

通过 stdin pipe 传入，避免密码出现在进程列表中：

```go
import "io"

session, _ := client.NewSession()
stdin, _ := session.StdinPipe()
var buf bytes.Buffer
session.Stdout = &buf

session.Start("sudo -S synouser --add user passwd 'Full Name' 0 user@local 0")
io.WriteString(stdin, password+"\n")
stdin.Close()
session.Wait()
```

### 7.6 逻辑路径 → 物理路径

DSM 逻辑路径 `/data/project/subdir` → 物理路径 `/volume1/data/project/subdir`：

```bash
# 查找共享所在的卷
for v in /volume1 /volume2 /volume3 /volume4 /volume5; do
    [ -d "$v/<共享名>" ] && { echo "$v"; break; }
done
```

---

## 八、Web 免登录跳转 FileStation

### 8.1 实现原理

隐藏 iframe POST 登录获取 cookie → 标签页通过 `launchApp` 打开 FileStation → `launchParam` 自动定位到项目目录。

```
用户点击「跳转 NAS」
  → 前端创建隐藏 iframe
  → POST webman/login.cgi（username + passwd）
  → DSM Set-Cookie: id=<session>
  → 1 秒后 window.open:
     https://host:5001/?launchApp=SYNO.SDS.App.FileStation3.Instance
     &launchParam=openfile=<路径>/
  → FileStation 打开并定位到指定目录
```

### 8.2 launchApp 标识对比

| 标识 | 效果 |
|------|------|
| `SYNO.SDS.FileStation.Application` | 打开 DSM 桌面，**不**进入 FileStation |
| `SYNO.SDS.App.FileStation3.Instance` | 直接打开 FileStation 应用 ✅ |

### 8.3 launchParam 文件夹定位

| 参数 | 格式 | 示例 |
|------|------|------|
| `launchParam` | `openfile=<路径>/` | `openfile=/data/项目2024_01/` |

- 路径为**共享文件夹相对路径**（去掉 `/volume1` 前缀）
- 末尾 `/` 必须
- 完整参数需 URL 编码：`openfile%3D%2Fdata%2F%E9%A1%B9%E7%9B%AE%2F`

### 8.4 完整实现（JavaScript）

```javascript
async function openNAS(projectId) {
  const { data } = await api.get(`/projects/${projectId}/nas-link`)
  // data: { host, nas_username, nas_password, nas_path }

  // 去掉 volume 前缀，加末尾 /
  const fsPath = data.nas_path.replace(/^\/volume\d+/, '') + '/'
  const fsUrl = `https://${data.host}:5001/` +
    `?launchApp=SYNO.SDS.App.FileStation3.Instance` +
    `&launchParam=openfile%3D${encodeURIComponent(fsPath)}`

  if (data.nas_password) {
    // 隐藏 iframe 登录（无弹窗、无 JSON 闪现）
    const iframe = document.createElement('iframe')
    iframe.name = 'dsm-auth-' + Date.now()
    iframe.style.display = 'none'
    document.body.appendChild(iframe)

    const form = document.createElement('form')
    form.method = 'POST'
    form.action = `https://${data.host}:5001/webman/login.cgi`
    form.target = iframe.name
    addInput('username', data.nas_username)
    addInput('passwd', data.nas_password)
    document.body.appendChild(form)
    form.submit()

    setTimeout(() => {
      document.body.removeChild(form)
      document.body.removeChild(iframe)
      window.open(fsUrl, '_blank')
    }, 1000)
  } else {
    window.open(fsUrl, '_blank')
  }
}
```

### 8.5 关键前提

- **HTTPS 同域部署**：应用与 DSM 同域名，DSM 反向代理（HTTPS 8443→Docker :8080）
- **Cookie 同域共享**：cookie 按域名跨端口，`webman/login.cgi` 返回的 `id` cookie 可被 `:5001` 端口使用
- **密码加密存储**：前端不存储密码，后端 AES-256-GCM 加密，密钥通过 `ENCRYPTION_KEY` 环境变量注入
- **隐藏 iframe** 优于弹窗：避免浏览器弹窗拦截，避免登录 JSON 响应（`{"success":true}`）闪现

---

## 九、参考脚本

可直接修改 NAS 地址和账号后 `go run` 验证。

### 9.1 Web API 全流程验证

```bash
# 运行
NAS_URL=https://192.168.1.10:5001 NAS_USER=admin NAS_PASS=password go run main.go
```

```go
package main

import (
    "crypto/tls"
    "fmt"
    "io"
    "net/http"
    "net/url"
    "os"
    "strings"
)

func main() {
    nasURL := env("NAS_URL", "https://192.168.1.10:5001")
    user := env("NAS_USER", "admin")
    pass := env("NAS_PASS", "password")

    hc := &http.Client{
        Transport: &http.Transport{TLSClientConfig: &tls.Config{InsecureSkipVerify: true}},
    }

    // 1. API 发现
    r, _ := hc.Get(fmt.Sprintf("%s/webapi/entry.cgi?api=SYNO.API.Info&version=1&method=query&query=SYNO.API.Auth", nasURL))
    b, _ := io.ReadAll(r.Body); r.Body.Close()
    fmt.Printf("API发现: %s\n", status(string(b)))

    // 2. 登录
    r, _ = hc.PostForm(nasURL+"/webapi/auth.cgi", url.Values{
        "api": {"SYNO.API.Auth"}, "version": {"6"}, "method": {"login"},
        "account": {user}, "passwd": {pass},
        "format": {"sid"}, "enable_syno_token": {"yes"}, "session": {"FileStation"},
    })
    b, _ = io.ReadAll(r.Body); r.Body.Close()
    sid := extract(string(b), `"sid":"`)
    fmt.Printf("登录: %s (sid=%s...)\n", status(string(b)), truncate(sid, 16))

    // 3. 共享文件夹
    r, _ = hc.Get(fmt.Sprintf("%s/webapi/entry.cgi?api=SYNO.FileStation.List&version=2&method=list_share&_sid=%s", nasURL, sid))
    b, _ = io.ReadAll(r.Body); r.Body.Close()
    fmt.Printf("共享列表: %s\n", status(string(b)))

    // 4. 用户列表
    r, _ = hc.Get(fmt.Sprintf("%s/webapi/entry.cgi?api=SYNO.Core.User&version=1&method=list&_sid=%s", nasURL, sid))
    b, _ = io.ReadAll(r.Body); r.Body.Close()
    fmt.Printf("用户列表: %s\n", status(string(b)))

    // 5. 登出
    r, _ = hc.Get(fmt.Sprintf("%s/webapi/entry.cgi?api=SYNO.API.Auth&version=6&method=logout&_sid=%s", nasURL, sid))
    b, _ = io.ReadAll(r.Body); r.Body.Close()
    fmt.Printf("登出: %s\n", status(string(b)))
}

func extract(json, key string) string {
    s := strings.Index(json, key); if s == -1 { return "" }; s += len(key)
    e := strings.Index(json[s:], `"`); if e == -1 { return "" }
    return json[s : s+e]
}
func status(s string) string {
    if strings.Contains(s, `"success":true`) { return "✅" }; return "❌"
}
func truncate(s string, n int) string { if len(s) <= n { return s }; return s[:n] + "..." }
func env(k, def string) string { if v := os.Getenv(k); v != "" { return v }; return def }
```

### 9.2 ACL 角色分离验证（SSH）

```bash
# 运行前确保 NAS 上已存在指定的 pm_user 和 lead_user
# 或修改脚本中的用户名
NAS_SSH_HOST=192.168.1.251 NAS_SSH_USER=admin NAS_SSH_PASS=password go run main.go
```

```go
package main

import (
    "bytes"
    "fmt"
    "os"
    "strings"
    "time"

    "golang.org/x/crypto/ssh"
)

const aclTool = "/usr/syno/bin/synoacltool"

func main() {
    host := env("NAS_SSH_HOST", "192.168.1.251") + ":" + env("NAS_SSH_PORT", "22")
    user := env("NAS_SSH_USER", "admin")
    pass := env("NAS_SSH_PASS", "password")

    c, _ := ssh.Dial("tcp", host, &ssh.ClientConfig{
        User: user, Auth: []ssh.AuthMethod{ssh.Password(pass)},
        HostKeyCallback: ssh.InsecureIgnoreHostKey(), Timeout: 10 * time.Second,
    })
    defer c.Close()

    exec := func(cmd string) string {
        s, _ := c.NewSession(); defer s.Close()
        var b bytes.Buffer; s.Stdout = &b; s.Run(cmd); return b.String()
    }

    // ===== 修改此处为 NAS 上真实存在的用户 =====
    pm := "pm_user"     // 项目经理用户名
    lead := "lead_user" // 专业负责人用户名

    root := "/volume1/data/_acl_test"

    // 建测试目录
    exec(fmt.Sprintf("rm -rf %s", root))
    exec(fmt.Sprintf("mkdir -p '%s/1_建筑' '%s/2_结构'", root, root))
    defer exec(fmt.Sprintf("rm -rf %s", root))

    // 授权：PM 项目根读写，lead 仅 1_建筑 只读
    exec(fmt.Sprintf("%s -add '%s' 'user:%s:allow:rwxpdDaARWcCo:fd--'", aclTool, root, pm))
    exec(fmt.Sprintf("%s -add '%s/1_建筑' 'user:%s:allow:r--pdDaARWcCo:fd--'", aclTool, root, lead))

    // 验证
    has := func(p, u string) bool {
        return strings.Contains(exec(fmt.Sprintf("%s -get '%s'", aclTool, p)), "user:"+u+":")
    }

    fmt.Printf("PM 有项目根: %v\n", has(root, pm))
    fmt.Printf("PM 有 1_建筑(继承): %v\n", has(root+"/1_建筑", pm))
    fmt.Printf("PM 有 2_结构(继承): %v\n", has(root+"/2_结构", pm))
    fmt.Printf("lead 有 1_建筑: %v\n", has(root+"/1_建筑", lead))
    fmt.Printf("lead 无 2_结构(隔离): %v\n", !has(root+"/2_结构", lead))
    fmt.Printf("lead 无 项目根(隔离): %v\n", !has(root, lead))
}

func env(k, def string) string { if v := os.Getenv(k); v != "" { return v }; return def }
```

---

## 十、常见问题

**Q: 如何找到我的 NAS IP？**
A: 浏览器访问 `find.synology.com` 或在 DSM 控制面板 → 网络 → 网络接口中查看。

**Q: DSM 7.x 为什么需要 SynoToken？**
A: DSM 7.x 强制 CSRF 防护。登录时设 `enable_syno_token=yes`，后续请求通过 `X-SYNO-TOKEN` 请求头携带。

**Q: 自签名证书报错怎么办？**
A: curl 加 `-k` 参数跳过；Go 设 `InsecureSkipVerify: true`；Node.js 设 `rejectUnauthorized: false`。

**Q: sid 过期了怎么判断？**
A: 响应 `{"success":false,"error":{"code":119}}` 表示会话已过期，重新登录即可。

**Q: CopyMove 什么时候算完成？**
A: 调用 `start` 获取 taskid，轮询 `status` 直到 `data.finished` 为 `true`。

**Q: synoacltool 报 "It's Linux mode" 怎么办？**
A: 该共享文件夹是 Linux 权限模式。DSM 控制面板 → 共享文件夹 → 编辑 → 权限 → 勾选"启用 Windows ACL"。

**Q: SSH 执行命令输出被杂讯污染？**
A: SSH 账号无 home 目录时 stderr 输出警告。Go 中取 `session.Stdout` 而非 `CombinedOutput`。

**Q: 如何查看用户属于哪些群组？**
A: `id -Gn <用户名>`，返回空格分隔的群组名列表。
