// Package dsmapi 群晖 DSM REST API Go 封装库
//
// 用法:
//
//	import dsm "github.com/example/dsm-api/scripts/go"
//	api := dsm.NewClient("192.168.1.10", "admin", "password")
//	api.Login("FileStation")
//	defer api.Logout()
package dsmapi

import (
	"crypto/tls"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
)

const Version = "1.0.0"

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

// NewClient 创建客户端（跳过证书校验）
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

func (c *Client) get(path string, params url.Values) (string, error) {
	u := c.Base + path
	if len(params) > 0 {
		u += "?" + params.Encode()
	}
	resp, err := c.hc.Get(u)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	return string(body), nil
}

func (c *Client) post(path string, data url.Values) (string, error) {
	resp, err := c.hc.PostForm(c.Base+path, data)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	return string(body), nil
}

func ok(body string) bool {
	return strings.Contains(body, `"success":true`)
}

// ---- 认证 ----

// ConnectivityTest 测试连通性
func (c *Client) ConnectivityTest() (string, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.API.Info"}, "version": {"1"},
		"method": {"query"}, "query": {"SYNO.API.Auth"},
	})
}

// Login 登录并获取 sid
func (c *Client) Login(session string) (string, error) {
	body, err := c.get("/webapi/auth.cgi", url.Values{
		"api": {"SYNO.API.Auth"}, "version": {"6"}, "method": {"login"},
		"account": {c.User}, "passwd": {c.Password},
		"format": {"sid"}, "enable_syno_token": {"yes"},
		"session": {session},
	})
	if err != nil {
		return "", err
	}
	// 提取 sid
	if idx := strings.Index(body, `"sid":"`); idx != -1 {
		start := idx + len(`"sid":"`)
		if end := strings.Index(body[start:], `"`); end != -1 {
			c.Sid = body[start : start+end]
		}
	}
	// 提取 synotoken
	if idx := strings.Index(body, `"synotoken":"`); idx != -1 {
		start := idx + len(`"synotoken":"`)
		if end := strings.Index(body[start:], `"`); end != -1 {
			c.Token = body[start : start+end]
		}
	}
	return body, nil
}

// Logout 登出
func (c *Client) Logout() (string, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.API.Auth"}, "version": {"6"},
		"method": {"logout"}, "_sid": {c.Sid},
	})
}

// ---- API 发现 ----

// APIQuery 查询 API 信息
func (c *Client) APIQuery(api string) (string, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.API.Info"}, "version": {"1"},
		"method": {"query"}, "query": {api},
	})
}

// ---- 文件操作 ----

// FSInfo 获取当前用户信息
func (c *Client) FSInfo() (string, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.FileStation.Info"}, "version": {"2"},
		"method": {"get"}, "_sid": {c.Sid},
	})
}

// FSListShares 列出共享文件夹
func (c *Client) FSListShares() (string, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.FileStation.List"}, "version": {"2"},
		"method": {"list_share"}, "_sid": {c.Sid},
	})
}

// FSList 列出目录内容
func (c *Client) FSList(folderPath string, additional string) (string, error) {
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
func (c *Client) FSCreateFolder(folderPath, name string) (string, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.FileStation.CreateFolder"}, "version": {"2"},
		"method": {"create"}, "folder_path": {folderPath},
		"name": {name}, "force_parent": {"true"}, "_sid": {c.Sid},
	})
}

// FSRename 重命名
func (c *Client) FSRename(path, newName string) (string, error) {
	return c.post("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.FileStation.Rename"}, "version": {"2"},
		"method": {"rename"}, "path": {path}, "name": {newName},
		"_sid": {c.Sid},
	})
}

// FSCopyMoveStart 发起复制/移动
func (c *Client) FSCopyMoveStart(path, destPath string, removeSrc, overwrite bool) (string, error) {
	return c.post("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.FileStation.CopyMove"}, "version": {"3"},
		"method": {"start"}, "path": {path},
		"dest_folder_path": {destPath},
		"remove_src": {fmt.Sprintf("%v", removeSrc)},
		"overwrite":  {fmt.Sprintf("%v", overwrite)},
		"_sid":       {c.Sid},
	})
}

// FSCopyMoveStatus 查询复制/移动进度
func (c *Client) FSCopyMoveStatus(taskid string) (string, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.FileStation.CopyMove"}, "version": {"3"},
		"method": {"status"}, "taskid": {taskid}, "_sid": {c.Sid},
	})
}

// ---- Docker 管理 ----

// DockerProjectList 列出 Docker 项目
func (c *Client) DockerProjectList() (string, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Docker.Project"}, "version": {"1"},
		"method": {"list"}, "_sid": {c.Sid},
	})
}

// DockerProjectCreate 创建 Docker 项目
func (c *Client) DockerProjectCreate(name, path, sharePath string) (string, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Docker.Project"}, "version": {"1"},
		"method": {"create"}, "name": {name},
		"path": {path}, "share_path": {sharePath}, "_sid": {c.Sid},
	})
}

// DockerContainerStop 停止容器
func (c *Client) DockerContainerStop(name string) (string, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Docker.Container"}, "version": {"1"},
		"method": {"stop"}, "name": {name}, "_sid": {c.Sid},
	})
}

// ---- 用户管理 ----

// UserList 列出用户
func (c *Client) UserList() (string, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Core.User"}, "version": {"1"},
		"method": {"list"}, "_sid": {c.Sid},
	})
}

// ---- 共享权限 ----

// SharePermissionList 查询共享文件夹权限
func (c *Client) SharePermissionList(name string) (string, error) {
	return c.get("/webapi/entry.cgi", url.Values{
		"api": {"SYNO.Core.Share.Permission"}, "version": {"1"},
		"method": {"list"}, "name": {name},
		"offset": {"0"}, "limit": {"50"}, "action": {"enum"},
		"is_unite_permission": {"false"}, "with_inherit": {"false"},
		"user_group_type": {"local_user"}, "_sid": {c.Sid},
	})
}
