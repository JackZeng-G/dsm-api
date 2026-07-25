// Package dsmapi 群晖 DSM REST API Go 封装库
//
// 用法:
//
//	import dsm "github.com/example/dsm-api/scripts/go"
//	api := dsm.NewClient("192.168.1.10", "admin", "password")
//	api.Login("FileStation")
//	defer api.Logout()
//
// 所有请求自动携带 _sid 与 X-SYNO-TOKEN（DSM 7 CSRF 防护）；
// 网络/HTTP 非 200/业务 success:false 统一以 error 返回。
package dsmapi

import (
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
)

// Version 封装库版本（对齐 python 黄金版）
const Version = "1.1.0"

// Client DSM API 客户端
type Client struct {
	Host     string
	User     string
	Password string
	Base     string
	Sid      string
	Token    string
	hc       *http.Client
}

// NewClient 创建客户端（跳过自签名证书校验，对应 curl -k）
func NewClient(host, user, password string) *Client {
	return &Client{
		Host:     host,
		User:     user,
		Password: password,
		Base:     fmt.Sprintf("https://%s:5001", host),
		hc: &http.Client{
			Transport: &http.Transport{
				TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
			},
		},
	}
}

// ---- 内部方法 ----

// request 统一请求：网络错 / HTTP 非 200 / success:false 均返回 error。
func (c *Client) request(method, path string, params url.Values) (map[string]interface{}, error) {
	u := c.Base + path
	var body io.Reader
	if method == "POST" {
		body = strings.NewReader(params.Encode())
	} else if len(params) > 0 {
		u += "?" + params.Encode()
	}

	req, err := http.NewRequest(method, u, body)
	if err != nil {
		return nil, fmt.Errorf("构造请求失败: %w", err)
	}
	if method == "POST" {
		req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	}
	if c.Token != "" { // DSM 7 强制 CSRF：后续请求带 X-SYNO-TOKEN
		req.Header.Set("X-SYNO-TOKEN", c.Token)
	}

	resp, err := c.hc.Do(req)
	if err != nil {
		return nil, fmt.Errorf("连接失败: %w", err)
	}
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, strings.TrimSpace(string(raw)))
	}

	var result map[string]interface{}
	if err := json.Unmarshal(raw, &result); err != nil {
		return nil, fmt.Errorf("解析响应失败: %w", err)
	}
	if ok, _ := result["success"].(bool); !ok {
		code, msg := parseError(result)
		return nil, fmt.Errorf("调用失败: code=%v %s", code, msg)
	}
	return result, nil
}

func (c *Client) get(path string, params url.Values) (map[string]interface{}, error) {
	return c.request("GET", path, params)
}

func (c *Client) post(path string, params url.Values) (map[string]interface{}, error) {
	return c.request("POST", path, params)
}

// parseError 从 success:false 响应中提取 error.code / error.message
func parseError(result map[string]interface{}) (interface{}, string) {
	if e, ok := result["error"].(map[string]interface{}); ok {
		msg, _ := e["message"].(string)
		return e["code"], msg
	}
	return "?", ""
}

// ---- 认证（一章）----

// ConnectivityTest 测试连通性（查询 SYNO.API.Info）
func (c *Client) ConnectivityTest() (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.API.Info"}, "version": {"1"},
		"method": {"query"}, "query": {"SYNO.API.Auth"},
	})
}

// Login 登录并保存 sid / synotoken
func (c *Client) Login(session string) (map[string]interface{}, error) {
	resp, err := c.get("/webapi/auth.cgi", url.Values{
		"api": {"SYNO.API.Auth"}, "version": {"6"}, "method": {"login"},
		"account": {c.User}, "passwd": {c.Password},
		"format": {"sid"}, "enable_syno_token": {"yes"},
		"session": {session},
	})
	if err != nil {
		return nil, err
	}
	if data, ok := resp["data"].(map[string]interface{}); ok {
		if sid, ok := data["sid"].(string); ok {
			c.Sid = sid
		}
		if tok, ok := data["synotoken"].(string); ok {
			c.Token = tok
		}
	}
	return resp, nil
}

// Logout 登出
func (c *Client) Logout() (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.API.Auth"}, "version": {"6"},
		"method": {"logout"}, "_sid": {c.Sid},
	})
}

// ---- API 发现（二章）----

// APIQuery 查询 API 信息
func (c *Client) APIQuery(api string) (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.API.Info"}, "version": {"1"},
		"method": {"query"}, "query": {api},
	})
}

// ---- 文件操作（三章）----

// FSInfo 获取 FileStation 信息
func (c *Client) FSInfo() (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.FileStation.Info"}, "version": {"2"},
		"method": {"get"}, "_sid": {c.Sid},
	})
}

// FSListShares 列出共享文件夹
func (c *Client) FSListShares() (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.FileStation.List"}, "version": {"2"},
		"method": {"list_share"}, "_sid": {c.Sid},
	})
}

// FSList 列出目录内容
func (c *Client) FSList(folderPath, additional string) (map[string]interface{}, error) {
	v := url.Values{
		"api": {"SYNO.FileStation.List"}, "version": {"2"},
		"method": {"list"}, "folder_path": {folderPath}, "_sid": {c.Sid},
	}
	if additional != "" {
		v.Set("additional", additional)
	}
	return c.get("/webapi/entry.cgi", v)
}

