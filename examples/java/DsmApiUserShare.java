package com.dsm.examples;

import com.dsm.api.DsmApi;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 示例: 用户与共享权限查询（只读，安全）
 *
 * 用法:
 *   javac -cp ../../scripts/java DsmApiUserShare.java
 *   NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password java -cp ../../scripts/java:. DsmApiUserShare
 *   SHARE_NAME=data NAS_IP=... java -cp ../../scripts/java:. DsmApiUserShare
 */
public class DsmApiUserShare {

    // 要查询的共享文件夹名（按你 NAS 实际改；可用 SHARE_NAME 环境变量覆盖）
    private static final String SHARE_NAME =
        System.getenv().getOrDefault("SHARE_NAME", "data");

    // 匹配含 name 字段的对象：<{ ... "name":"X" ... }>
    private static final Pattern NAME_OBJ = Pattern.compile(
        "\\{([^{}]*\"name\"\\s*:\\s*\"[^\"]+\"[^{}]*)\\}", Pattern.DOTALL);

    public static void main(String[] args) {
        DsmApi api = new DsmApi();
        System.out.println("=== DSM API 用户与共享权限示例 ===");
        System.out.println("NAS: " + api.getHost());

        try {
            api.login();
        } catch (RuntimeException e) {
            System.out.println("登录失败: " + e.getMessage());
            return;
        }

        // ---- 1. 用户列表（手册六章）----
        System.out.println("\n--- 系统用户列表 ---");
        try {
            String resp = api.userList();
            Matcher m = NAME_OBJ.matcher(resp);
            boolean found = false;
            while (m.find()) {
                String inner = m.group(1);
                String name = DsmApi.extractJsonString(inner, "name");
                String desc = DsmApi.extractJsonString(inner, "description");
                if (!name.isEmpty()) {
                    found = true;
                    System.out.println("  " + name + "  (" + desc + ")");
                }
            }
            if (!found) {
                System.out.println("  （无用户或字段结构不同。原始响应:）");
                System.out.println("  " + resp);
            }
        } catch (RuntimeException e) {
            System.out.println("  查询失败: " + e.getMessage());
        }

        // ---- 2. 共享文件夹权限（手册五章，仅共享级别）----
        System.out.println("\n--- 共享文件夹 [" + SHARE_NAME + "] 权限 ---");
        try {
            String resp = api.sharePermissionList(SHARE_NAME);
            Matcher m = NAME_OBJ.matcher(resp);
            boolean found = false;
            while (m.find()) {
                String inner = m.group(1);
                String name = DsmApi.extractJsonString(inner, "name");
                if (name.isEmpty()) continue;
                String perm;
                if (inner.contains("\"is_writable\":true")
                        || inner.contains("\"is_writable\":\"true\"")) {
                    perm = "读写";
                } else if (inner.contains("\"is_readonly\":true")
                        || inner.contains("\"is_readonly\":\"true\"")) {
                    perm = "只读";
                } else if (inner.contains("\"is_deny\":true")
                        || inner.contains("\"is_deny\":\"true\"")) {
                    perm = "拒绝";
                } else {
                    perm = "无";
                }
                found = true;
                System.out.println("  " + name + ": " + perm);
            }
            if (!found) {
                System.out.println("  （无权限条目，或共享不存在。原始响应:）");
                System.out.println("  " + resp);
            }
        } catch (RuntimeException e) {
            System.out.println("  查询失败: " + e.getMessage());
            System.out.println("  提示: 子目录权限不支持，仅共享文件夹级别（手册五章 5.1）。");
        }

        try {
            api.logout();
        } catch (RuntimeException e) {
            System.out.println("登出失败: " + e.getMessage());
        }

        System.out.println("\n=== 用户与权限示例完成 ===");
    }
}
