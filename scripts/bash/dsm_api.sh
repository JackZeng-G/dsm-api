#!/usr/bin/env bash
#=============================================================================
# DSM API - Bash 封装库
# 用法: source dsm_api.sh
# 环境变量: NAS_IP, NAS_USER, NAS_PASS
#
# 所有请求自动携带 _sid 与 X-SYNO-TOKEN（DSM 7 CSRF 防护）；
# 网络/HTTP/JSON 异常统一返回非 0 退出码，并在 stderr 打印友好提示。
# 对应手册: 一-七章
#=============================================================================
set -euo pipefail

DSM_API_VERSION="1.1.0"

# ---- 配置 ----
DSM_HOST="${NAS_IP:-192.168.1.10}"
DSM_USER="${NAS_USER:-admin}"
DSM_PASS="${NAS_PASS:-password}"
DSM_BASE="https://${DSM_HOST}:5001"
DSM_SID=""
DSM_TOKEN=""

# ---- 工具函数 ----
# 判断响应 JSON 是否 success:true（容忍空格）
_dsm_success() {
    grep -qE '"success"[[:space:]]*:[[:space:]]*true' <<< "$1"
}

# 统一请求：附加 X-SYNO-TOKEN 头（DSM 7 CSRF），并在
# curl 失败 / HTTP 非 200 / 响应非 JSON 时打印友好错误、返回非 0
# 用法: _dsm_curl <url> [curl 额外参数...]
_dsm_curl() {
    local url="$1"; shift
    local tmp http_code resp
    tmp=$(mktemp 2>/dev/null) || tmp="/tmp/dsm_${$}_${RANDOM}"

    # --connect-timeout 10 / --max-time 30：失败更快（对齐 python timeout=30）
    local curl_args=(-s -k --connect-timeout 10 --max-time 30 -o "$tmp" -w "%{http_code}")
    # 登录后所有请求带 X-SYNO-TOKEN（DSM 7 强制 CSRF 防护）
    [ -n "$DSM_TOKEN" ] && curl_args+=(-H "X-SYNO-TOKEN: ${DSM_TOKEN}")

    if ! http_code=$(curl "${curl_args[@]}" "$url" "$@" 2>/dev/null); then
        rm -f "$tmp"
        echo "请求失败: 网络错误（无法连接 ${DSM_HOST}:5001）" >&2
        return 1
    fi

    if [ "$http_code" != "200" ]; then
        rm -f "$tmp"
        echo "请求失败: HTTP ${http_code}" >&2
        return 1
    fi

    resp=$(cat "$tmp" 2>/dev/null || true)
    rm -f "$tmp"

    if [ -z "$resp" ]; then
        echo "请求失败: 空响应" >&2
        return 1
    fi
    if ! grep -q '{' <<< "$resp"; then
        echo "请求失败: 响应非 JSON（可能端口/路径错误）" >&2
        return 1
    fi

    printf '%s' "$resp"
}

