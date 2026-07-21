#!/usr/bin/env python3
"""示例: Docker 管理"""
import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "scripts", "python"))
from dsm_api import DsmApi


def main():
    api = DsmApi()
    print("=== DSM API Docker 示例 ===")

    api.login()
    print("\n--- Docker 项目列表 ---")
    print(api.docker_project_list())

    api.logout()
    print("\n=== Docker 示例完成 ===")


if __name__ == "__main__":
    main()
