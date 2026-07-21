package main

import (
	"fmt"
	"os"

	dsm "github.com/example/dsm-api/scripts/go"
)

func main() {
	host := getenv("NAS_IP", "192.168.1.10")
	user := getenv("NAS_USER", "admin")
	pass := getenv("NAS_PASS", "password")

	api := dsm.NewClient(host, user, pass)
	fmt.Printf("=== DSM API 认证示例 ===\n")
	fmt.Printf("NAS: %s\n", host)

	// 1. 连通性测试
	fmt.Println("\n--- 1. 连通性测试 ---")
	body, err := api.ConnectivityTest()
	if err != nil {
		fmt.Printf("错误: %v\n", err)
		return
	}
	fmt.Println(body)

	// 2. 登录
	fmt.Println("\n--- 2. 登录 ---")
	body, err = api.Login("FileStation")
	if err != nil {
		fmt.Printf("错误: %v\n", err)
		return
	}
	fmt.Printf("SID: %.20s...\n", api.Sid)

	// 3. 登出
	fmt.Println("\n--- 3. 登出 ---")
	body, err = api.Logout()
	if err != nil {
		fmt.Printf("错误: %v\n", err)
		return
	}
	fmt.Println(body)

	fmt.Println("\n=== 认证流程完成 ===")
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
