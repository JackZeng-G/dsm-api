package com.dsm.examples;

import com.dsm.api.DsmApi;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 示例: 系统信息与存储健康（CPU/内存/磁盘/SMART，只读）
 *
 * 用法:
 *   javac -cp ../../scripts/java DsmApiSystem.java
 *   NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password java -cp ../../scripts/java:. DsmApiSystem
 */
public class DsmApiSystem {

    // 匹配磁盘对象：<{ ... "id":"sda" ... }>（id/status/health/name 等字段，顺序不限）
    private static final Pattern DISK_OBJ = Pattern.compile(
        "\\{([^{}]*\"id\"\\s*:\\s*\"[^\"]+\"[^{}]*)\\}", Pattern.DOTALL);

    public static void main(String[] args) {
        DsmApi api = new DsmApi();
        System.out.println("=== DSM API 系统与存储示例 ===");
        System.out.println("NAS: " + api.getHost());

        try {
            api.login();
        } catch (RuntimeException e) {
            System.out.println("登录失败: " + e.getMessage());
            return;
        }

        // ---- 1. CPU/内存/磁盘利用率（手册七章 7.1）----
        System.out.println("\n--- 系统利用率 ---");
        try {
            String resp = api.systemUtilization();
            String cpuUser = DsmApi.extractJsonValue(resp, "user_load");
            if (cpuUser.isEmpty()) cpuUser = "?";
            String memUsage = DsmApi.extractJsonValue(resp, "memory_usage");
            if (memUsage.isEmpty()) memUsage = "?";
            String memTotal = DsmApi.extractJsonValue(resp, "memory_size");
            if (memTotal.isEmpty()) memTotal = "?";
            System.out.println("  CPU 用户态: " + cpuUser + "%");
            System.out.println("  内存: " + memUsage + " / " + memTotal + " (MB)");
        } catch (RuntimeException e) {
            System.out.println("  查询失败: " + e.getMessage());
        }

        // ---- 2. 磁盘列表（手册七章 7.2）----
        System.out.println("\n--- 磁盘列表 ---");
        try {
            String resp = api.storageDiskList();
            Matcher m = DISK_OBJ.matcher(resp);
            boolean found = false;
            while (m.find()) {
                String inner = m.group(1);
                String id = DsmApi.extractJsonString(inner, "id");
                if (id.isEmpty()) continue;
                String model = DsmApi.extractJsonString(inner, "model");
                String temp = DsmApi.extractJsonValue(inner, "temp");
                String status = DsmApi.extractJsonString(inner, "status");
                if (model.isEmpty()) model = "?";
                if (temp.isEmpty()) temp = "?";
                if (status.isEmpty()) status = "?";
                found = true;
                System.out.println("  " + id + "  型号=" + model
                    + "  温度=" + temp + "°C  状态=" + status);
            }
            if (!found) {
                System.out.println("  （返回结构不同。原始响应:）");
                System.out.println("  " + resp);
            }
        } catch (RuntimeException e) {
            System.out.println("  查询失败: " + e.getMessage());
        }

        // ---- 3. SMART 健康（手册七章 7.2，version 固定 1）----
        System.out.println("\n--- SMART 健康 ---");
        try {
            String resp = api.smartHealth();
            Matcher m = DISK_OBJ.matcher(resp);
            boolean found = false;
            while (m.find()) {
                String inner = m.group(1);
                String id = DsmApi.extractJsonString(inner, "id");
                if (id.isEmpty()) continue;
                String health = DsmApi.extractJsonString(inner, "health");
                if (health.isEmpty()) health = "?";
                found = true;
                System.out.println("  " + id + "  健康状态=" + health);
            }
            if (!found) {
                System.out.println("  （返回结构不同。原始响应:）");
                System.out.println("  " + resp);
            }
        } catch (RuntimeException e) {
            System.out.println("  查询失败: " + e.getMessage());
        }

        try {
            api.logout();
        } catch (RuntimeException e) {
            System.out.println("登出失败: " + e.getMessage());
        }

        System.out.println("\n=== 系统与存储示例完成 ===");
    }
}
