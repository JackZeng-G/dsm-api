package com.dsm.examples;

import com.dsm.api.DsmApi;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 示例: Docker 项目管理（含手册四章 4.1 清理 → 重建顺序）
 *
 * ⚠️ 删除/重建是破坏性操作。本示例默认只读（list + 状态查询），
 *    清理重建流程以注释展示，确认目标后取消注释执行。
 *
 * 用法:
 *   javac -cp ../../scripts/java DsmApiDocker.java
 *   NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password java -cp ../../scripts/java:. DsmApiDocker
 */
public class DsmApiDocker {
    // 匹配项目对象：<"id">:{ ... "name":"X" ... "status":"Y" ... }，name/status 顺序不限
    private static final Pattern PROJECT_PATTERN = Pattern.compile(
        "\"([^\"]+)\"\\s*:\\s*\\{(?=[^{}]*\"name\"\\s*:\\s*\"([^\"]+)\")" +
        "(?=[^{}]*\"status\"\\s*:\\s*\"([^\"]+)\")[^{}]*\\}",
        Pattern.DOTALL
    );

    public static void main(String[] args) {
        DsmApi api = new DsmApi();
        System.out.println("=== DSM API Docker 项目示例 ===");
        System.out.println("NAS: " + api.getHost());

        try {
            api.login();
            System.out.println("登录成功，SID: "
                + api.getSid().substring(0, Math.min(20, api.getSid().length())) + "...");
        } catch (RuntimeException e) {
            System.out.println("登录失败: " + e.getMessage());
            return;
        }

        // ---- 1. 列出项目 + 状态 ----
        System.out.println("\n--- Docker 项目列表 ---");
        try {
            String resp = api.dockerProjectList();
            Matcher m = PROJECT_PATTERN.matcher(resp);
            boolean found = false;
            while (m.find()) {
                found = true;
                System.out.println("  id=" + m.group(1)
                    + "  name=" + m.group(2)
                    + "  status=" + m.group(3));
            }
            if (!found) {
                System.out.println("  （无项目，或返回结构不同。原始响应:）");
                System.out.println("  " + resp);
            }
        } catch (RuntimeException e) {
            System.out.println("  列表失败: " + e.getMessage());
        }

        // ---- 2. 清理 → 重建顺序（手册 4.1，破坏性，默认不执行）----
        // 核心陷阱：RUNNING 状态 delete 返回假成功 → 下次 build 报 2104。
        // 正确顺序：stop(id) → 轮询 list 等 status=stopped → delete(id) → 验证消失 → create → build
        //
        // String targetId = "<要清理的项目ID>";
        // api.dockerProjectStop(targetId);
        // while (true) {                                  // 轮询等 STOPPED
        //     Matcher m = PROJECT_PATTERN.matcher(api.dockerProjectList());
        //     boolean stopped = true;
        //     while (m.find()) {
        //         if (m.group(1).equals(targetId) && m.group(3).equalsIgnoreCase("running")) {
        //             stopped = false; break;
        //         }
        //     }
        //     if (stopped) break;
        //     try { Thread.sleep(2000); } catch (InterruptedException ie) { break; }
        // }
        // api.dockerProjectDelete(targetId);              // STOPPED 才真删
        // api.dockerProjectCreate("myapp", "/volume1/docker/myapp", "/docker/myapp");
        // api.dockerProjectBuild("<新项目ID>");
        System.out.println("\n--- 清理重建流程（手册 4.1，破坏性，见源码注释，默认不执行）---");

        try {
            api.logout();
        } catch (RuntimeException e) {
            System.out.println("登出失败: " + e.getMessage());
        }

        System.out.println("\n=== Docker 示例完成 ===");
    }
}
