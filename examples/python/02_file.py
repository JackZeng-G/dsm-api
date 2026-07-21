#!/usr/bin/env python3
"""示例: 文件操作"""
import sys, os, time
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "scripts", "python"))
from dsm_api import DsmApi


def main():
    api = DsmApi()
    print("=== DSM API 文件操作示例 ===")

    api.login()

    print("\n--- 共享文件夹列表 ---")
    resp = api.fs_list_shares()
    shares = resp.get("data", {}).get("shares", [])
    for s in shares:
        print(f"  {s.get('name')}: {s.get('path')}")

    print("\n--- 创建测试文件夹 ---")
    resp = api.fs_create_folder("/data", f"dsm_test_{int(time.time())}")
    print(f"result: {resp}")

    print("\n--- /data 目录内容 ---")
    resp = api.fs_list("/data")
    files = resp.get("data", {}).get("files", [])
    for f in files:
        print(f"  {'[DIR]' if f.get('isdir') else '[FILE]'} {f.get('name')}")

    api.logout()
    print("\n=== 文件操作完成 ===")


if __name__ == "__main__":
    main()
