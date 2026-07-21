package com.dsm.examples;

import com.dsm.api.DsmApi;

/**
 * 示例: 认证与连通性测试
 * 用法: javac -cp ../../scripts/java ../../scripts/java/com/dsm/api/DsmApi.java DsmApiAuth.java
 *       NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password java -cp ../../scripts/java:. DsmApiAuth
 */
public class DsmApiAuth {
    public static void main(String[] args) throws Exception {
        DsmApi api = new DsmApi();
        System.out.println("=== DSM API 认证示例 ===");
        System.out.println("NAS: " + api.getHost());

        // 1. 连通性测试
        System.out.println("\n--- 1. 连通性测试 ---");
        System.out.println(api.connectivityTest());

        // 2. 登录
        System.out.println("\n--- 2. 登录 ---");
        String resp = api.login("FileStation");
        System.out.println("SID: " + api.getSid().substring(0, Math.min(20, api.getSid().length())) + "...");

        // 3. 登出
        System.out.println("\n--- 3. 登出 ---");
        System.out.println(api.logout());

        System.out.println("\n=== 认证流程完成 ===");
    }
}
