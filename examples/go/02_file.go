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
	fmt.Println("=== DSM API 文件操作示例 ===")

	api.Login("FileStation")

	fmt.Println("\n--- 共享文件夹列表 ---")
	body, _ := api.FSListShares()
	fmt.Println(body)

	fmt.Println("\n--- 创建测试文件夹 ---")
	body, _ = api.FSCreateFolder("/data", "dsm_test_golang")
	fmt.Println(body)

	fmt.Println("\n--- /data 目录内容 ---")
	body, _ = api.FSList("/data", "")
	fmt.Println(body)

	api.Logout()
	fmt.Println("\n=== 文件操作完成 ===")
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
