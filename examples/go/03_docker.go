// 示例: Docker 项目管理（含手册四章 4.1 清理 → 重建顺序）
//
// ⚠️ 删除/重建是破坏性操作。本示例默认只读（list + 状态查询），
// 清理重建流程以注释展示，确认目标后取消注释执行。
//
// 用法:
//
//	NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password go run 03_docker.go
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
	fmt.Println("=== DSM API Docker 项目示例 ===")

	if _, err := api.Login("FileStation"); err != nil {
		fmt.Printf("登录失败: %v\n", err)
		return
	}
	defer api.Logout()

	// ---- 1. 列出项目 + 状态 ----
	fmt.Println("\n--- Docker 项目列表 ---")
	resp, err := api.DockerProjectList()
	if err != nil {
		fmt.Printf("  列表失败: %v\n", err)
	} else {
		projects, _ := resp["data"].(map[string]interface{})
		if len(projects) == 0 {
			fmt.Println("  （无项目）")
		}
		for pid, infoRaw := range projects {
			info, _ := infoRaw.(map[string]interface{})
			fmt.Printf("  id=%s  name=%v  status=%v\n", pid, info["name"], info["status"])
		}
	}

	// ---- 2. 清理 → 重建顺序（手册 4.1，破坏性，默认不执行）----
	// 核心陷阱：RUNNING 状态 delete 返回假成功 → 下次 build 报 2104。
	// 正确顺序：stop(id) → 轮询 list 等 status=stopped → delete(id) → 验证消失 → create → build
	//
	// targetID := "<要清理的项目ID>"
	// if _, err := api.DockerProjectStop(targetID); err != nil {
	//     fmt.Printf("停止失败: %v\n", err)
	//     return
	// }
	// for { // 轮询等 STOPPED
	//     list, _ := api.DockerProjectList()
	//     info, _ := list["data"].(map[string]interface{})[targetID].(map[string]interface{})
	//     if fmt.Sprintf("%v", info["status"]) != "running" {
	//         break
	//     }
	//     time.Sleep(2 * time.Second)
	// }
	// api.DockerProjectDelete(targetID)                              // STOPPED 才真删
	// api.DockerProjectCreate("myapp", "/volume1/docker/myapp", "/docker/myapp")
	// api.DockerProjectBuild("<新项目ID>")
	fmt.Println("\n--- 清理重建流程（手册 4.1，破坏性，见源码注释，默认不执行）---")

	fmt.Println("\n=== Docker 示例完成 ===")
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
