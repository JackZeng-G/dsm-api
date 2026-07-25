"""
DSM API - Python 封装库
用法: from dsm_api import DsmApi

环境变量: NAS_IP, NAS_USER, NAS_PASS

覆盖: 认证 / 文件 / Docker 项目 / 用户 / 共享权限 / 系统与存储
对应手册: 一-七章
"""

import os
import json
import ssl
import urllib.error
import urllib.parse
import urllib.request
from typing import Optional

DSM_API_VERSION = "1.1.0"


class DsmApiError(Exception):
    """DSM API 调用异常（网络错 / success:false）"""


class DsmApi:
    """群晖 DSM REST API 客户端

    所有请求自动携带 _sid 与 X-SYNO-TOKEN（DSM 7 CSRF 防护）；
    网络/HTTP 异常统一抛 DsmApiError。
    """

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

        # 跳过自签名证书校验（对应 curl -k）
        self._ctx = ssl.create_default_context()
        self._ctx.check_hostname = False
        self._ctx.verify_mode = ssl.CERT_NONE

    # ---------------- 内部方法 ----------------
    def _request(self, method: str, path: str, params: Optional[dict] = None) -> dict:
        url = f"{self.base}{path}"
        headers: dict = {}
        data = None
        if method == "GET":
            if params:
                url = f"{url}?{urllib.parse.urlencode(params)}"
        else:  # POST
            data = urllib.parse.urlencode(params or {}).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        if self.token:  # DSM 7 强制 CSRF：后续请求带 X-SYNO-TOKEN
            headers["X-SYNO-TOKEN"] = self.token

        req = urllib.request.Request(url, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, context=self._ctx, timeout=30) as resp:
                return json.loads(resp.read())
        except urllib.error.HTTPError as e:
            raise DsmApiError(f"HTTP {e.code}: {e.reason}") from e
        except urllib.error.URLError as e:
            raise DsmApiError(f"连接失败: {e.reason}") from e
        except Exception as e:
            raise DsmApiError(f"请求失败: {e}") from e

    def _get(self, path: str, params: dict = None) -> dict:
        return self._request("GET", path, params)

    def _post(self, path: str, params: dict = None) -> dict:
        return self._request("POST", path, params)

    @staticmethod
    def _ok(resp: dict) -> bool:
        return resp.get("success", False)

    def _check(self, resp: dict, action: str = "") -> dict:
        """调用后校验 success，失败抛 DsmApiError（含 error.code）"""
        if not self._ok(resp):
            err = resp.get("error", {})
            code = err.get("code", "?")
            msg = err.get("message", "")
            raise DsmApiError(f"{action}失败: code={code} {msg}".strip())
        return resp

    # ---------------- 认证（一章）----------------
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

    # ---------------- API 发现（二章）----------------
    def api_query(self, api: str = "all") -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.API.Info", "version": "1",
            "method": "query", "query": api,
        })

    # ---------------- 文件操作（三章）----------------
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
        """发起复制/移动（异步），返回 taskid"""
        return self._post("/webapi/entry.cgi", {
            "api": "SYNO.FileStation.CopyMove", "version": "3",
            "method": "start", "path": path,
            "dest_folder_path": dest_path,
            "remove_src": str(remove_src).lower(),
            "overwrite": str(overwrite).lower(),
            "_sid": self.sid,
        })

    def fs_copy_move_status(self, taskid: str) -> dict:
        """查询复制/移动进度（data.finished=true 表示完成）"""
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.FileStation.CopyMove", "version": "3",
            "method": "status", "taskid": taskid, "_sid": self.sid,
        })

    # ---------------- Docker 项目管理（四章，含 4.1 清理重建顺序）----------------
    def docker_project_list(self) -> dict:
        """列出所有项目（返回 map，key=项目ID）"""
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Docker.Project", "version": "1",
            "method": "list", "_sid": self.sid,
        })

    def docker_project_get(self, project_id: str) -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Docker.Project", "version": "1",
            "method": "get", "id": project_id, "_sid": self.sid,
        })

    def docker_project_stop(self, project_id: str) -> dict:
        """项目级停止（清理首选，用 id 非 name）"""
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Docker.Project", "version": "1",
            "method": "stop", "id": project_id, "_sid": self.sid,
        })

    def docker_project_delete(self, project_id: str) -> dict:
        """删除项目（须 STOPPED 才真删，否则假成功 → 2104）"""
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Docker.Project", "version": "1",
            "method": "delete", "id": project_id, "_sid": self.sid,
        })

    def docker_project_create(self, name: str, path: str, share_path: str) -> dict:
        """创建项目（path=物理路径，share_path=去掉 /volume1 前缀）"""
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Docker.Project", "version": "1",
            "method": "create", "name": name,
            "path": path, "share_path": share_path, "_sid": self.sid,
        })

    def docker_project_build(self, project_id: str) -> dict:
        """构建并启动"""
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Docker.Project", "version": "1",
            "method": "build", "id": project_id, "_sid": self.sid,
        })

    def docker_container_stop(self, name: str) -> dict:
        """容器级停止（仅兜底：项目级 stop 未生效时才用）"""
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Docker.Container", "version": "1",
            "method": "stop", "name": name, "_sid": self.sid,
        })

    # ---------------- 用户管理（六章）----------------
    def user_list(self) -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Core.User", "version": "1",
            "method": "list", "_sid": self.sid,
        })

    # ---------------- 共享权限（五章）----------------
    def share_permission_list(self, name: str = "data") -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Core.Share.Permission", "version": "1",
            "method": "list", "name": name, "offset": "0",
            "limit": "50", "action": "enum",
            "is_unite_permission": "false", "with_inherit": "false",
            "user_group_type": "local_user", "_sid": self.sid,
        })

    # ---------------- 系统与存储（七章）----------------
    def system_utilization(self) -> dict:
        """CPU/内存/磁盘利用率（data.cpu / data.memory / data.disk）"""
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Core.System.Utilization", "version": "1",
            "method": "get", "_sid": self.sid,
        })

    def system_health(self) -> dict:
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Core.System.SystemHealth", "version": "1",
            "method": "get", "_sid": self.sid,
        })

    def storage_disk_list(self) -> dict:
        """磁盘列表（id 如 sda/sdb）"""
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Core.Storage.Disk", "version": "1",
            "method": "list", "_sid": self.sid,
        })

    def smart_health(self) -> dict:
        """SMART 健康（version 固定 1）"""
        return self._get("/webapi/entry.cgi", {
            "api": "SYNO.Storage.CGI.Smart", "version": "1",
            "method": "get_health_info", "_sid": self.sid,
        })


# ---------------- 便捷函数 ----------------
_default_api: Optional[DsmApi] = None


def get_api() -> DsmApi:
    global _default_api
    if _default_api is None:
        _default_api = DsmApi()
    return _default_api
