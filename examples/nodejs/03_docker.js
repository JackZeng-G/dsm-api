#!/usr/bin/env node
/**
 * 示例: Docker 项目管理（含手册四章 4.1 清理 → 重建顺序）
 *
 * ⚠️ 删除/重建是破坏性操作。本示例默认只读（list + 状态查询），
 *    清理重建流程以注释展示，确认目标后取消注释执行。
 *
 * 用法: NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password node 03_docker.js
 */
const { DsmApi } = require('../../scripts/nodejs/dsm_api');

async function main() {
  const api = new DsmApi();
  console.log('=== DSM API Docker 项目示例 ===');

  try {
    await api.login();
  } catch (e) {
    console.log(`登录失败: ${e.message}`);
    return;
  }

  // ---- 1. 列出项目 + 状态 ----
  console.log('\n--- Docker 项目列表 ---');
  try {
    const resp = await api.dockerProjectList();
    const projects = (resp.data && resp.data) || {};
    const ids = Object.keys(projects);
    if (ids.length === 0) {
      console.log('  （无项目）');
    }
    for (const pid of ids) {
      const info = projects[pid] || {};
      console.log(`  id=${pid}  name=${info.name}  status=${info.status}`);
    }
  } catch (e) {
    console.log(`  列表失败: ${e.message}`);
  }

  // ---- 2. 清理 → 重建顺序（手册 4.1，破坏性，默认不执行）----
  // 核心陷阱：RUNNING 状态 delete 返回假成功 → 下次 build 报 2104。
  // 正确顺序：stop(id) → 轮询 list 等 status=stopped → delete(id) → 验证消失 → create → build
  //
  // const TARGET_ID = '<要清理的项目ID>';
  // await api.dockerProjectStop(TARGET_ID);
  // for (;;) {                       // 轮询等 STOPPED
  //   const list = (await api.dockerProjectList()).data || {};
  //   const info = list[TARGET_ID] || {};
  //   if (info.status !== 'running') break;
  //   await new Promise(r => setTimeout(r, 2000));
  // }
  // await api.dockerProjectDelete(TARGET_ID);  // STOPPED 才真删
  // await api.dockerProjectCreate('myapp', '/volume1/docker/myapp', '/docker/myapp');
  // await api.dockerProjectBuild('<新项目ID>');
  console.log('\n--- 清理重建流程（手册 4.1，破坏性，见源码注释，默认不执行）---');

  try {
    await api.logout();
  } catch (e) {
    console.log(`登出失败: ${e.message}`);
  }

  console.log('\n=== Docker 示例完成 ===');
}

main().catch(e => console.error(e));
