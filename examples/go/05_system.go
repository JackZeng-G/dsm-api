// 示例: 系统信息与存储健康（CPU/内存/磁盘/SMART，只读）
//
// 用法:
//
//	NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password go run 05_system.go
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
	fmt.Println("=== DSM API 系统与存储示例 ===")

	if _, err := api.Login("FileStation"); err != nil {
		fmt.Printf("登录失败: %v\n", err)
		return
	}
	defer api.Logout()

	// ---- 1. CPU/内存/磁盘利用率（手册七章 7.1）----
	fmt.Println("\n--- 系统利用率 ---")
	if resp, err := api.SystemUtilization(); err != nil {
		fmt.Printf("  查询失败: %v\n", err)
	} else {
		data, _ := resp["data"].(map[string]interface{})
		cpu, _ := data["cpu"].(map[string]interface{})
		mem, _ := data["memory"].(map[string]interface{})
		fmt.Printf("  CPU 用户态: %v%%\n", cpu["user_load"])
		fmt.Printf("  内存: %v / %v (MB)\n", mem["memory_usage"], mem["memory_size"])
	}

	// ---- 2. 磁盘列表（手册七章 7.2）----
	fmt.Println("\n--- 磁盘列表 ---")
	if resp, err := api.StorageDiskList(); err != nil {
		fmt.Printf("  查询失败: %v\n", err)
	} else {
		data, _ := resp["data"].(map[string]interface{})
		disks, _ := data["disks"].([]interface{})
		if len(disks) == 0 {
			fmt.Printf("  （返回结构: %v)\n", data)
		}
		for _, dRaw := range disks {
			d, _ := dRaw.(map[string]interface{})
			fmt.Printf("  %v  型号=%v  温度=%v°C  状态=%v\n",
				d["id"], d["model"], d["temp"], d["status"])
		}
	}

	// ---- 3. SMART 健康（手册七章 7.2，version 固定 1）----
	fmt.Println("\n--- SMART 健康 ---")
	if resp, err := api.SmartHealth(); err != nil {
		fmt.Printf("  查询失败: %v\n", err)
	} else {
		data, _ := resp["data"].(map[string]interface{})
		disks, _ := data["disks"].([]interface{})
		if len(disks) == 0 {
			fmt.Printf("  （返回结构: %v)\n", data)
		}
		for _, dRaw := range disks {
			d, _ := dRaw.(map[string]interface{})
			fmt.Printf("  %v  健康状态=%v\n", d["id"], d["health"])
		}
	}

	fmt.Println("\n=== 系统与存储示例完成 ===")
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