// FSCreateFolder 创建文件夹
func (c *Client) FSCreateFolder(folderPath, name string) (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.FileStation.CreateFolder"}, "version": {"2"},
		"method": {"create"}, "folder_path": {folderPath},
		"name": {name}, "force_parent": {"true"}, "_sid": {c.Sid},
	})
}

// FSRename 重命名
func (c *Client) FSRename(path, newName string) (map[string]interface{}, error) {
	return c.post("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.FileStation.Rename"}, "version": {"2"},
		"method": {"rename"}, "path": {path}, "name": {newName},
		"_sid": {c.Sid},
	})
}

// FSCopyMoveStart 发起复制/移动（异步），返回 taskid
func (c *Client) FSCopyMoveStart(path, destPath string, removeSrc, overwrite bool) (map[string]interface{}, error) {
	return c.post("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.FileStation.CopyMove"}, "version": {"3"},
		"method": {"start"}, "path": {path},
		"dest_folder_path": {destPath},
		"remove_src":       {fmt.Sprintf("%v", removeSrc)},
		"overwrite":        {fmt.Sprintf("%v", overwrite)},
		"_sid":             {c.Sid},
	})
}

// FSCopyMoveStatus 查询复制/移动进度（data.finished=true 表示完成）
func (c *Client) FSCopyMoveStatus(taskid string) (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.FileStation.CopyMove"}, "version": {"3"},
		"method": {"status"}, "taskid": {taskid}, "_sid": {c.Sid},
	})
}

// ---- Docker 项目管理（四章，含 4.1 清理重建顺序）----

// DockerProjectList 列出所有项目（data，key=项目ID）
func (c *Client) DockerProjectList() (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Docker.Project"}, "version": {"1"},
		"method": {"list"}, "_sid": {c.Sid},
	})
}

// DockerProjectGet 查询单个项目详情
func (c *Client) DockerProjectGet(id string) (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Docker.Project"}, "version": {"1"},
		"method": {"get"}, "id": {id}, "_sid": {c.Sid},
	})
}

// DockerProjectStop 项目级停止（清理首选，用 id 非 name）
func (c *Client) DockerProjectStop(id string) (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Docker.Project"}, "version": {"1"},
		"method": {"stop"}, "id": {id}, "_sid": {c.Sid},
	})
}

// DockerProjectDelete 删除项目（须 STOPPED 才真删，否则假成功 → 2104）
func (c *Client) DockerProjectDelete(id string) (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Docker.Project"}, "version": {"1"},
		"method": {"delete"}, "id": {id}, "_sid": {c.Sid},
	})
}

// DockerProjectCreate 创建项目（path=物理路径，share_path=去掉 /volume1 前缀）
func (c *Client) DockerProjectCreate(name, path, sharePath string) (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Docker.Project"}, "version": {"1"},
		"method": {"create"}, "name": {name},
		"path": {path}, "share_path": {sharePath}, "_sid": {c.Sid},
	})
}

// DockerProjectBuild 构建并启动
func (c *Client) DockerProjectBuild(id string) (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Docker.Project"}, "version": {"1"},
		"method": {"build"}, "id": {id}, "_sid": {c.Sid},
	})
}

// DockerContainerStop 容器级停止（仅兜底：项目级 stop 未生效时才用）
func (c *Client) DockerContainerStop(name string) (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Docker.Container"}, "version": {"1"},
		"method": {"stop"}, "name": {name}, "_sid": {c.Sid},
	})
}

// ---- 用户管理（六章）----

// UserList 列出系统用户
func (c *Client) UserList() (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Core.User"}, "version": {"1"},
		"method": {"list"}, "_sid": {c.Sid},
	})
}

// ---- 共享权限（五章）----

// SharePermissionList 查询共享文件夹权限（仅共享级别）
func (c *Client) SharePermissionList(name string) (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Core.Share.Permission"}, "version": {"1"},
		"method": {"list"}, "name": {name},
		"offset": {"0"}, "limit": {"50"}, "action": {"enum"},
		"is_unite_permission": {"false"}, "with_inherit": {"false"},
		"user_group_type": {"local_user"}, "_sid": {c.Sid},
	})
}

// ---- 系统与存储（七章）----

// SystemUtilization CPU/内存/磁盘利用率（data.cpu / data.memory / data.disk）
func (c *Client) SystemUtilization() (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Core.System.Utilization"}, "version": {"1"},
		"method": {"get"}, "_sid": {c.Sid},
	})
}

// SystemHealth 系统健康状态
func (c *Client) SystemHealth() (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Core.System.SystemHealth"}, "version": {"1"},
		"method": {"get"}, "_sid": {c.Sid},
	})
}

// StorageDiskList 磁盘列表（id 如 sda/sdb）
func (c *Client) StorageDiskList() (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Core.Storage.Disk"}, "version": {"1"},
		"method": {"list"}, "_sid": {c.Sid},
	})
}

// SmartHealth SMART 健康（version 固定 1）
func (c *Client) SmartHealth() (map[string]interface{}, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Storage.CGI.Smart"}, "version": {"1"},
		"method": {"get_health_info"}, "_sid": {c.Sid},
	})
}
