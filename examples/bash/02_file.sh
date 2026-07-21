#!/usr/bin/env bash
# 示例: 文件操作
# 用法: source ../scripts/bash/dsm_api.sh && ./02_file.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../../scripts/bash/dsm_api.sh"

echo "=== DSM API 文件操作示例 ==="

# 登录
echo "--- 登录 ---"
dsm_login > /dev/null

# 列出共享文件夹
echo ""
echo "--- 共享文件夹列表 ---"
dsm_fs_list_shares

# 创建测试文件夹
echo ""
echo "--- 创建测试文件夹 ---"
dsm_fs_create_folder "/data" "dsm_test_$(date +%s)"

# 列出 /data 内容
echo ""
echo "--- /data 目录内容 ---"
dsm_fs_list "/data"

# 登出
echo ""
echo "--- 登出 ---"
dsm_logout > /dev/null

echo ""
echo "=== 文件操作完成 ==="