# 校验 success=true，否则打印 error.code 与 action 到 stderr
# 用法: _dsm_check <resp> [action]
_dsm_check() {
    local resp="$1"
    local action="${2:-操作}"
    if ! _dsm_success "$resp"; then
        local code=""
        if [[ "$resp" =~ \"code\"[[:space:]]*:[[:space:]]*([0-9]+) ]]; then
            code="${BASH_REMATCH[1]}"
        fi
        echo "${action}失败: code=${code:-?}" >&2
        return 1
    fi
}

# ---------------- 认证（一章）----------------
dsm_connectivity_test() {
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.API.Info&version=1&method=query&query=SYNO.API.Auth"
}

dsm_login() {
    local session="${1:-FileStation}"
    local resp sid token

    resp=$(_dsm_curl "${DSM_BASE}/webapi/auth.cgi?api=SYNO.API.Auth&version=6&method=login&account=${DSM_USER}&passwd=${DSM_PASS}&format=sid&enable_syno_token=yes&session=${session}") || return 1

    if ! _dsm_success "$resp"; then
        echo "登录失败: $resp" >&2
        return 1
    fi

    # 用 sed 提取 sid 和 synotoken（不依赖 jq）
    sid=$(sed -n 's/.*"sid":"\([^"]*\)".*/\1/p' <<< "$resp")
    token=$(sed -n 's/.*"synotoken":"\([^"]*\)".*/\1/p' <<< "$resp")

    if [ -z "$sid" ]; then
        echo "无法提取 sid" >&2
        return 1
    fi

    DSM_SID="$sid"
    DSM_TOKEN="$token"
    echo "$resp"
}

dsm_logout() {
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.API.Auth&version=6&method=logout&_sid=${DSM_SID}"
}

# ---------------- API 发现（二章）----------------
dsm_api_query() {
    local api="${1:-all}"
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.API.Info&version=1&method=query&query=${api}"
}

# ---------------- 文件操作（三章）----------------
dsm_fs_info() {
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.FileStation.Info&version=2&method=get&_sid=${DSM_SID}"
}

dsm_fs_list_shares() {
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.FileStation.List&version=2&method=list_share&_sid=${DSM_SID}"
}

dsm_fs_list() {
    local folder_path="${1:-/data}"
    local additional="${2:-}"
    local url="${DSM_BASE}/webapi/entry.cgi?api=SYNO.FileStation.List&version=2&method=list&folder_path=${folder_path}&_sid=${DSM_SID}"
    [ -n "$additional" ] && url="${url}&additional=${additional}"
    _dsm_curl "$url"
}

dsm_fs_create_folder() {
    local folder_path="${1:-/data}"
    local name="${2:-newfolder}"
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.FileStation.CreateFolder&version=2&method=create&folder_path=${folder_path}&name=${name}&force_parent=true&_sid=${DSM_SID}"
}

dsm_fs_rename() {
    local path="$1"
    local new_name="$2"
    _dsm_curl -X POST "${DSM_BASE}/webapi/entry.cgi" \
        -d "api=SYNO.FileStation.Rename&version=2&method=rename" \
        -d "path=${path}" \
        -d "name=${new_name}" \
        -d "_sid=${DSM_SID}"
}

dsm_fs_copy_move_start() {
    local path="$1"           # JSON 数组字符串，如 '["/data/src"]'
    local dest_path="$2"
    local remove_src="${3:-false}"
    local overwrite="${4:-false}"
    _dsm_curl -X POST "${DSM_BASE}/webapi/entry.cgi" \
        -d "api=SYNO.FileStation.CopyMove&version=3&method=start" \
        -d "path=${path}" \
        -d "dest_folder_path=${dest_path}" \
        -d "remove_src=${remove_src}" \
        -d "overwrite=${overwrite}" \
        -d "_sid=${DSM_SID}"
}

dsm_fs_copy_move_status() {
    local taskid="$1"
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.FileStation.CopyMove&version=3&method=status&taskid=${taskid}&_sid=${DSM_SID}"
}

# ---------------- Docker 项目管理（四章，含 4.1 清理重建顺序）----------------
dsm_docker_project_list() {
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=list&_sid=${DSM_SID}"
}

dsm_docker_project_get() {
    local project_id="$1"
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=get&id=${project_id}&_sid=${DSM_SID}"
}

dsm_docker_project_stop() {
    # 项目级停止（清理首选，用 id 非 name）
    local project_id="$1"
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=stop&id=${project_id}&_sid=${DSM_SID}"
}

dsm_docker_project_delete() {
    # 删除项目（须 STOPPED 才真删，否则假成功 → 2104）
    local project_id="$1"
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=delete&id=${project_id}&_sid=${DSM_SID}"
}

dsm_docker_project_create() {
    # path=物理路径，share_path=去掉 /volume1 前缀
    local name="$1"
    local path="$2"
    local share_path="$3"
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=create&name=${name}&path=${path}&share_path=${share_path}&_sid=${DSM_SID}"
}

dsm_docker_project_build() {
    # 构建并启动
    local project_id="$1"
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=build&id=${project_id}&_sid=${DSM_SID}"
}

dsm_docker_container_stop() {
    # 容器级停止（仅兜底：项目级 stop 未生效时才用）
    local name="$1"
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Docker.Container&version=1&method=stop&name=${name}&_sid=${DSM_SID}"
}

# ---------------- 用户管理（六章）----------------
dsm_user_list() {
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Core.User&version=1&method=list&_sid=${DSM_SID}"
}

# ---------------- 共享权限（五章）----------------
dsm_share_permission_list() {
    local name="${1:-data}"
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Core.Share.Permission&version=1&method=list&name=${name}&offset=0&limit=50&action=enum&is_unite_permission=false&with_inherit=false&user_group_type=local_user&_sid=${DSM_SID}"
}

# ---------------- 系统与存储（七章）----------------
dsm_system_utilization() {
    # CPU/内存/磁盘利用率（data.cpu / data.memory / data.disk）
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Core.System.Utilization&version=1&method=get&_sid=${DSM_SID}"
}

dsm_system_health() {
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Core.System.SystemHealth&version=1&method=get&_sid=${DSM_SID}"
}

dsm_storage_disk_list() {
    # 磁盘列表（id 如 sda/sdb）
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Core.Storage.Disk&version=1&method=list&_sid=${DSM_SID}"
}

dsm_smart_health() {
    # SMART 健康（version 固定 1）
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Storage.CGI.Smart&version=1&method=get_health_info&_sid=${DSM_SID}"
}

# ---- 导出变量 ----
export DSM_SID DSM_TOKEN DSM_BASE DSM_HOST
