#!/usr/bin/env bash
# 示例: 认证与连通性测试
# 用法: source ../scripts/bash/dsm_api.sh && ./01_auth.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../../scripts/bash/dsm_api.sh"

echo "=== DSM API 认证示例 ==="
echo "NAS: ${DSM_HOST}"

# 1. 连通性测试
echo ""
echo "--- 1. 连通性测试 ---"
dsm_connectivity_test

# 2. 登录
echo ""
echo "--- 2. 登录 ---"
dsm_login

echo "SID: ${DSM_SID:0:20}..."

# 3. 登出
echo ""
echo "--- 3. 登出 ---"
dsm_logout

echo ""
echo "=== 认证流程完成 ==="
