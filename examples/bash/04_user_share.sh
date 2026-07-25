#!/usr/bin/env bash
#=============================================================================
# 示例: 用户与共享权限查询（只读，安全）
#
# 用法: NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password \
#      SHARE_NAME=data ./04_user_share.sh
#=============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../../scripts/bash/dsm_api.sh"

SHARE_NAME="${SHARE_NAME:-data}"

echo "=== DSM API 用户与共享权限示例 ==="

# ---- 登录 ----
if ! dsm_login > /dev/null; then
    echo "登录失败，请检查 NAS_IP/NAS_USER/NAS_PASS 环境变量" >&2
    exit 1
fi
trap 'dsm_logout > /dev/null 2>&1 || true' EXIT

# ---- 1. 用户列表（手册六章）----
echo
echo "--- 系统用户列表 ---"
resp=$(dsm_user_list) || {
    echo "  查询请求失败（详见上方错误）" >&2
    exit 1
}
if ! _dsm_check "$resp" "用户列表" >/dev/null; then
    exit 1
fi

if command -v jq >/dev/null 2>&1; then
    count=$(printf '%s' "$resp" | jq -r '.data.users | length')
    if [ "${count:-0}" -eq 0 ]; then
        echo "  （无用户或字段结构不同）"
    else
        printf '%s' "$resp" | jq -r '.data.users[] | "  \(.name // "?")  (\(.description // ""))"'
    fi
else
    printf '%s\n' "$resp"
    echo "  （提示：安装 jq 可格式化输出）"
fi

# ---- 2. 共享文件夹权限（手册五章，仅共享级别）----
echo
echo "--- 共享文件夹 [${SHARE_NAME}] 权限 ---"
resp=$(dsm_share_permission_list "$SHARE_NAME") || {
    echo "  查询请求失败（详见上方错误）" >&2
    exit 1
}
if ! _dsm_check "$resp" "共享权限" >/dev/null; then
    echo "  提示: 子目录权限不支持，仅共享文件夹级别（手册五章 5.1）。" >&2
    exit 1
fi

if command -v jq >/dev/null 2>&1; then
    # 兼容两种返回结构：data.acl.acl 或 data.permissions
    count=$(printf '%s' "$resp" | jq -r '(.data.acl.acl // .data.permissions // []) | length')
    if [ "${count:-0}" -eq 0 ]; then
        echo "  （无权限条目，或共享不存在）"
    else
        printf '%s' "$resp" | jq -r '
            (.data.acl.acl // .data.permissions)[] |
            "  \(.name // "?"): " + (
                if .is_writable then "读写"
                elif .is_readonly then "只读"
                elif .is_deny then "拒绝"
                else "无" end
            )'
    fi
else
    printf '%s\n' "$resp"
    echo "  （提示：安装 jq 可格式化输出）"
fi

echo
echo "=== 用户与权限示例完成 ==="
