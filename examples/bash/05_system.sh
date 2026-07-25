#!/usr/bin/env bash
#=============================================================================
# 示例: 系统信息与存储健康（CPU/内存/磁盘/SMART，只读）
#
# 用法: NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password ./05_system.sh
#=============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../../scripts/bash/dsm_api.sh"

echo "=== DSM API 系统与存储示例 ==="

# ---- 登录 ----
if ! dsm_login > /dev/null; then
    echo "登录失败，请检查 NAS_IP/NAS_USER/NAS_PASS 环境变量" >&2
    exit 1
fi
trap 'dsm_logout > /dev/null 2>&1 || true' EXIT

# ---- 1. CPU/内存/磁盘利用率（手册七章 7.1）----
echo
echo "--- 系统利用率 ---"
resp=$(dsm_system_utilization) || {
    echo "  查询请求失败（详见上方错误）" >&2
    exit 1
}
if _dsm_check "$resp" "系统利用率" >/dev/null; then
    if command -v jq >/dev/null 2>&1; then
        printf '%s' "$resp" | jq -r '"  CPU 用户态: \(.data.cpu.user_load // "?")%"'
        printf '%s' "$resp" | jq -r '"  内存: \(.data.memory.memory_usage // "?") / \(.data.memory.memory_size // "?") (MB)"'
    else
        printf '%s\n' "$resp"
        echo "  （提示：安装 jq 可格式化输出）"
    fi
fi

# ---- 2. 磁盘列表（手册七章 7.2）----
echo
echo "--- 磁盘列表 ---"
resp=$(dsm_storage_disk_list) || {
    echo "  查询请求失败（详见上方错误）" >&2
    exit 1
}
if _dsm_check "$resp" "磁盘列表" >/dev/null; then
    if command -v jq >/dev/null 2>&1; then
        count=$(printf '%s' "$resp" | jq -r '.data.disks | length')
        if [ "${count:-0}" -eq 0 ]; then
            echo "  （无磁盘或字段结构不同）"
        else
            printf '%s' "$resp" | jq -r '.data.disks[] | "  \(.id // "?")  型号=\(.model // "?")  温度=\(.temp // "?")°C  状态=\(.status // "?")"'
        fi
    else
        printf '%s\n' "$resp"
        echo "  （提示：安装 jq 可格式化输出）"
    fi
fi

# ---- 3. SMART 健康（手册七章 7.2，version 固定 1）----
echo
echo "--- SMART 健康 ---"
resp=$(dsm_smart_health) || {
    echo "  查询请求失败（详见上方错误）" >&2
    exit 1
}
if _dsm_check "$resp" "SMART 健康" >/dev/null; then
    if command -v jq >/dev/null 2>&1; then
        count=$(printf '%s' "$resp" | jq -r '.data.disks | length')
        if [ "${count:-0}" -eq 0 ]; then
            echo "  （无磁盘或字段结构不同）"
        else
            printf '%s' "$resp" | jq -r '.data.disks[] | "  \(.id // "?")  健康状态=\(.health // "?")"'
        fi
    else
        printf '%s\n' "$resp"
        echo "  （提示：安装 jq 可格式化输出）"
    fi
fi

echo
echo "=== 系统与存储示例完成 ==="
