#!/usr/bin/env python3
"""
示例: Docker 项目管理（含手册四章 4.1 清理 → 重建顺序）

⚠️ 删除/重建是破坏性操作。本示例默认只读（list + 状态查询），
   清理重建流程以注释展示，确认目标后取消注释执行。

用法: NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password python3 03_docker.py
"""
import sys
import os
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "scripts", "python"))
from dsm_api import DsmApi, DsmApiError


def main():
    api = DsmApi()
    print("=== DSM API Docker 项目示例 ===")

    try:
        api.login()
    except DsmApiError as e:
        print(f"登录失败: {e}")
        return

    # ---- 1. 列出项目 + 状态 ----
    print("\n--- Docker 项目列表 ---")
    try:
        resp = api.docker_project_list()
        projects = resp.get("data", {})
        if not projects:
            print("  （无项目）")
        for pid, info in projects.items():
            print(f"  id={pid}  name={info.get('name')}  status={info.get('status')}")
    except DsmApiError as e:
        print(f"  列表失败: {e}")

    # ---- 2. 清理 → 重建顺序（手册 4.1，破坏性，默认不执行）----
    # 核心陷阱：RUNNING 状态 delete 返回假成功 → 下次 build 报 2104。
    # 正确顺序：stop(id) → 轮询 list 等 status=stopped → delete(id) → 验证消失 → create → build
    #
    # TARGET_ID = "<要清理的项目ID>"
    # api.docker_project_stop(TARGET_ID)
    # while True:                       # 轮询等 STOPPED
    #     info = api.docker_project_list()["data"].get(TARGET_ID, {})
    #     if info.get("status") != "running":
    #         break
    #     time.sleep(2)
    # api.docker_project_delete(TARGET_ID)   # STOPPED 才真删
    # api.docker_project_create("myapp", "/volume1/docker/myapp", "/docker/myapp")
    # api.docker_project_build("<新项目ID>")
    print("\n--- 清理重建流程（手册 4.1，破坏性，见源码注释，默认不执行）---")

    try:
        api.logout()
    except DsmApiError as e:
        print(f"登出失败: {e}")

    print("\n=== Docker 示例完成 ===")


if __name__ == "__main__":
    main()
