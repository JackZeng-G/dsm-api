#!/usr/bin/env bash
#=============================================================================
# 示例: Docker 项目管理（含手册四章 4.1 清理 → 重建顺序）
#
# ⚠️ 删除/重建是破坏性操作。本示例默认只读（list + 状态查询），
#    清理重建流程以注释展示，确认目标后取消注释执行。
#
# 用法: NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password ./03_docker.sh
#=============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../../scripts/bash/dsm_api.sh"

echo "=== DSM API Docker 项目示例 ==="

# ---- 登录 ----
if ! dsm_login > /dev/null; then
    echo "登录失败，请检查 NAS_IP/NAS_USER/NAS_PASS 环境变量" >&2
    exit 1
fi
# 退出时自动登出（即使 set -e 中止）
trap 'dsm_logout > /dev/null 2>&1 || true' EXIT

# ---- 1. 列出项目 + 状态 ----
echo
echo "--- Docker 项目列表 ---"
resp=$(dsm_docker_project_list) || {
    echo "  列表请求失败（详见上方错误）" >&2
    exit 1
}
_dsm_check "$resp" "项目列表" >/dev/null || exit 1

if command -v jq >/dev/null 2>&1; then
    count=$(printf '%s' "$resp" | jq -r '.data | length')
    if [ "${count:-0}" -eq 0 ]; then
        echo "  （无项目）"
    else
        printf '%s' "$resp" | jq -r '.data | to_entries[] | "  id=\(.key)  name=\(.value.name // "?")  status=\(.value.status // "?")"'
    fi
else
    printf '%s\n' "$resp"
    echo "  （提示：安装 jq 可格式化输出）"
fi

# ---- 2. 清理 → 重建顺序（手册 4.1，破坏性，默认不执行）----
# 核心陷阱：RUNNING 状态 delete 返回假成功 → 下次 build 报 2104。
# 正确顺序：stop(id) → 轮询 list 等 status=stopped → delete(id) → 验证消失 → create → build
#
# TARGET_ID="<要清理的项目ID>"
# dsm_docker_project_stop "$TARGET_ID" || exit 1
# while :; do                                      # 轮询等 STOPPED
#     status=$(dsm_docker_project_list | jq -r ".data[\"$TARGET_ID\"].status")
#     [ "$status" = "stopped" ] && break
#     sleep 2
# done
# dsm_docker_project_delete "$TARGET_ID" || exit 1  # STOPPED 才真删
# dsm_docker_project_create "myapp" "/volume1/docker/myapp" "/docker/myapp"
# NEW_ID=$(dsm_docker_project_list | jq -r '.data | to_entries[] | select(.value.name=="myapp").key')
# dsm_docker_project_build "$NEW_ID"
echo
echo "--- 清理重建流程（手册 4.1，破坏性，见源码注释，默认不执行）---"

echo
echo "=== Docker 示例完成 ==="
