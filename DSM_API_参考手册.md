# DSM API 参考手册

> 群晖 DSM 7.x REST API + SSH CLI 工具速查手册。从零开始，每个 API 均附可执行 curl 示例。
>
> **定位**：一-六章、十二-十三章、十五章为实测验证的 curl/Go/CLI **主体**（权威）；七章起及各章带「资料来源」的小节为 `synology-api` 封装库源码**反推**，供扩展参考，**遇冲突以验证主体为准**。

---

## 目录

| 章 | 标题 | 性质 |
|----|------|------|
| 〇 | 快速入门 | ✅ 验证 |
| 一 | 认证：SYNO.API.Auth | ✅ 验证 |
| 二 | API 发现：SYNO.API.Info | ✅ 验证 |
| 三 | 文件操作（FileStation） | ✅ 验证 |
| 四 | Docker 项目管理 | ✅ 验证 |
| 五 | 共享权限 | ✅ 验证 |
| 六 | 用户管理 | ✅ 验证 |
| 七 | 系统与存储 API | 📎 反推 |
| 八 | 系统服务与安全 API | 📎 反推 |
| 九 | 媒体与协作套件 API | 📎 反推 |
| 十 | 备份与虚拟化 API | 📎 反推 |
| 十一 | 目录服务 API | 📎 反推 |
| 十二 | SSH CLI 工具 | ✅ 验证 |
| 十三 | Web 免登录跳转 FileStation | ✅ 验证 |
| 十四 | Python 封装库 synology-api | — |
| 十五 | 参考脚本 | ✅ 验证 |
| 十六 | 常见问题 | — |

> ✅ = 实测验证主体（权威）；📎 = 封装库源码反推（参考，遇冲突以验证为准）。

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
| 错误码 | 100=未知, 101=参数错, 102=API 不存在, 105=权限不足, 119=会话过期, 114=API 不可用, 2104=Docker 项目状态冲突 |

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

### 认证机制补充

