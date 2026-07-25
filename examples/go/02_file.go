package main

import (
	"encoding/json"
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

	if _, err := api.Login("FileStation"); err != nil {
		fmt.Printf("登录失败: %v\n", err)
		return
	}
	defer api.Logout()

	fmt.Println("\n--- 共享文件夹列表 ---")
	body, err := api.FSListShares()
	if err != nil {
		fmt.Printf("查询失败: %v\n", err)
		return
	}
	printJSON(body)

	fmt.Println("\n--- 创建测试文件夹 ---")
	body, err = api.FSCreateFolder("/data", "dsm_test_golang")
	if err != nil {
		fmt.Printf("创建失败: %v\n", err)
		return
	}
	printJSON(body)

	fmt.Println("\n--- /data 目录内容 ---")
	body, err = api.FSList("/data", "")
	if err != nil {
		fmt.Printf("查询失败: %v\n", err)
		return
	}
	printJSON(body)

	fmt.Println("\n=== 文件操作完成 ===")
}

// printJSON 以缩进 JSON 打印 map 响应
func printJSON(v interface{}) {
	b, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		fmt.Printf("%v\n", v)
		return
	}
	fmt.Println(string(b))
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
