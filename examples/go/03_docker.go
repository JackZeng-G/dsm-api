package main

import (
	"fmt"

	dsm "github.com/example/dsm-api/scripts/go"
)

func main() {
	api := dsm.NewClient(
		getenv("NAS_IP", "192.168.1.10"),
		getenv("NAS_USER", "admin"),
		getenv("NAS_PASS", "password"),
	)
	fmt.Println("=== DSM API Docker 示例 ===")

	api.Login("FileStation")

	fmt.Println("\n--- Docker 项目列表 ---")
	body, _ := api.DockerProjectList()
	fmt.Println(body)

	api.Logout()
	fmt.Println("\n=== Docker 示例完成 ===")
}
