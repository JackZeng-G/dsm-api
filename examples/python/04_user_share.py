#!/usr/bin/env python3
"""
示例: 用户与共享权限查询（只读，安全）

用法: NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password python3 04_user_share.py
"""
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "scripts", "python"))
from dsm_api import DsmApi, DsmApiError

# 要查询的共享文件夹名（按你 NAS 实际改）
SHARE_NAME = os.environ.get("SHARE_NAME", "data")


def main():
    api = DsmApi()
    print("=== DSM API 用户与共享权限示例 ===")

    try:
        api.login()
    except DsmApiError as e:
        print(f"登录失败: {e}")
        return

    # ---- 1. 用户列表（手册六章）----
    print("\n--- 系统用户列表 ---")
    try:
        resp = api.user_list()
        users = resp.get("data", {}).get("users", [])
        if not users:
            print("  （无用户或字段结构不同）")
        for u in users:
            print(f"  {u.get('name')}  ({u.get('description', '')})")
    except DsmApiError as e:
        print(f"  查询失败: {e}")

    # ---- 2. 共享文件夹权限（手册五章，仅共享级别）----
    print(f"\n--- 共享文件夹 [{SHARE_NAME}] 权限 ---")
    try:
        resp = api.share_permission_list(SHARE_NAME)
        perms = resp.get("data", {}).get("acl", {}).get("acl", [])
        if not perms:
            # 兼容另一种返回结构
            perms = resp.get("data", {}).get("permissions", [])
        if not perms:
            print(f"  （无权限条目，或共享不存在。返回: {resp.get('data')})")
        for p in perms:
            name = p.get("name", "?")
            perm = ("读写" if p.get("is_writable") else
                    "只读" if p.get("is_readonly") else
                    "拒绝" if p.get("is_deny") else "无")
            print(f"  {name}: {perm}")
    except DsmApiError as e:
        print(f"  查询失败: {e}")
        print("  提示: 子目录权限不支持，仅共享文件夹级别（手册五章 5.1）。")

    try:
        api.logout()
    except DsmApiError as e:
        print(f"登出失败: {e}")

    print("\n=== 用户与权限示例完成 ===")


if __name__ == "__main__":
    main()
