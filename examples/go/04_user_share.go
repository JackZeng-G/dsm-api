// 示例: 用户与共享权限查询（只读，安全）
//
// 用法:
//
//	NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password go run 04_user_share.go
//	SHARE_NAME=data go run 04_user_share.go
package main

import (
	"fmt"
	"os"

	dsm "github.com/example/dsm-api/scripts/go"
)

func main() {
	api := dsm.NewClient(
		getenv("NAS_IP", "192.168.1.10"),
		getenv("NAS_USER", "admin"),
		getenv("NAS_PASS", "password"),
	)
	shareName := getenv("SHARE_NAME", "data")
	fmt.Println("=== DSM API 用户与共享权限示例 ===")

	if _, err := api.Login("FileStation"); err != nil {
		fmt.Printf("登录失败: %v\n", err)
		return
	}
	defer api.Logout()

	// ---- 1. 用户列表（手册六章）----
	fmt.Println("\n--- 系统用户列表 ---")
	if resp, err := api.UserList(); err != nil {
		fmt.Printf("  查询失败: %v\n", err)
	} else {
		data, _ := resp["data"].(map[string]interface{})
		users, _ := data["users"].([]interface{})
		if len(users) == 0 {
			fmt.Println("  （无用户或字段结构不同）")
		}
		for _, uRaw := range users {
			u, _ := uRaw.(map[string]interface{})
			desc, _ := u["description"].(string)
			fmt.Printf("  %v  (%s)\n", u["name"], desc)
		}
	}

	// ---- 2. 共享文件夹权限（手册五章，仅共享级别）----
	fmt.Printf("\n--- 共享文件夹 [%s] 权限 ---\n", shareName)
	if resp, err := api.SharePermissionList(shareName); err != nil {
		fmt.Printf("  查询失败: %v\n", err)
		fmt.Println("  提示: 子目录权限不支持，仅共享文件夹级别（手册五章 5.1）。")
	} else {
		data, _ := resp["data"].(map[string]interface{})
		// 兼容两种返回结构：data.acl.acl 或 data.permissions
		perms := permsFromData(data)
		if len(perms) == 0 {
			fmt.Printf("  （无权限条目，或共享不存在。返回: %v)\n", data)
		}
		for _, pRaw := range perms {
			p, _ := pRaw.(map[string]interface{})
			name, _ := p["name"].(string)
			if name == "" {
				name = "?"
			}
			fmt.Printf("  %s: %s\n", name, permLabel(p))
		}
	}

	fmt.Println("\n=== 用户与权限示例完成 ===")
}

// permsFromData 兼容 data.acl.acl 与 data.permissions 两种结构
func permsFromData(data map[string]interface{}) []interface{} {
	if acl, ok := data["acl"].(map[string]interface{}); ok {
		if inner, ok := acl["acl"].([]interface{}); ok {
			return inner
		}
	}
	if perms, ok := data["permissions"].([]interface{}); ok {
		return perms
	}
	return nil
}

// permLabel 根据可读/可写字段输出中文权限标签
func permLabel(p map[string]interface{}) string {
	if w, _ := p["is_writable"].(bool); w {
		return "读写"
	}
	if r, _ := p["is_readonly"].(bool); r {
		return "只读"
	}
	if d, _ := p["is_deny"].(bool); d {
		return "拒绝"
	}
	return "无"
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
