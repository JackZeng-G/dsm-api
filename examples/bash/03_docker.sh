#!/usr/bin/env bash
# 示例: Docker 管理
# 用法: source ../scripts/bash/dsm_api.sh && ./03_docker.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../../scripts/bash/dsm_api.sh"

echo "=== DSM API Docker 示例 ==="

# 登录
echo "--- 登录 ---"
dsm_login > /dev/null

# 列出 Docker 项目
echo "--- Docker 项目列表 ---"
dsm_docker_project_list

# 登出
echo "--- 登出 ---"
dsm_logout > /dev/null

echo "=== Docker 示例完成 ==="