> 资料来源：[`synology-api`](https://github.com/N4S4/synology-api) `auth.py` 反推。

- **session 名陷阱**：非管理员账号登录时 session 名应填 `webui`（库硬编码，注释「兼容非管理员 API 调用」），而非上文示例的 `FileStation`。非管理员用 `FileStation` 可能登录失败或权限受限。
- **http 链路密码加密**：非 HTTPS 时 password 不明文传输——先调 `encryption.cgi`（`SYNO.API.Encryption`）取 NAS RSA 公钥，再用 RSA+AES 加密 `account`/`passwd`。生产仍建议 HTTPS。
- **DSM 7 Noise 握手**：DSM 7.2+ 登录可带 `ik_message` 参数，响应触发 Noise 协议握手（库 `_finish_noise_handshake`），用于后续请求签名。裸 curl 一般无需处理，但极新固件遇登录异常可留意。
- **可信设备（device_id）**：OTP 场景下传 `device_id` + `device_name` 可标记可信设备，免重复 OTP；两者须成对使用。
- **SynoToken**：登录响应 `data.synotoken` 即上文 `X-SYNO-TOKEN` 头的值，失效（code=119）后重新登录获取。

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

### 3.6 其他 FileStation API

> 资料来源：[`synology-api`](https://github.com/N4S4/synology-api) 封装库源码反推（库取动态 `maxVersion`，参数名为源码字段名）。3.1-3.5 已覆盖 Info/List/CreateFolder/Rename/CopyMove；下表为其余 API。异步任务（Search/DirSize/Delete/Extract/Compress）统一 `start` 取 taskid → 轮询 `status` → `stop` 终止。

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.FileStation.Search | start / list / stop | start: folder_path, recursive, pattern, extension, filetype, size_from, size_to, mtime_from, mtime_to, owner, group；list/stop: taskid | 异步文件搜索 |
| SYNO.FileStation.VirtualFolder | list | type, offset, limit, additional | CIFS/NFS/远程挂载点列表 |
| SYNO.FileStation.Favorite | list / add / delete / clear_broken / edit | path, name, index | 收藏夹管理 |
| SYNO.FileStation.DirSize | start / stop / status | start: path；stop/status: taskid | 异步目录大小统计 |
| SYNO.FileStation.MD5 | start / status | start: file_path；status: taskid | 库 `stop_md5` 实现误调 DirSize（bug） |
| SYNO.FileStation.CheckPermission | write | path, filename, overwrite, create_only | 写入前权限预检 |
| SYNO.FileStation.Upload | upload | path, create_parents, overwrite, file（multipart） | 文件上传，file 走 multipart 表单 |
| SYNO.FileStation.Sharing | getinfo / list / create / delete / clear_invalid / edit | id；create/edit: path, password, date_expired, date_available | 分享链接管理 |
| SYNO.FileStation.Delete | start / status / stop / delete | start: path, recursive, accurate_progress；delete(同步): path, recursive | 异步删除大目录；delete 为阻塞同步 |
| SYNO.FileStation.Extract | start / status / stop / list | file_path, dest_folder_path, overwrite, keep_dir, create_subfolder, codepage, password | 解压；list 列压缩包内文件 |
| SYNO.FileStation.Compress | start / status / stop | path, dest_file_path, level, mode, format, _password | 压缩，format=zip/tar |
| SYNO.FileStation.BackgroundTask | list | offset, limit, sort_by, api_filter | 所有后台任务总览 |
| SYNO.FileStation.Download | download | path, mode（open/download/serve） | 文件下载，mode 走 query string |

---

## 四、Docker 项目管理：SYNO.Docker.Project

DSM Container Manager 以「项目」为管理单位（一个项目 = compose 定义的一组容器 + 映像）。本系统部署 / 升级即围绕项目级 API。全部用 GET 方法调用即可（DSM 同时接受 GET / POST）。

### 4.1 正确的清理 → 重建顺序（关键）

升级或重新部署时，**必须按以下顺序清理旧项目**，否则会触发「项目卡 `CREATED` / build 报 `2104`」死循环：

```text
1. Project list          按 name 找到旧项目 ID
2. Project stop(id)      项目级停止
3. 轮询 Project list     读 status，直到不再是 RUNNING（即 STOPPED）
4. Container stop(name)  兜底：项目级 stop 未生效时才用
5. Project delete(id)    STOPPED 状态下才真删（含容器 + 映像）
6. Project list          验证项目已从列表消失
7. Project create(name, path, share_path)
8. Project build(id)
```

**核心陷阱**：步骤 2 后**必须轮询等 status 切到 `STOPPED`** 再 delete。若在 `RUNNING` 状态直接 delete，API 返回 `success:true` 但实际未删（假成功）→ 下次 create / build 冲突报 `error.code=2104`，项目卡在 `CREATED`，此后 delete / build 均返 2104 无法清理，只能去 DSM Container Manager UI 手动删除。

### 4.2 API 调用

```bash
# 列出所有项目（返回 map，key=项目ID，value 含 name/status/id）
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=list&_sid=$SID"

# 项目级停止（用 id，不是 name）— 清理首选
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=stop&id=<项目ID>&_sid=$SID"

# 删除项目（含容器 + 映像；项目须处于 STOPPED 才真删）
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=delete&id=<项目ID>&_sid=$SID"

# 创建项目（path=物理路径，share_path=共享相对路径，去掉 /volume1 前缀）
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=create&name=myapp&path=/volume1/docker/myapp&share_path=/docker/myapp&_sid=$SID"

# 构建并启动
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=build&id=<项目ID>&_sid=$SID"

# 容器级停止（仅兜底，name=容器名）
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Docker.Container&version=1&method=stop&name=<容器名>&_sid=$SID"
```

list 响应（`status` 取值 `running` / `stopped` / `created`，轮询判断用）：

```json
{
  "success": true,
  "data": {
    "abc123def": { "id": "abc123def", "name": "huayang-file", "status": "running" }
  }
}
```

### 4.3 API 速查

| API | 方法 | 关键参数 | 说明 |
|-----|------|----------|------|
| SYNO.Docker.Project v1 | list | — | 返回 map，含 status |
| SYNO.Docker.Project v1 | stop | id | 项目级停止（首选） |
| SYNO.Docker.Project v1 | delete | id | 须 STOPPED 才真删 |
| SYNO.Docker.Project v1 | create | name, path, share_path | share_path 去掉 /volume1 前缀 |
| SYNO.Docker.Project v1 | build | id | 构建并启动 |
| SYNO.Docker.Container v1 | stop | name | 仅兜底 |

### 4.4 不可用的 API（避坑）

`SYNO.Docker.Container` / `SYNO.Docker.Image` 的 `list` / `delete` 在本 DSM 返回 `error.code=114`（不可用）。**删容器 / 映像只能依赖 Project delete（STOPPED 时真删）**，无法单独操作容器或映像。

### 4.5 其他 Docker API

> 资料来源：[`synology-api`](https://github.com/N4S4/synology-api) 封装库源码反推（库取动态 `maxVersion`，参数名为源码字段名）。项目级 list/stop/delete/create/build、容器级 stop 见 4.2。下表标注「本机 114」者为 4.4 已验证在本 DSM 不可用。

> ⚠️ **Project create 参数差异（以 4.2 已验证为准）**：封装库 `SYNO.Docker.Project.create` 传 `content`（compose YAML 内容字符串）；手册 4.2 传 `path`（compose 文件物理目录 `/volume1/docker/myapp`）——两种创建方式不等价。**以 4.2 的 `path` 方式为准**（已验证），库的 `content` 方式为封装层封装、未在本机验证。库另有 `update` method（修改 content）手册未覆盖。

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Docker.Container | list / get / start / restart / delete / create / signal / export / stats | name；list: limit, offset, type；create: profile(JSON, **POST**)；delete: name, force；signal: name, signal | list/delete 本机 114；stop 见 4.2 |
| SYNO.Docker.Container.Log | get | name, from, to, level, keyword, sort_dir, offset, limit（**POST**） | 容器日志；字段 `from`/`to`（连写） |
| SYNO.Docker.Container.Profile | get / import / export | name；import: name, profile(JSON, **POST**)；export: name, path | 容器配置导入/导出 |
| SYNO.Docker.Container.PkgProfile | get / list | name / — | 套件级容器配置 |
| SYNO.Docker.Container.Resource | get | — | 容器资源概览 |
| SYNO.Docker.Image | list / get / pull / delete / export / import | name, tag；import: path(**POST**) | list/delete 本机 114 |
| SYNO.Docker.Network | list / get / create / delete | name；create: name, driver, enable_ipv6, subnet, gateway, iprange | 字段 `iprange`（连写） |
| SYNO.Docker.Registry | get / search / create / set / using / delete / tags | search: q, offset, limit；create/set: name, url, enable_trust_SSC, [username, password]；tags: name | 双版本：search/create/set/using/delete=v1，tags=v2；字段 `enable_trust_SSC`、`q` |
| SYNO.Docker.Log | get | offset, limit, sort_dir, [keyword, level] | daemon 全局日志 |
| SYNO.Docker.Migrate | get / start | — | 迁移状态/启动 |
| SYNO.Docker.Utils | get / prune / version | — | prune 清未用资源 |

---

## 五、共享权限：SYNO.Core.Share.Permission

> ⚠️ 仅支持**共享文件夹级别**。传子目录路径会报 `{"success":false,"error":{"code":402}}`。子目录权限须走 SSH + synoacltool（第十二章）。

### 5.1 查询与设置权限 — SYNO.Core.Share.Permission

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

### 5.2 共享文件夹与加密其他 API

> 资料来源：[`synology-api`](https://github.com/N4S4/synology-api) 封装库源码反推（库取动态 `maxVersion`，参数名为源码字段名）。Permission 的 list(enum)/set 见 5.1。

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.Share | list / get / create / clone / delete / validate_set | name；create/clone: name, shareinfo(JSON: name, vol_path, desc, enable_recycle_bin, [hidden, share_quota, encryption, enc_passwd])；delete: name(数组) | 共享文件夹 CRUD；create/clone/validate_set 走 **POST** |
| SYNO.Core.Share.Crypto | encrypt / decrypt | name；decrypt: name, password(**POST**) | 文件夹加密/解密；http 链路下 password 经 `session.encrypt_params` 加密 |
| SYNO.Core.Share.Permission | list(find) / list_by_group / set_by_user_group | find: name, substr；list_by_group: name, user_group_type, share_type(JSON), additional(JSON) | find 按名称子串搜；list_by_group 反向按群组列权限 |
| SYNO.Core.Share.KeyManager.Store | init / verify / explore | init: share_path, passphrase；verify: passphrase | init/verify 源码 `raise NotImplementedError`（实际 403）；explore 列已存在 store |
| SYNO.Core.Share.KeyManager.AutoKey | list | — | 列出 AutoKey 密钥 |

---

## 六、用户管理：SYNO.Core.User

### 6.1 列出用户

```bash
# 列出系统用户
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Core.User&version=1&method=list&_sid=$SID"
```

> 完整 CRUD 需管理员会话 + JSON 请求体。无批量接口，批量操作需循环调用。

### 6.2 用户 CRUD 与密码策略

> 资料来源：[`synology-api`](https://github.com/N4S4/synology-api) 封装库源码反推（库取动态 `maxVersion`，参数名为源码字段名）。User.list 见 6.1。

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.User | get / create / set / delete | get: name；create/set: name, description, email, expired, cannot_chg_passwd, passwd_never_expire, password(**POST**)；delete: name | create/set 走 POST；http 链路下 password 经 `session.encrypt_params` 加密 |
| SYNO.Core.User.Group | join / join_status | join: name, join_group(JSON), leave_group(JSON)；join_status: task_id | 异步加退组，返 task_id 轮询 |
| SYNO.Core.User.PasswordPolicy | get / set | set: enable_reset_passwd_by_email, password_must_change, strong_password(JSON: min_length, mixed_case, included_numeric_char, included_special_char, …) | 密码强度策略 |
| SYNO.Core.User.PasswordExpiry | get / set | set: password_expire_enable, max_age, min_age, enable_login_prompt, never_expired_list(JSON) | 密码过期策略 |
| SYNO.Core.User.PasswordConfirm | auth | password(**POST**) | 返 `SynoConfirmPWToken`；敏感操作（建 root 计划任务等）二次确认 |
| SYNO.Core.User.UsernamePolicy | list | — | 返禁用用户名列表（root/admin/…） |

---

## 七、系统与存储 API

> 系统监控与存储运维相关 API。资料来源：[`synology-api`](https://github.com/N4S4/synology-api) 封装库源码反推（库取动态 `maxVersion`，参数名为源码字段名；`SYNO.Storage.CGI.Smart` 库写死 version=1）。

### 7.1 系统信息

```bash
# CPU/内存/磁盘利用率（分别取 data.cpu / data.memory / data.disk）
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Core.System.Utilization&version=1&method=get&_sid=$SID"

# 系统健康总览
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Core.System.SystemHealth&version=1&method=get&_sid=$SID"
```

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.System | info | [type=network / type=storage_v2] | 系统信息；CPU 温度取 `data.sys_temp` |
| SYNO.Core.System.Utilization | get | — | CPU/内存/磁盘利用率，分别取 `data.cpu`/`.memory`/`.disk` |
| SYNO.Core.System.SystemHealth | get | — | 系统健康总览 |

### 7.2 存储

```bash
# 列出所有磁盘
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Core.Storage.Disk&version=1&method=list&_sid=$SID"

# 磁盘 SMART 健康（version 固定 1）
curl -sk "$NAS/webapi/entry.cgi?api=SYNO.Storage.CGI.Smart&version=1&method=get_health_info&_sid=$SID"
```

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.Storage.Disk | list / get | list: —；get: id | 磁盘列表/详情，id 如 `sda` |
| SYNO.Core.Storage.Disk.FWUpgrade | get / start | start: id | 固件升级状态/触发 |
| SYNO.Core.Storage.Pool | list / get / set | get/set: id；set: [description] | 存储池 |
| SYNO.Core.Storage.Volume | list / get / set | get/set: volume_path；set: [description] | 卷，volume_path 如 `/volume1` |
| SYNO.Storage.CGI.Smart | get_health_info / get_smart_info / get_latest_online_drive_db_info 等 | — | **SMART 健康**；version 固定 1 |

---

## 八、系统服务与安全 API

> 资料来源：[`synology-api`](https://github.com/N4S4/synology-api) 封装库源码反推（库取动态 `maxVersion`，参数名为源码字段名）。同 API 多 method 用 `/` 合并；`get/set` 成对者合并为一行。`**kwargs` 表示库动态透传、无固定字段名。

### 8.1 套件中心 — core_package

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.Package | get / list | id, additional / ignore_hidden, additional | 套件详情/列表 |
| SYNO.Core.Package.Server | list | blforcereload, blloadothers | 可安装套件源 |
| SYNO.Core.Package.Setting | get / set / feasibility_check | enable_email, enable_autoupdate, default_vol, update_channel… | 套件中心设置 |
| SYNO.Core.Package.Setting.Volume | get | — | 默认安装卷 |
| SYNO.Core.Package.Info | get | — | 套件中心信息 |
| SYNO.Core.Package.Installation | install / status / upload / check / upgrade / get_queue / delete | operation, type, url, name, checksum, filesize, task_id, volume_path… | 安装/升级；install 走 batch compound（check+install）；upload 走 multipart |
| SYNO.Core.Package.Installation.Download | check | taskid | 下载安装校验 |
| SYNO.Core.Package.Uninstallation | uninstall | id, dsm_apps | 卸载 |

### 8.2 DSM 升级 — core_upgrade

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.Upgrade.AutoUpgrade.Security | get / set | enabled | 自动安全更新 |
| SYNO.Core.Upgrade.Cluster.{Patch,Server,Server.Download} | get / list / start | — | 集群补丁/服务器 |
| SYNO.Core.Upgrade.Group / Group.Download / Group.Setting | get / list / start / set | enabled | 升级分组 |
| SYNO.Core.Upgrade.GroupInstall / GroupInstall.Network | get / start / set | — | 批量安装 |
| SYNO.Core.Upgrade.Patch | get / list | — | 补丁 |
| SYNO.Core.Upgrade.PreCheck | get / start | — | 升级前检查 |
| SYNO.Core.Upgrade.RemoteAction | get / set | action | 远程操作 |
| SYNO.Core.Upgrade.JuniorModeData | get / set | — | Junior 模式 |

### 8.3 通知 — core_notification

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.Notification.Mail / Mail.Auth / Mail.Oauth / Mail.Profile.Conf | get / set | — | 邮件通知（含 OAuth/Profile） |
| SYNO.Core.Notification.Push / Push.AuthToken / Push.Mobile / Push.Webhook.Provider | get / set | — | 推送通知 |
| SYNO.Core.Notification.SMS / SMS.Provider | get / set | — | 短信通知 |
| SYNO.Core.Notification.Line | get / set | — | Line 通知 |
| SYNO.Core.Notification.Advance.{CustomizedData,FilterSettings,FilterSettings.Profile,FilterSettings.Template,Variables,WarningPercentage} | get / set | data | 高级通知规则 |
| SYNO.Core.Notification.CMS / CMS.Conf / Sysnotify | get / set | — | CMS/系统通知 |
| SYNO.DSM.PushNotification | requesttoken | — | 推送令牌 |

### 8.4 计划任务 — event_scheduler / task_scheduler

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.TaskScheduler | list / get / create / set / delete / run / set_enable | id, name, real_owner, owner, enable, schedule, extra, type | 脚本/蜂鸣/服务/回收站任务；create/set 在 `owner==root` 时切 `.Root` 变体 + SynoConfirmPWToken |
| SYNO.Core.TaskScheduler | get_history_status_list / get_history_log | id, timestamp | 任务历史 |
| SYNO.Core.EventScheduler | action / run / delete / set_enable / result_list / result_get_file / config_get / config_set | task_name, owner, event, operation, type, enable_output | 事件调度 |
| SYNO.Core.EventScheduler.Root | action | (同上) + SynoConfirmPWToken | root 任务 |
| SYNO.Core.Hardware.PowerSchedule | save / load | poweron_tasks, poweroff_tasks | 开关机计划 |

### 8.5 日志中心 — log_center

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.LogCenter.RecvRule / Client / History / Setting.Storage | list / get | — | 日志接收规则/客户端/历史/存储 |
| SYNO.LogCenter.Log | get_remotearch_subfolder | — | 远程日志归档 |
| SYNO.Core.SyslogClient.Log | list | — | 显示日志 |
| SYNO.Core.SyslogClient.Status | cnt_get / eps_get | — | 日志计数/EPS |
| SYNO.Core.SyslogClient.Setting.Notify / FileTransfer | get | — | 通知/文件传输 |

### 8.6 快照 — snapshot

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.Share.Snapshot | list / create / delete / set | name, filter, attr, additional, snapinfo, snapshots | 共享文件夹快照 |
| SYNO.Core.ISCSI.LUN | list / list_snapshot / take_snapshot / delete_snapshot | src_lun_uuid, description, is_locked, snapshot_uuids | LUN 快照 |
| SYNO.DR.Plan | list / sync | additional, plan_id, auto_remove, is_send_encrypted | 灾难恢复复制 |
| SYNO.Snap.Usage.Share | get_conf / set_conf / start / status / cancel / clean / get_report | — | 快照空间占用 |

### 8.7 系统设置 — core_system

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.System.ResetButton | get / set | enabled | Reset 按钮 |
| SYNO.Core.Region.Language | get / set | language | 语言 |
| SYNO.Core.Region.NTP / NTP.DateTimeFormat / NTP.Server | get / set | enabled | NTP/时间 |
| SYNO.Core.Theme.{AppPortalLogin,Desktop,FileSharingLogin,Image,Login} | get / set / list | — | 主题 |
| SYNO.Core.Desktop.{Defs,Initdata,JSUIString,PersonalUpdater,SessionData,Timeout,UIString,Upgrade} | get / set | timeout | 桌面设置 |
| SYNO.Core.{Help,UISearch,PersonalSettings,GroupSettings,UserSettings} | get / set / list | query, group | 个人/组设置 |
| SYNO.Entry.SocketIo | emit / listeners_count | — | Socket.IO |
| SYNO.License.HA | get_uuid / ha_remote_login / save_vault | — | HA 许可 |
| SYNO.Remote.Credential / Challenge / Info / Verifier | get / set | — | 远程凭证 |
| SYNO.VideoPlayer.{Subtitle,SynologyDrive.Subtitle} | get | — | 字幕 |

### 8.8 安全防护 — core_security

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.Security.AutoBlock.Rules | get / list / set / delete | rules | 自动封锁规则 |
| SYNO.Core.Security.DSM / DSM.Embed / DSM.Proxy | get / set | **kwargs | DSM 安全 |
| SYNO.Core.Security.DoS | get / set | **kwargs | DoS 防护 |
| SYNO.Core.Security.Firewall | get / set | **kwargs | 防火墙总配置 |
| SYNO.Core.Security.Firewall.Adapter / Conf / Geoip | get / set / list | **kwargs | 适配器/配置/GeoIP |
| SYNO.Core.Security.Firewall.Profile.Apply | start / status | profile_name | 应用配置文件 |
| SYNO.Core.Security.Firewall.Rules | get / list / set / delete | rules | 防火墙规则 |
| SYNO.Core.Security.Firewall.Rules.Serv | get / list | — | 服务规则 |

### 8.9 认证与 OTP — core_security_auth

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.SmartBlock / SmartBlock.{Device,Trusted,Untrusted,User} | get / set / list / delete | devices, entries, users | 智能封锁（设备/可信/不可信/用户） |
| SYNO.Core.OTP / OTP.Admin / OTP.EnforcePolicy / OTP.Ex / OTP.Mail | get / set | **kwargs | OTP 配置 |
| SYNO.Core.TrustDevice | get / list / delete | devices | 可信设备 |
| SYNO.Core.DisableAdmin | get / set | **kwargs | 禁用 admin |
| SYNO.Auth.RescueEmail | get / set / verify | email, code | 救援邮箱 |
| SYNO.API.Auth.Key / Key.Code | get / grant | — | API 密钥 |
| SYNO.API.Auth.Type | get | — | 认证类型 |
| SYNO.API.Auth.RedirectURI | check / run | — | 重定向 URI |

### 8.10 网络 — core_network

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.Network.Authentication / Authentication.Cert | get / set / delete | enable, profile, cert_path, key_path, ca_path | 网络认证 |
| SYNO.Core.Network.Ethernet.External | get / set | enable, ifname | 外部网口 |
| SYNO.Core.Network.IPv6 / IPv6.Router / IPv6.Router.Prefix | get / set | enable, type, mode, prefix, prefix_length | IPv6 |
| SYNO.Core.Network.MACClone | get / set | enable, ifname, mac | MAC 克隆 |
| SYNO.Core.Network.OVS | get / set | enable | OVS |
| SYNO.Core.Network.PPPoE.Relay | get / set | enable, server_ifname, client_ifname | PPPoE 中继 |
| SYNO.Core.Network.Router.Static.Route | list / get / create / delete | id, dest, gateway, mask, metric, ifname | 静态路由 |
| SYNO.Core.Network.TrafficControl.{Rules,RouterRules} | get / list / create / set / delete | id, protocol, upload_limit, download_limit, port, rules | 流量控制 |
| SYNO.Core.Network.UPnPServer | get / set | enable | UPnP |
| SYNO.Core.Network.WOL | get / set / wake | enable, mac, ifname | 网络唤醒 |

### 8.11 VPN — vpn

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.VPNServer.Settings.Config | status_load / load | serv_type(pptp/openvpn/l2tp) | VPN 服务配置 |
| SYNO.VPNServer.Management.Connection | enum | sort, dir, start, limit | 活动连接 |
| SYNO.VPNServer.Management.Log | load | start, limit, prtltype | VPN 日志 |
| SYNO.VPNServer.Management.Interface | load | — | 网络接口 |
| SYNO.VPNServer.Management.Account | load | action, start, limit | 权限设置 |
| SYNO.VPNServer.Settings.Certificate | export | serv_type=openvpn | OpenVPN 配置导出 |
| SYNO.Core.Network.VPN / VPN.OpenVPN.CA | get / set | reconnect, interval, ca_path | VPN 客户端 |
| SYNO.Core.Network.VPN.OpenVPNWithConf / .Certs | get / set | conf_path, username, password, cert_path, key_path, ca_path | OpenVPN 配置导入 |

### 8.12 证书 — core_certificate

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.Certificate.CRT | list / set / create / recreate / renew / delete | as_default, desc, id, ids | 证书 CRUD；renew 续期 |
| SYNO.Core.Certificate | import / export | id, desc, as_default, key, cert, inter_cert（multipart 文件） | 导入/导出（multipart/GET 直连，非 request_data） |
| SYNO.Core.Certificate.Service | set | settings(JSON: service/old_id/id) | 为服务绑定证书 |

### 8.13 防病毒 / 安全顾问 — antivirus / security_advisor

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.AntiVirus.Config / Schedule / Quarantine / General | get / load | start, limit | 防病毒配置/计划/隔离 |
| SYNO.SecurityAdvisor.Conf / Conf.Location / Conf.Checklist / Conf.Checklist.Alert | get / list / set | group=home | 安全顾问检查清单 |
| SYNO.SecurityAdvisor.LoginActivity / LoginActivity.User | list / get | offser(源码拼写), limit | 登录活动（字段 `offser` 非 offset） |
| SYNO.SecurityAdvisor.Report / Report.HTML | create / list / open | — | 安全报告 |
| SYNO.Core.SecurityScan.Conf | get / group_enum | argGroup | 安全扫描 |

### 8.14 外部设备 — core_external_device

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.ExternalDevice.Bluetooth / Bluetooth.Device / Bluetooth.Settings | get / set / list / connect / disconnect | enable, id, discoverable, name | 蓝牙 |
| SYNO.Core.ExternalDevice.DefaultPermission | get / set | permission | 默认权限 |
| SYNO.Core.ExternalDevice.Printer | list / get / clean | id | 打印机 |
| SYNO.Core.ExternalDevice.Printer.Driver | list / get | id | 打印驱动 |
| SYNO.Core.ExternalDevice.Printer.Network / Network.Host | list / create / delete | host, port, driver_id, id | 网络打印机 |
| SYNO.Core.ExternalDevice.Printer.OAuth | get / set | token | 打印机 OAuth |
| SYNO.Core.ExternalDevice.Printer.USB | list / get / release | id | USB 打印机 |
| SYNO.Core.ExternalDevice.Storage.EUnit / Storage.Setting | list / get / set | id, auto_format, auto_mount | 外部存储 |

### 8.15 DSM 杂项合集（概览）— service_apps / service_hw / service_user

> 这三个文件是 DSM 内部 API 的合集（各 50-67 个 method，多为 UI 内部用），列出覆盖的 API 域，按需查源码。

- **core_service_apps**：`SYNO.Core.{ACL, ActionPriv, ActionPriv.Role, AppNotify, AppPortal, AppPortal.AccessControl, AppPortal.Config, AppPortal.ReverseProxy, AppPriv, AppPriv.App, AppPriv.Rule, BackgroundTask, Backup.ED, BandwidthControl, Certificate.CSR, Certificate.LetsEncrypt, Certificate.LetsEncrypt.Account, Certificate.Tencent, CMS, CMS.Cache, CMS.Identity, CMS.Policy, CMS.ServerInfo, CMS.Task, CMS.Token, DDNS.Ethernet, DDNS.TWNIC, DSMNotify.MailContent, DSMNotify.Strings, DataCollect, DataCollect.Application, EW.Info, Factory.Config, Factory.Manutild, File, File.Thumbnail, FileServ.NFS.AdvancedSetting, FileServ.NFS.ConfBackup, FileServ.NFS.IDMap, FileServ.NFS.Kerberos, FileServ.NFS.SharePrivilege, FileServ.Rsync.Account, FileServ.SMB.ConfBackup, FileServ.SMB.Control, FileServ.SMB.MSDFS, Findhost}`
- **core_service_hw**：`SYNO.Core.{Group.ExtraAdmin, Group.Member, Group.ValidLocalAdmin, Hardware.LCM, Hardware.Led.Brightness, Hardware.MemoryLayout, Hardware.NeedReboot, Hardware.OOBManagement, Hardware.RemoteFanStatus, Hardware.SpectreMeltdown, Hardware.VideoTranscoding, ISCSI.FCTarget, ISCSI.Host, ISCSI.Lunbkp, ISCSI.Node, ISCSI.Replication, ISCSI.VMware, MediaIndexing, MediaIndexing.IndexFolder, MediaIndexing.MediaConverter, MediaIndexing.Scheduler, MediaIndexing.ThumbnailQuality, MyDSCenter, MyDSCenter.Account, MyDSCenter.Login, MyDSCenter.Logout, MyDSCenter.Purchase, NormalUser, NormalUser.LoginNotify, OAuth.Scope, OAuth.Server, Package.AutoUpgrade.Progress, Package.Control, Package.FakeIFrame, Package.Feed, Package.Legal.PreRelease, Package.Log, Package.MyDS, Package.MyDS.Purchase, Package.Progress, Package.Screenshot, Package.Screenshot.Server, Package.Setting.Update, Package.Thumb, Package.Thumb.Server, PersonalNotification.Device, PersonalNotification.Event, PersonalNotification.Filter, PersonalNotification.Mobile, PersonalNotification.Settings, PhotoViewer, PortForwarding, PortForwarding.Compatibility, PortForwarding.RouterInfo, PortForwarding.RouterList, PortForwarding.Rules.Serv}`
- **core_service_user**：`SYNO.Core.{Promotion.Info, Promotion.PreInstall, QuickConnect.Hostname, QuickConnect.RegisterSite, QuickConnect.SNI, QuickConnect.Upnp, QuickStart.Info, QuickStart.Install, Report, Report.Analyzer, Report.Analyzer.File, Report.Analyzer.Share, Report.Config, Report.History, Report.Redirect, Report.Util, ResetAdmin, SecurityScan.Operation, Service.Conf, Service.PortInfo, Share.Crypto, Share.Crypto.Key, Share.CryptoFile, Share.KeyManager.AutoKey, Share.KeyManager.Key, Share.KeyManager.MachineKey, Share.KeyManager.Store, Share.Migration, Share.Migration.Task, Share.Permission, Share.PermissionReport, Share.ShellFile, Sharing, Sharing.Initdata, Sharing.Login, Sharing.Session, SupportForm.Form, SupportForm.Log, SupportForm.Service, Synohdpack, SyslogClient.PersonalActivity, Tuned, User.Group, User.PasswordExpiry, User.PasswordMeter, User.PasswordPolicy, User.UsernamePolicy, Virtualization.Host.Capability, VolEncKeepKey, Web.DSM.External, Web.Security.HTTPCompression, Web.Security.TLSProfile}`

---

## 九、媒体与协作套件 API

> 资料来源：[`synology-api`](https://github.com/N4S4/synology-api) 封装库源码反推（库取动态 `maxVersion`，参数名为源码字段名）。

### 9.1 Synology Photos — photos（SYNO.Foto.* / SYNO.FotoTeam.*）

> 个人空间 `SYNO.Foto.*`、团队空间 `SYNO.FotoTeam.*`。走 `_foto_get` 的 API（Browse.Unit/Diff/Thumbnail/Favorite/BackgroundTask.Info/Migration/Streaming/Download）需 `X-Syno-Token` + `Cookie: id={sid}`，非标准 `X-SYNO-TOKEN`；Upload 走 multipart、version 硬编码 1。

| API（Foto / FotoTeam 同构） | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| Foto.UserInfo | me | — | 用户信息 |
| Browse.Folder | get / list / count | id, limit, offset, additional | 文件夹（FotoTeam 同） |
| Browse.Album | get / list / delete / set_name | id, name, additional | 相册 |
| Browse.ConditionAlbum | suggest / create / set_condition | user_id, keyword, condition, name | 条件相册 |
| Browse.NormalAlbum | get / create | id, name, item | 普通相册 |
| Browse.Category | get | — | 分类 |
| Browse.Concept / GeneralTag / Geocoding | list / count / get / create | offset, limit, id, name | 概念/标签/地理编码 |
| Browse.Person | list / count / get / set / merge / separate / list_face | id, name, source_ids, target_id, face_id | 人物（合并/拆分） |
| Browse.Item | list / count / get / set / set_favorite | folder_id/album_id/person_id, offset, limit, sort_by, id, description, rating, favorite | 多功能：按文件夹/相册/人物列出；id 语义随 method 变 |
| Browse.RecentlyAdded / Timeline / SimilarTimeline / SimilarItem | list / count / get | offset, limit, id | 最近/时间线/相似 |
| Browse.Unit | count / get / get_thumbnail_status | id, id_item, additional | 单元（_foto_get） |
| Browse.Diff | get / get_version | diff_version, limit, version_time | 差异（_foto_get） |
| Sharing.Passphrase | set_shared / update / get / get_permission | policy, album_id/folder_id, passphrase, expiration, permission | 分享（两轮：先 set_shared 再 update 设权限） |
| Sharing.Misc | list_user_group | team_space_sharable_list | 可分享用户/组 |
| Search.Filter / Search.Search | list / suggest | keyword | 搜索 |
| Setting.{Guest,Admin,User,TeamSpace,Wizard,MobileCompatibility} | get / set | **kwargs | 设置 |
| Index | get | — | 索引状态 |
| Download | download | unit_id | 下载 |
| Upload.Item | upload | name, duplicate, folder, uploadDestination, file（multipart） | 上传（v=1） |
| Thumbnail | get | id, type=unit, size | 缩略图（_foto_get） |
| Favorite | list | offset, limit | 收藏（DSM 7.3 可能 405/103） |
| BackgroundTask.Info | list_user_task / get_status / get_error_detail / abort_task / clear_completed_task | id | 后台任务（_foto_get） |
| Migration | get_status | — | 迁移状态（_foto_get） |
| Streaming | streaming | id, quality | 视频流（需 Token+Cookie） |

### 9.2 AudioStation — audiostation

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.AudioStation.Info | getinfo | — | 信息 |
| SYNO.AudioStation.Playlist | list | library, limit | 播放列表 |
| SYNO.AudioStation.RemotePlayer | list / getplaylist / control | type, additional, id, action(play/stop/next/prev) | 远程播放器控制 |
| SYNO.AudioStation.Pin | list | — | 固定歌曲 |
| SYNO.AudioPlayer.Stream | stream / transcode | id | 音频流/转码 |

### 9.3 DownloadStation — downloadstation

> 动态 `SYNO.DownloadStation{ver}.*`：默认空串=`SYNO.DownloadStation.*`；`download_st_version=2`=`SYNO.DownloadStation2.*`。

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.DownloadStation.Info | getinfo / getconfig / setserverconfig | bt_max_download, bt_max_upload, emule_enabled, default_destination… | 服务器配置 |
| SYNO.DownloadStation.Schedule | getconfig / setconfig | enabled, emule_enabled | 计划 |
| SYNO.DownloadStation(.2).Task | list / get / create / delete / pause / resume / edit | id, additional, limit, offset, uri, url, destination, force_complete | 任务 CRUD；create 文件上传走 type=file+multipart |
| SYNO.DownloadStation2.Task.Source | download | id | 任务源 |
| SYNO.DownloadStation2.Task | delete_condition / pause_condition / resume_condition | type, type_inverse, status | 批量清/暂停/恢复 |
| SYNO.DownloadStation(.2).Task.List / Task.List.Polling | get / download | list_id, file_indexes, destination | 任务列表轮询/下载 |
| SYNO.DownloadStation.Statistic | getinfo | — | 统计 |
| SYNO.DownloadStation.RSS.Site / (.2).RSS.Feed / (.2).RSS.Filter | list / refresh / add / set / delete | id, offset, limit, feed_id, name, match, not_match, destination, is_regex | RSS |
| SYNO.DownloadStation(.2).BTSearch | start / list / get / clean / getModule | keyword, module, taskid, offset, limit, sort_by, filter_category | BT 搜索 |

### 9.4 NoteStation — notestation

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.NoteStation.Setting | get / init | — | 设置 |
| SYNO.NoteStation.Info | get | — | 信息 |
| SYNO.NoteStation.Notebook / Tag / Shortcut / Todo / Smart | list | offset, limit | 笔记本/标签/快捷方式/待办/智能 |
| SYNO.NoteStation.Note | list / get / idle | object_id | 笔记 |

### 9.5 Calendar — calendar

> 所有调用经 `_cal_request`：字符串值自动加引号，JSON 经 `json.dumps`。

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Cal.Cal | create / list / get / set / delete | cal_id, cal_type, cal_displayname, cal_description, cal_color, is_hidden_*, notify_* | 日历 CRUD |
| SYNO.Cal.Event | create / list / get / set / delete | cal_id, evt_id, summary, is_all_day, tz_id, dtstart, dtend, is_repeat_evt, repeat_setting, participant | 事件 |
| SYNO.Cal.Todo | create / list / get / set / delete | evt_id, original_cal_id, summary, due, percent_complete, priority_order | 待办 |
| SYNO.Cal.Setting | get / set | date_format, default_cal, time_zone, week_start_day… | 设置 |
| SYNO.Cal.Timezone | list | — | 时区 |
| SYNO.Cal.Contact | list | list_dsm_only | 联系人 |

### 9.6 Chat — chat（SYNO.Chat.*）

> 24 个子 API。`SYNO.Chat.External` 走 token 模式（无 DSM session），其余走 DSM session。

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| Chat.Channel / Channel.Named / Channel.Preference | list / close / archive / view / create / join / invite / disjoin / get | channel_id, name, type, user_ids, channel_key_encs, last_view_at | 频道 |
| Chat.User / User.Status / User.Avatar / User.Preference | list / get | user_id | 用户 |
| Chat.Post | list / search / create / pin / unpin / thread_list | channel_id, limit, offset, keyword, message, file_id | 消息；create 带文件走 multipart |
| Chat.Post.Attachment / Reaction / Reminder / Schedule | list / set / get / delete / create | post_id, sticker_name, remind_at, send_at, cronjob_id, message | 附件/反应/提醒/定时 |
| Chat.Webhook.{Incoming,Outgoing,Slash,BuiltIn,Broadcast} | list / create / delete | name, channel_id, url, command, events, icon_url | Webhook |
| Chat.Chatbot / Bot | list / set / delete | user_id, url, purpose, nickname | 机器人 |
| Chat.External | incoming / channel_list / user_list / post_list / post_file_get / chatbot | token, payload, channel_id, post_id, user_ids | token 模式（ChatBot 类） |
| Chat.Sticker | list | — | 贴纸 |
| Chat.Admin.Setting / Chat.App / Chat.Misc | get | — | 管理/应用/杂项 |

### 9.7 CloudSync — cloud_sync（单一 SYNO.CloudSync API，22 method）

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.CloudSync | get_config / list_conn / get_connection_setting / get_property / get_conn_auth_info / get_log / list_sess / get_selective_sync_config / get_selective_folder_list / get_recently_change | connection_id, session_id, offset, keyword, date_from, date_to, is_tray, group_by | 连接/任务查询 |
| SYNO.CloudSync | set_global_config / set_personal_config / set_connection_setting / set_schedule_setting / set_session_setting / set_selective_sync_config | repo_vol_path, worker_count, sync_mode, max_upload_speed, storage_class, is_enabled_schedule, sync_direction, filtered_paths | 配置设置 |
| SYNO.CloudSync | pause / resume / unlink_connection / unlink_session / create_session / test_task_setting | connection_id, session_id, conn_info(batch) | 控制；create_session/test 走 batch_request |

### 9.8 MailPlus Server — mailplus_server

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.MailPlusServer.{MailPlus,SMTP.General,SMTP.Security,IMAP_POP3,Security,Report} | get | — | 配置查询 |
| SYNO.MailPlusServer.ServerList | list | — | 服务器列表 |
| SYNO.MailPlusServer.Audit.{AdminLog,TransactionLog} / Queue | list | offset, limit | 审计日志/队列 |

### 9.9 OAuth — oauth

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.OAUTH.Client / Token | list | offset, limit | 客户端/令牌 |
| SYNO.OAUTH.Log | list | action, offset, limit | 日志（多带 `action:'list'`） |

### 9.10 Universal Search — universal_search

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Finder.FileIndexing.Search | search | keyword, indice, criteria_list, from, size, fields, file_type, sorter_field, sorter_direction | 全局搜索 |

### 9.11 USB Copy — usb_copy

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.USBCopy | get_global_setting / get_log_list / get / enable / disable | id, offset, limit, log_filter | USB 拷贝任务 |

### 9.12 WebStation — web_station

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.WebStation.{Status,Default,PHP,Python} | get | — | 状态/默认/PHP/Python |
| SYNO.WebStation.HTTP.VHost / PHP.Profile / Python.Profile / ErrorPage / WebService.Portal / WebService.Service / Package / Shortcut / Task | list | — | 虚拟主机/Profile/服务 |

---

## 十、备份与虚拟化 API

> 资料来源：[`synology-api`](https://github.com/N4S4/synology-api) 封装库源码反推（库取动态 `maxVersion`，参数名为源码字段名）。

### 10.1 备份 — core_backup（含 S2S / DR.Node）

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Backup.Repository | get / list | task_id | 备份仓库 |
| SYNO.Backup.Task | list / get / status / backup / cancel / suspend / discard / resume / delete | task_id, task_state, blOnline, additional, is_remove_data, task_id_list | 备份任务（运行/取消/挂起/删除） |
| SYNO.Backup.Target | error_detect / error_detect_cancel | detect_data, sessId, sessKey, task_id | 完整性检查 |
| SYNO.SDS.Backup.{Client,Server}.Common.{Log,Statistic} | list / get | limit, offset, filter_keyword, filter_target_id, task_id | 客户端/服务器日志统计 |
| SYNO.Backup.Service.VersionBackup.{Target,Config} | list / get / set / detail | target_id, parallel_backup_limit | 版本备份（Vault） |
| SYNO.Backup.App / App.Backup / App.Restore | get_icon / list / mysql_check / surveillance_check | — | 套件备份/恢复 |
| SYNO.Backup.Config.AutoBackup | get / set / list / backup / restore / status / download_private_key / upload_private_key / get_meta | — | 自动备份配置 |
| SYNO.Backup.Config.Backup / Config.Restore | download / list / start / status / check / delete / upload / list_conflict | — | 配置备份/恢复 |
| SYNO.S2S.Server | get / set | — | S2S 服务器 |
| SYNO.DR.Node | info / check_and_reset / reset / get_net_info / get_remote_net_info / test_connection / test_download_speed / test_privilege / test_sync_speed | — | DR 节点 |
| SYNO.DR.Node.Credential | create / delete / get / list / set / relay / reverse_create / temp_create / temp_reverse_create / test_create / test_reverse_create / test_set | — | DR 凭证 |
| SYNO.DR.Node.Session | create / delete / find / get / temp_create | — | DR 会话 |
| SYNO.DisasterRecovery.Log | list / clear / export | — | DR 日志 |
| SYNO.DisasterRecovery.Retention | get / set / delete / info / get_timezone / set_timezone / check_worm_lockable / get_worm_lock / set_worm_lock / clear_worm_lock_notify_time / notify_worm_lock_disable | — | DR 保留策略（WORM 锁） |

### 10.2 Active Backup for Business — core_active_backup

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.ActiveBackup.Setting | list / set | settings, cert_use_package | 设置（并发设备/保留策略/限流/证书） |
| SYNO.ActiveBackup.Inventory | list | — | 虚拟机监控器 |
| SYNO.ActiveBackup.Overview | list_device_transfer_size | time_start, time_end | 设备传输量 |
| SYNO.ActiveBackup.Task | list / backup / cancel / remove | task_ids, trigger_type, load_status, load_result, load_devices, load_versions, filter | 任务（运行/取消/删除） |
| SYNO.ActiveBackup.Version | delete | task_id, version_ids | 删除版本 |
| SYNO.ActiveBackup.Log | list_log / list_result / list_result_detail | offset, limit, filter, task_id, result_id | 日志/历史/详情 |
| SYNO.ActiveBackup.Share | list_storage | — | 存储列表 |

### 10.3 Active Backup for Microsoft 365 — abm

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.ActiveBackupOffice365 | list_tasks / get_general_log / get_all_log / get_task_setting / get_worker_count / update_worker_count / set_task_setting / backup_task / cancel_task / delete_task / relink_task | task_id, offset, limit, key_word, backup_job_worker_count, event_worker_count, start_hour, repeat_every_hours, task_info, selected, region | M365 备份任务 |

### 10.4 虚拟化 — virtualization（SYNO.Virtualization.API.*）

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| Task.Info | list / get / clear | taskid | 任务 |
| Network / Storage / Host | list | — | 网络/存储/主机 |
| Guest | list / get / set / delete | guest_name, guest_id, additional, autorun, description, new_guest_name, vcpu_num, vram_size | 虚拟机 |
| Guest.Action | poweron / poweroff / shutdown | guest_name, guest_id, host_id, host_name | 电源操作 |
| Guest.Image | list / create | image_name, image_id, auto_clean_task, storage_ids, storage_names, type, ds_file_path | 映像；**`delete_image` 源码误用 `method=create`** |

### 10.5 iSCSI — core_iscsi

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.ISCSI.LUN | create / delete / list / get / set / clone / stop_clone / map_target / unmap_target | uuid, uuids, name, type, location, size, emulate_*, can_snapshot, dev_attrib, src_lun_uuid, dst_lun_name, target_ids | LUN CRUD/克隆/映射 |
| SYNO.Core.ISCSI.Target | create / delete / list / get / set / enable / disable / map_lun / unmap_lun | target_id, name, iqn, auth_type, max_sessions, user, mutual_user, lun_uuids | Target CRUD/映射 |

### 10.6 Surveillance Station — surveillancestation（SYNO.SurveillanceStation.*）

> method 众多（~300，PascalCase 命名），多用于 UI 内部。下表按 API 分组列代表 method；空参数（`—`）多为无业务过滤的查询。完整 method 见源码 `surveillancestation.py`。

| API | 代表 method | 关键参数 | 备注 |
|-----|------------|----------|------|
| Info | GetInfo | — | 套件信息 |
| Camera | Save/List/GetInfo/ListGroup/GetSnapshot/Enable/Disable/GetCapabilityByCamId/GetOccupiedSize/CheckCamValid/GetLiveViewPath | cameraId, camId, idList | 相机管理 |
| Camera.Event | AudioEnum/AlarmEnum/MotionEnum/MDParamSave/ADParamSave/DIParamSave/TDParamSave/AlarmStsPolling | camId | 事件枚举/参数 |
| Camera.Group / Camera.Import / Camera.Wizard / Camera.Search / Camera.Status | Enum/Save/Delete/Save/ArchiveCamEnum/QuickCreate/CheckQuota/FormatSDCard/Start/GetInfo/OneTime | privCamType, groupList, id | 分组/导入/向导/搜索/状态 |
| PTZ / PTZ.Preset / PTZ.Patrol | Move/Zoom/Focus/Iris/AutoFocus/AbsPtz/Home/AutoPan/ObjTracking/List/GoPreset/SetPreset/DelPreset/Execute/SetHome/Enum/Load/Save/Delete/Run/Stop | — | 云台/预置/巡航 |
| ExternalRecording | Record | — | 外部录制 |
| Recording / Recording.Bookmark / Recording.Export / Recording.Mount | List/Delete/DeleteFilter/DeleteAll/ApplyAdvanced/CountByCategory/Keepalive/Trunc/LoadAdvanced/Lock/Unlock/LockFilter/UnlockFilter/Download/CheckEventValid/Stream/RangeExport/GetRangeExportProgress/OnRangeExportDone/SaveBookmark/DeleteBookmark/Load/CheckName/CamEnum/CheckAvailableExport/Save/GetEvtExpInfo | — | 录像/书签/导出 |
| CMS / CMS.GetDsStatus | Redirect/Load/ApplyOption/GetInfo/DoSyncData/CheckSambaEnabled/BatCheckSambaService/GetMDSnapshot/GetCMSStatus/EnableSamba/NotifyCMSBreak/LockSelf/EnableCMS/UnPair/GetFreeSpace/Lock/Test/Logout/Pair/Login/Save | — | CMS 集群管理 |
| Log / License / Stream | CountByCategory/Clear/List/GetSetting/SetSetting/Load/CheckQuota/EventStream | data, num_only | 日志/许可/流 |
| ActionRule | Save/DownloadHistory/SendData2Player/DeleteHistory/List/Disable/Enable/ListHistory/Delete | idList, Start, limit | 动作规则 |
| Emap / Emap.Image | List/Load/Load | — | 电子地图 |
| Notification / Notification.SMS / Notification.SMS.ServiceProvider / Notification.PushService / Notification.Schedule / Notification.Email | GetRegisterToken/SetCustomizedMessage/SetVariables/GetVariables/SetAdvSetting/GetAdvSetting/SendTestMessage/GetSetting/SetSetting/ListMobileDevice/SetSetting/SendVerificationMail/Get*/Set*/SetBatchSchedule/Create/List/Delete | data, ctrlVal, camId | 通知（短信/推送/邮件/计划） |
| Alert / Alert.Setting | RecServClear/EventCount/ClearSelected/Enum/RecServerEnum/Unlock/Trigger/EventFlushHeader/Lock/RecServerEventCount/Save | — | 警报/分析 |
| SnapShot | ChkFileExist/Edit/CountByCategory/ChkContainLocked/UnlockFiltered/List/Unlock/TakeSnapshot/GetSetting/DeleteFiltered/LoadSnapshot/Lock/Download/SaveSetting/Save/ChkSnapshotValid | — | 快照 |
| VisualStation / VisualStation.Layout / VisualStation.Search | Enable/ReqNetConfig/Lock/Enum/Unlock/Disable/Delete/Enum/Save/Delete/InfoGet/Start/SearchIP/Stop | — | VisualStation |
| AxisAcsCtrler / AxisAcsCtrler.Search | GetUpdateInfo/CountByCategoryCardHolder/EnumLogConfig/GetCardholderPhoto/CountByCategoryLog/EnumCardHolder/RetrieveLastCard/EnableCtrler/AckAlarm/SaveLogConfig/Save/DownloadLog/GetDoorNames/TestConnect/Enum/SaveCardHolder/ListDoor/ClearLog/ListPrivilege/DoorControl/SavePrivilege/ListLog/Delete/Retrieve/BlockCardHolder/CountByCategory/Start/GetInfo | — | Axis 门禁 |
| DigitalOutput / ExternalEvent / IOModule / IOModule.Search | Enum/Save/PollState/CtrlLED/Trigger/Enum/EnumPort/EnumVendorModel/Save/Enable/Disable/Delete/TestConn/GetCap/PortSetting/PollingDI/PollingDO/GetDevNumOfDs/CountByCategory/Start/InfoGet | ctrlVal, camId | 数字输出/外部事件/IO 模块 |
| HomeMode | Switch/GetInfo | — | 家庭模式 |
| Transactions.Device / Transactions.Transaction | Enum/Lock/Unlock/Delete/Begin/Complete/Cancel/AppendData | — | 交易会话 |
| Archiving.Pull | SaveTask/LoginSourceDS/DeleteTask/ListTask/Enable/DisableTask/BatchEditTask/BatchEditProgress/GetBatchEditProgress/BatchEditProgressDone | — | 归档拉取 |
| YoutubeLive | Load/Save/CloseLive | — | YouTube 直播 |
| IVA / IVA.Report / IVA.Recording / IVA.TaskGroup | ListTask/SaveTask/DeleteTask/EnableTask/DisableTask/ResetPplCntCounter/GetCount/GetReport/List/Delete/GetAnalyticResult/Lock/Unlock/List/Create/Edit/Delete/Enable/Disable/GetPeopleCount/ResetPeopleCount | — | 智能视频分析（人流量） |
| Face / Face.Result | ListTask/DeleteTask/EnableTask/DisableTask/ListPlayableTsk/CreateFaceGroup/DeleteFaceGroup/EditFaceGroup/ListFaceGroup/CountFaceGroup/DetectImageFace/CreateRegisteredFace/DeleteRegisteredFace/EditRegisteredFace/ListRegisteredFace/CountRegisteredFace/SearchRegisteredFace/List/Delete/Lock/Unlock/GetEventInfo/GetAnalyticResult/Correct/MarkAsStranger | — | 人脸识别 |

---

## 十一、目录服务 API

> 资料来源：[`synology-api`](https://github.com/N4S4/synology-api) 封装库源码反推（库取动态 `maxVersion`，参数名为源码字段名）。

### 11.1 SSO / LDAP 目录 — core_directory

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.Directory.Azure.SSO | get / set | enable, tenant_id, client_id | Azure AD SSO |
| SYNO.Core.Directory.OIDC.SSO | get / set | enable, issuer, client_id | OIDC SSO |
| SYNO.Core.Directory.SSO.{CAS,SAML,SAML.Metadata,SAML.Status,Setting,Status,Profile,utils,WebSphere.SSO} | get / set | enable, settings, metadata, profile, data | SSO 全套（SAML/CAS/WebSphere/OIDC） |
| SYNO.Core.Directory.Domain.Conf / Domain.Trust | get / set | conf, trust | 域配置/信任 |
| SYNO.Core.Directory.LDAP.{BaseDN,Login.Notify,Profile,Refresh,User} | get / set | base_dn, enable, profile, user | LDAP 配置 |

### 11.2 目录服务诊断 — directory_service_check

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.DirectoryServiceCheck.{Common,Debug,Domain,DomainJoin,DomainService,DomainValidation,LDAP,Progress} | get / set | action | 目录服务诊断（8 子 API，均 get/set+action） |

### 11.3 Active Directory — directory_server

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.ActiveDirectory.Info | get | — | AD 信息 |
| SYNO.ActiveDirectory.Directory | list / delete | action, basedn, limit, objectCategory, offset, scope, dnList | AD 对象浏览/删除 |
| SYNO.ActiveDirectory.User | create / set | logon_name, email, password, located_dn, description, account_is_disabled, cannot_change_password, change_password_next_logon, password_never_expire | AD 用户 |
| SYNO.ActiveDirectory.Group | create / conflict | name, located_dn, description, type, scope, email | AD 群组 |
| SYNO.Auth.ForgotPwd | send | user | 忘记密码 |
| SYNO.Entry.Request | request | compound, mode, stop_when_error | 复合请求（改密/加组成员，内嵌 User.set/Group.Member.add/Polling.get） |
| SYNO.Core.Directory.Domain | update_start / update_status | domain_name, task_id | 域记录更新（异步，轮询 task_id） |

### 11.4 LDAP Server — ldap_server

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.Directory.LDAP / LDAP.BaseDN / LDAP.Profile | get / list | — | LDAP 配置/BaseDN/Profile |
| SYNO.Core.Directory.LDAP.User | list | offset, limit | LDAP 用户 |
| SYNO.Core.Directory.LDAP.Refresh | set | — | 刷新 |
| SYNO.Core.Directory.LDAP.Login.Notify | get | — | 登录通知 |

### 11.5 DHCP — dhcp_server

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Network.DHCPServer / DHCPServer.Vendor / DHCPServer.PXE | get | ifname | DHCP 配置 |
| SYNO.Network.DHCPServer.ClientList | list | ifname | 客户端列表 |
| SYNO.Network.DHCPServer.Reservation | get | ifname | 地址预留 |
| SYNO.Core.TFTP | get | — | TFTP |
| SYNO.Core.Network.Bond / Ethernet | list | — | 网络绑定/网口 |

### 11.6 群组 — core_group

| API | method | 关键参数 | 备注 |
|-----|--------|----------|------|
| SYNO.Core.Group | list / set / create / delete | name, new_name, description, offset, limit, name_only, type | 群组 CRUD |
| SYNO.Core.Group.Member | list / change | group, ingroup, add_member, remove_member | 成员管理 |
| SYNO.Core.BandwidthControl | get / set | name, owner_type, bandwidths | 带宽限制 |
| SYNO.Core.Quota | get / set | name, subject_type, support_share_quota, group_quota | 配额 |
| SYNO.Core.Share.Permission | list_by_group / set_by_user_group | name, user_group_type, share_type, additional, permissions | 按群组列/设权限（对照 5.1） |

### 11.7 Synology Drive 管理台 — drive_admin_console（SYNO.SynologyDrive.*）

| API | 代表 method | 关键参数 | 备注 |
|-----|------------|----------|------|
| SynologyDrive | get_status / check_user / Info / DSM / Statistics / Activation | — | Drive 状态/信息 |
| SynologyDrive.Config / Settings / NodeLockingOption | get / list / get | pause_duration | 配置/设置/节点锁 |
| SynologyDrive.Connection | summary / list | — | 连接概览/活动 |
| SynologyDrive.Share / TeamFolders | list_active / list / list | — | 共享/团队文件夹 |
| SynologyDrive.{Profiles,Privilege,Tasks,Labels,Notifications,Log,DBUsage,Node.Delete,Migration.UserHome,Index} | list / get / set_native_client_index_pause | offset, limit, user, keyword, datefrom, dateto, username, target, pause_duration | 用户同步/权限/任务/标签/通知/日志/DB/迁移/索引 |
| SynologyDriveShareSync.{Connection,Config} | list / get | — | ShareSync 连接/配置 |
| C2FS.Share | list | — | C2 文件共享 |

---

## 十二、SSH CLI 工具

### 12.1 SSH 连接（Go）

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

### 12.2 synoacltool — 子目录 ACL

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

### 12.3 synouser — 用户管理（需 sudo）

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

### 12.4 synogroup — 群组管理（需 sudo）

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

### 12.5 sudo 密码传递（Go）

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

### 12.6 逻辑路径 → 物理路径

DSM 逻辑路径 `/data/project/subdir` → 物理路径 `/volume1/data/project/subdir`：

```bash
# 查找共享所在的卷
for v in /volume1 /volume2 /volume3 /volume4 /volume5; do
    [ -d "$v/<共享名>" ] && { echo "$v"; break; }
done
```

---

## 十三、Web 免登录跳转 FileStation

### 13.1 实现原理

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

### 13.2 launchApp 标识对比

| 标识 | 效果 |
|------|------|
| `SYNO.SDS.FileStation.Application` | 打开 DSM 桌面，**不**进入 FileStation |
| `SYNO.SDS.App.FileStation3.Instance` | 直接打开 FileStation 应用 ✅ |

### 13.3 launchParam 文件夹定位

| 参数 | 格式 | 示例 |
|------|------|------|
| `launchParam` | `openfile=<路径>/` | `openfile=/data/项目2024_01/` |

- 路径为**共享文件夹相对路径**（去掉 `/volume1` 前缀）
- 末尾 `/` 必须
- 完整参数需 URL 编码：`openfile%3D%2Fdata%2F%E9%A1%B9%E7%9B%AE%2F`

### 13.4 完整实现（JavaScript）

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

### 13.5 关键前提

- **HTTPS 同域部署**：应用与 DSM 同域名，DSM 反向代理（HTTPS 8443→Docker :8080）
- **Cookie 同域共享**：cookie 按域名跨端口，`webman/login.cgi` 返回的 `id` cookie 可被 `:5001` 端口使用
- **密码加密存储**：前端不存储密码，后端 AES-256-GCM 加密，密钥通过 `ENCRYPTION_KEY` 环境变量注入
- **隐藏 iframe** 优于弹窗：避免浏览器弹窗拦截，避免登录 JSON 响应（`{"success":true}`）闪现

---

## 十四、Python 封装库 synology-api

> 第三方 Python 封装 [`synology-api`](https://github.com/N4S4/synology-api)（作者 Renato Visaggio / N4S4，MIT）。对底层 REST API 做面向对象封装，自动处理 `SYNO.API.Auth` 登录、session、token 续期。当前已实现 300+ API。本文档第一至十一章为直接调 REST 的「裸 API」用法；若用 Python 开发，本库可省大量样板代码。
>
> ⚠️ 非群晖官方维护，无 SLA。生产关键路径建议先理解底层（见一至十一章）。

### 14.1 安装

```bash
pip3 install synology-api
# 或从源码安装最新版
pip3 install git+https://github.com/N4S4/synology-api
```

仅支持 Python 3。

### 14.2 基本用法

每个 API 域对应一个类，构造时传 NAS 连接信息，库内部自动完成登录与 token 续期：

```python
from synology_api.filestation import FileStation
from synology_api.downloadstation import DownloadStation

fs = FileStation(
    'Synology Ip',        # NAS IP
    'Synology Port',      # 端口（5000 http / 5001 https）
    'Username',
    'Password',
    secure=False,         # True=https，False=http
    cert_verify=False,    # 自签名证书校验：False 跳过（对应 curl -k）
    dsm_version=7,        # DSM 版本：6 或 7
    debug=True,           # 打印请求/响应，排查用
    otp_code=None         # 启用 2FA 时填 6 位验证码
)

fs_info = fs.get_info()
```

构造参数与本文档「裸 API」的对应关系：

| 构造参数 | 对应裸 API 概念 |
|----------|----------------|
| `secure` | `https://`（5001）vs `http://`（5000） |
| `cert_verify=False` | curl `-k` / Go `InsecureSkipVerify: true` |
| `dsm_version=7` | 触发 `enable_syno_token=yes` + `X-SYNO-TOKEN` 头 |
| `otp_code` | 登录 URL 的 `&otp_code=` 参数 |

`fs.get_info()` 即封装 `SYNO.FileStation.Info` 的 `get` 方法（对照第三章 3.1），返回：

```json
{
  "data": {
    "hostname": "MyCloud",
    "is_manager": true,
    "support_sharing": true,
    "support_vfs": true,
    "support_virtual_protocol": ["cifs", "nfs", "iso"],
    "system_codepage": "enu",
    "uid": 1026
  },
  "success": true
}
```

### 14.3 主要模块

各模块对应一个 SYNO API 域。完整清单（300+）见官方 [Supported APIs](https://n4s4.github.io/synology-api/docs/apis)，多数方法无单独文档，建议 `dir(obj)` 或查源码探索。

| 模块导入路径 | 对应 SYNO API | 说明 |
|--------------|---------------|------|
| `synology_api.filestation.FileStation` | `SYNO.FileStation.*` | 文件/目录增删改查、上传下载（对照第三章） |
| `synology_api.downloadstation.DownloadStation` | `SYNO.DownloadStation.*` | 下载任务管理 |
| `synology_api.sys_info.CoreSysInfo` | `SYNO.Core.System.*` | 系统信息（网络、存储、CPU） |
| `synology_api.core.Core` | `SYNO.Core.*` | 用户/群组/共享等核心（对照五、六章） |
| `synology_api.photos.Photos` | `SYNO.FotoStation/FotoTeam.*` | Synology Photos |


### 14.4 库 vs 裸 API 选型对照

| 维度 | 裸 REST（本文档一至十一章） | synology-api 库 |
|------|--------------------------|----------------|
| 语言 | 任意（curl/Go/Node/…） | 仅 Python |
| 登录/续期 | 手动管 sid，遇 119 重登 | 库自动处理 |
| SynoToken | 手动 `enable_syno_token` + 头 | 库自动注入 |
| 异步任务（CopyMove） | 手动 taskid 轮询（3.4） | 库方法封装 |
| 可控性 | 最高 | 受库实现约束 |
| 适用场景 | 后端集成、跨语言、生产 | 快速脚本、原型 |

### 14.5 注意事项

- **Docker/Container 项目**：库 Docker 分类已覆盖 `SYNO.Docker.Project/Container/Image/Network/Registry` 全套（见 14.3）。但第四章 4.1 的清理顺序陷阱（`RUNNING` 状态 delete 假成功、须轮询至 `STOPPED` 再 delete）是 DSM 底层语义，库封装不改变，用库同样要遵守 stop→轮询→delete。
- **版本漂移**：库版本与 DSM 小版本可能不匹配，遇异常先 `debug=True` 看实际请求与 `error.code`（对照通用规则表）。
- **配套配方**：作者另维护 [synology-api-recipes](https://github.com/N4S4/synology-api-recipes)，含磁盘健康、Docker、媒体等可运行示例。

---

## 十五、参考脚本

可直接修改 NAS 地址和账号后 `go run` 验证。

### 15.1 Web API 全流程验证

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

### 15.2 ACL 角色分离验证（SSH）

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

## 十六、常见问题

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

**Q: Docker 项目卡在 CREATED、build / delete 报 2104 怎么办？**
A: 旧项目未正确清理。根因是 `RUNNING` 状态下 delete 返回假成功。处理：按 4.1 顺序重新走「Project stop(id) → 轮询等 STOPPED → delete(id) → 验证消失」再 create / build。若项目已卡在 `CREATED`，delete / build 均返 2104 无法用 API 清理，需在 DSM Container Manager UI 手动删除该项目。
