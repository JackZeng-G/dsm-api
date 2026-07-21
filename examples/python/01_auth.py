#!/usr/bin/env python3
"""
示例: 认证与连通性测试
用法: NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password python3 01_auth.py
"""
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "scripts", "python"))
from dsm_api import DsmApi


def main():
    api = DsmApi()
    print(f"=== DSM API 认证示例 ===")
    print(f"NAS: {api.host}")

    # 1. 连通性测试
    print("\n--- 1. 连通性测试 ---")
    resp = api.connectivity_test()
    print(f"成功: {api._ok(resp)}")

    # 2. 登录
    print("\n--- 2. 登录 ---")
    resp = api.login()
    print(f"成功: {api._ok(resp)}, SID: {api.sid[:20] if api.sid else 'N/A'}...")

    # 3. 登出
    print("\n--- 3. 登出 ---")
    resp = api.logout()
    print(f"成功: {api._ok(resp)}")

    print("\n=== 认证流程完成 ===")


if __name__ == "__main__":
    main()
