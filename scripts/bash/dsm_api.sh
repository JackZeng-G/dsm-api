#!/usr/bin/env bash
#=============================================================================
# DSM API - Bash 封装库
# 用法: source dsm_api.sh
# 环境变量: NAS_IP, NAS_USER, NAS_PASS
#=============================================================================
set -euo pipefail

DSM_API_VERSION="1.0.0"

# ---- 配置 ----
DSM_HOST="${NAS_IP:-192.168.1.10}"
DSM_USER="${NAS_USER:-admin}"
DSM_PASS="${NAS_PASS:-password}"
DSM_BASE="https://${DSM_HOST}:5001"
DSM_SID=""
DSM_TOKEN=""

# ---- 工具函数 ----
_dsm_success() {
    grep -q '"success":true' <<< "$1"
}

_dsm_curl() {
    local url="$1"; shift
    curl -sk "$url" "$@"
}

# ---- 认证 ----
dsm_connectivity_test() {
    local resp
    resp=$(_dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.API.Info&version=1&method=query&query=SYNO.API.Auth")
    echo "$resp"
}

dsm_login() {
    local session="${1:-FileStation}"
    local resp sid token

    resp=$(_dsm_curl "${DSM_BASE}/webapi/auth.cgi?api=SYNO.API.Auth&version=6&method=login&account=${DSM_USER}&passwd=${DSM_PASS}&format=sid&enable_syno_token=yes&session=${session}")

    if ! _dsm_success "$resp"; then
        echo "登录失败: $resp" >&2
        return 1
    fi

    # 用 grep/sed 提取 sid 和 synotoken（不依赖 jq）
    sid=$(echo "$resp" | sed -n 's/.*"sid":"\([^"]*\)".*/\1/p')
    token=$(echo "$resp" | sed -n 's/.*"synotoken":"\([^"]*\)".*/\1/p')

    if [ -z "$sid" ]; then
        echo "无法提取 sid" >&2
        return 1
    fi

    DSM_SID="$sid"
    DSM_TOKEN="$token"
    echo "$resp"
}

dsm_logout() {
    local resp
    resp=$(_dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.API.Auth&version=6&method=logout&_sid=${DSM_SID}")
    echo "$resp"
}

# ---- API 发现 ----
dsm_api_query() {
    local api="${1:-all}"
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.API.Info&version=1&method=query&query=${api}"
}

# ---- 文件操作 ----
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

# ---- Docker 管理 ----
dsm_docker_project_list() {
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=list&_sid=${DSM_SID}"
}

dsm_docker_project_create() {
    local name="$1"
    local path="$2"
    local share_path="$3"
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Docker.Project&version=1&method=create&name=${name}&path=${path}&share_path=${share_path}&_sid=${DSM_SID}"
}

dsm_docker_container_stop() {
    local name="$1"
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Docker.Container&version=1&method=stop&name=${name}&_sid=${DSM_SID}"
}

# ---- 用户管理 ----
dsm_user_list() {
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Core.User&version=1&method=list&_sid=${DSM_SID}"
}

# ---- 共享权限 ----
dsm_share_permission_list() {
    local name="${1:-data}"
    _dsm_curl "${DSM_BASE}/webapi/entry.cgi?api=SYNO.Core.Share.Permission&version=1&method=list&name=${name}&offset=0&limit=50&action=enum&is_unite_permission=false&with_inherit=false&user_group_type=local_user&_sid=${DSM_SID}"
}

# ---- 导出变量 ----
export DSM_SID DSM_TOKEN DSM_BASE DSM_HOST
