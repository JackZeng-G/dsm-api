package com.dsm.examples;

import com.dsm.api.DsmApi;

/** 示例: 文件操作 */
public class DsmApiFile {
    public static void main(String[] args) throws Exception {
        DsmApi api = new DsmApi();
        System.out.println("=== DSM API 文件操作示例 ===");

        api.login("FileStation");

        System.out.println("\n--- 共享文件夹列表 ---");
        System.out.println(api.fsListShares());

        System.out.println("\n--- 创建测试文件夹 ---");
        System.out.println(api.fsCreateFolder("/data", "dsm_test_java"));

        System.out.println("\n--- /data 目录内容 ---");
        System.out.println(api.fsList("/data"));

        api.logout();
        System.out.println("\n=== 文件操作完成 ===");
    }
}
