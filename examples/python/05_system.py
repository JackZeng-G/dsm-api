#!/usr/bin/env python3
"""
示例: 系统信息与存储健康（CPU/内存/磁盘/SMART，只读）

用法: NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password python3 05_system.py
"""
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "scripts", "python"))
from dsm_api import DsmApi, DsmApiError


def main():
    api = DsmApi()
    print("=== DSM API 系统与存储示例 ===")

    try:
        api.login()
    except DsmApiError as e:
        print(f"登录失败: {e}")
        return

    # ---- 1. CPU/内存/磁盘利用率（手册七章 7.1）----
    print("\n--- 系统利用率 ---")
    try:
        resp = api.system_utilization()
        data = resp.get("data", {})
        cpu = data.get("cpu", {}).get("user_load", "?")
        mem = data.get("memory", {})
        mem_usage = mem.get("memory_usage", "?")
        mem_total = mem.get("memory_size", "?")
        print(f"  CPU 用户态: {cpu}%")
        print(f"  内存: {mem_usage} / {mem_total} (MB)")
    except DsmApiError as e:
        print(f"  查询失败: {e}")

    # ---- 2. 磁盘列表（手册七章 7.2）----
    print("\n--- 磁盘列表 ---")
    try:
        resp = api.storage_disk_list()
        disks = resp.get("data", {}).get("disks", [])
        if not disks:
            print(f"  （返回结构: {resp.get('data')})")
        for d in disks:
            print(f"  {d.get('id', '?')}  型号={d.get('model', '?')}  "
                  f"温度={d.get('temp', '?')}°C  状态={d.get('status', '?')}")
    except DsmApiError as e:
        print(f"  查询失败: {e}")

    # ---- 3. SMART 健康（手册七章 7.2，version 固定 1）----
    print("\n--- SMART 健康 ---")
    try:
        resp = api.smart_health()
        data = resp.get("data", {})
        disks = data.get("disks", [])
        if not disks:
            print(f"  （返回结构: {data})")
        for d in disks:
            print(f"  {d.get('id', '?')}  健康状态={d.get('health', '?')}")
    except DsmApiError as e:
        print(f"  查询失败: {e}")

    try:
        api.logout()
    except DsmApiError as e:
        print(f"登出失败: {e}")

    print("\n=== 系统与存储示例完成 ===")


if __name__ == "__main__":
    main()
