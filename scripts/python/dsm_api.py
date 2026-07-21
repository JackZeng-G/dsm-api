"""
DSM API - Python 封装库
用法: from dsm_api import DsmApi

环境变量: NAS_IP, NAS_USER, NAS_PASS
"""

import os
import urllib.parse
import urllib.request
import ssl
import json
from typing import Optional, Any

DSM_API_VERSION = "1.0.0"


class DsmApi:
    """群晖 DSM REST API 客户端"""

    def __init__(
        self,
        host: Optional[str] = None,
        user: Optional[str] = None,
        password: Optional[str] = None,
    ):
        self.host = host or os.environ.get("NAS_IP", "192.168.1.10")
        self.user = user or os.environ.get("NAS_USER", "admin")
        self.password = password or os.environ.get("NAS_PASS", "password")
        self.base = f"https://{self.host}:5001"
        self.sid: str = ""
        self.token: str = ""

        # 跳过自签名证书校验
        self._ctx = ssl.create_default_context()
        self._ctx.check_hostname = False
        self._ctx.verify_mode = ssl.CERT_NONE

    # ---- 内部方法 ----
    def _get(self, path: str, params: dict = None) -> dict:
        url = f"{self.base}{path}"
        if params:
            query = urllib.parse.urlencode(params)
            url = f"{url}?{query}"
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, context=self._ctx) as resp:
            return json.loads(resp.read())

    def _post(self, path: str, data: dict = None) -> dict:
        url = f"{self.base}{path}"
        body = urllib.parse.urlencode(data or {}).encode()
        req = urllib.request.Request(url, data=body, method="POST")
        with urllib.request.urlopen(req, context=self._ctx) as resp:
            return json.loads(resp.read())

    def _ok(self, resp: dict) -> bool:
        return resp.get("success", False)

    # ---- 认证 ----
    def connectivity_test(self) -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.API.Info", "version": "1",
            "method": "query", "query": "SYNO.API.Auth",
        })

    def login(self, session: str = "FileStation") -> dict:
        resp = self._get("/webapi/auth.cgi", {
            "api": "SYNO.API.Auth", "version": "6", "method": "login",
            "account": self.user, "passwd": self.password,
            "format": "sid", "enable_syno_token": "yes",
            "session": session,
        })
        if self._ok(resp):
            data = resp.get("data", {})
            self.sid = data.get("sid", "")
            self.token = data.get("synotoken", "")
        return resp

    def logout(self) -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.API.Auth", "version": "6",
            "method": "logout", "_sid": self.sid,
        })

    # ---- API 发现 ----
    def api_query(self, api: str = "all") -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.API.Info", "version": "1",
            "method": "query", "query": api,
        })

    # ---- 文件操作 ----
    def fs_info(self) -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.FileStation.Info", "version": "2",
            "method": "get", "_sid": self.sid,
        })

    def fs_list_shares(self) -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.FileStation.List", "version": "2",
            "method": "list_share", "_sid": self.sid,
        })

    def fs_list(self, folder_path: str = "/data", additional: str = "") -> dict:
        params = {
            "api": "SYNO.FileStation.List", "version": "2",
            "method": "list", "folder_path": folder_path, "_sid": self.sid,
        }
        if additional:
            params["additional"] = additional
        return self._get("/webapi/entry.cgi", params)

    def fs_create_folder(self, folder_path: str = "/data", name: str = "newfolder") -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.FileStation.CreateFolder", "version": "2",
            "method": "create", "folder_path": folder_path,
            "name": name, "force_parent": "true", "_sid": self.sid,
        })

    def fs_rename(self, path: str, new_name: str) -> dict:
        return self._post("/webapi/entry.cgi", {
            "api": "SYNO.FileStation.Rename", "version": "2",
            "method": "rename", "path": path, "name": new_name,
            "_sid": self.sid,
        })

    def fs_copy_move_start(self, path: str, dest_path: str,
                           remove_src: bool = False, overwrite: bool = False) -> dict:
        """发起复制/移动（异步）"""
        return self._post("/webapi/entry.cgi", {
            "api": "SYNO.FileStation.CopyMove", "version": "3",
            "method": "start", "path": path,
            "dest_folder_path": dest_path,
            "remove_src": str(remove_src).lower(),
            "overwrite": str(overwrite).lower(),
            "_sid": self.sid,
        })

    def fs_copy_move_status(self, taskid: str) -> dict:
        """查询复制/移动进度"""
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.FileStation.CopyMove", "version": "3",
            "method": "status", "taskid": taskid, "_sid": self.sid,
        })

    # ---- Docker 管理 ----
    def docker_project_list(self) -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Docker.Project", "version": "1",
            "method": "list", "_sid": self.sid,
        })

    def docker_project_create(self, name: str, path: str, share_path: str) -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Docker.Project", "version": "1",
            "method": "create", "name": name,
            "path": path, "share_path": share_path, "_sid": self.sid,
        })

    def docker_container_stop(self, name: str) -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Docker.Container", "version": "1",
            "method": "stop", "name": name, "_sid": self.sid,
        })

    # ---- 用户管理 ----
    def user_list(self) -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Core.User", "version": "1",
            "method": "list", "_sid": self.sid,
        })

    # ---- 共享权限 ----
    def share_permission_list(self, name: str = "data") -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Core.Share.Permission", "version": "1",
            "method": "list", "name": name, "offset": "0",
            "limit": "50", "action": "enum",
            "is_unite_permission": "false", "with_inherit": "false",
            "user_group_type": "local_user", "_sid": self.sid,
        })


# ---- 便捷函数 ----
_default_api: Optional[DsmApi] = None


def get_api() -> DsmApi:
    global _default_api
    if _default_api is None:
        _default_api = DsmApi()
    return _default_api
