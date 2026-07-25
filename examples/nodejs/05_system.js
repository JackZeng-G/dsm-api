#!/usr/bin/env node
/**
 * 示例: 系统信息与存储健康（CPU/内存/磁盘/SMART，只读）
 *
 * 用法: NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password node 05_system.js
 */
const { DsmApi } = require('../../scripts/nodejs/dsm_api');

async function main() {
  const api = new DsmApi();
  console.log('=== DSM API 系统与存储示例 ===');

  try {
    await api.login();
  } catch (e) {
    console.log(`登录失败: ${e.message}`);
    return;
  }

  // ---- 1. CPU/内存/磁盘利用率（手册七章 7.1）----
  console.log('\n--- 系统利用率 ---');
  try {
    const resp = await api.systemUtilization();
    const data = resp.data || {};
    const cpu = (data.cpu && data.cpu.user_load) != null ? data.cpu.user_load : '?';
    const mem = data.memory || {};
    const memUsage = mem.memory_usage != null ? mem.memory_usage : '?';
    const memTotal = mem.memory_size != null ? mem.memory_size : '?';
    console.log(`  CPU 用户态: ${cpu}%`);
    console.log(`  内存: ${memUsage} / ${memTotal} (MB)`);
  } catch (e) {
    console.log(`  查询失败: ${e.message}`);
  }

  // ---- 2. 磁盘列表（手册七章 7.2）----
  console.log('\n--- 磁盘列表 ---');
  try {
    const resp = await api.storageDiskList();
    const disks = (resp.data && resp.data.disks) || [];
    if (disks.length === 0) {
      console.log(`  （返回结构: ${JSON.stringify(resp.data)})`);
    }
    for (const d of disks) {
      console.log(`  ${d.id || '?'}  型号=${d.model || '?'}  温度=${d.temp != null ? d.temp : '?'}°C  状态=${d.status || '?'}`);
    }
  } catch (e) {
    console.log(`  查询失败: ${e.message}`);
  }

  // ---- 3. SMART 健康（手册七章 7.2，version 固定 1）----
  console.log('\n--- SMART 健康 ---');
  try {
    const resp = await api.smartHealth();
    const data = resp.data || {};
    const disks = data.disks || [];
    if (disks.length === 0) {
      console.log(`  （返回结构: ${JSON.stringify(data)})`);
    }
    for (const d of disks) {
      console.log(`  ${d.id || '?'}  健康状态=${d.health || '?'}`);
    }
  } catch (e) {
    console.log(`  查询失败: ${e.message}`);
  }

  try {
    await api.logout();
  } catch (e) {
    console.log(`登出失败: ${e.message}`);
  }

  console.log('\n=== 系统与存储示例完成 ===');
}

main().catch(e => console.error(e));
