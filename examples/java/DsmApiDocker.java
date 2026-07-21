package com.dsm.examples;

import com.dsm.api.DsmApi;

/** 示例: Docker 管理 */
public class DsmApiDocker {
    public static void main(String[] args) throws Exception {
        DsmApi api = new DsmApi();
        System.out.println("=== DSM API Docker 示例 ===");

        api.login("FileStation");

        System.out.println("\n--- Docker 项目列表 ---");
        System.out.println(api.dockerProjectList());

        api.logout();
        System.out.println("\n=== Docker 示例完成 ===");
    }
}
