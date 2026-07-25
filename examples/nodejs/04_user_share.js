#!/usr/bin/env node
/**
 * 示例: 用户与共享权限查询（只读，安全）
 *
 * 用法: NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password node 04_user_share.js
 *       SHARE_NAME=data node 04_user_share.js
 */
const { DsmApi } = require('../../scripts/nodejs/dsm_api');

// 要查询的共享文件夹名（按你 NAS 实际改）
const SHARE_NAME = process.env.SHARE_NAME || 'data';

async function main() {
  const api = new DsmApi();
  console.log('=== DSM API 用户与共享权限示例 ===');

  try {
    await api.login();
  } catch (e) {
    console.log(`登录失败: ${e.message}`);
    return;
  }

  // ---- 1. 用户列表（手册六章）----
  console.log('\n--- 系统用户列表 ---');
  try {
    const resp = await api.userList();
    const users = (resp.data && resp.data.users) || [];
    if (users.length === 0) {
      console.log('  （无用户或字段结构不同）');
    }
    for (const u of users) {
      console.log(`  ${u.name}  (${u.description || ''})`);
    }
  } catch (e) {
    console.log(`  查询失败: ${e.message}`);
  }

  // ---- 2. 共享文件夹权限（手册五章，仅共享级别）----
  console.log(`\n--- 共享文件夹 [${SHARE_NAME}] 权限 ---`);
  try {
    const resp = await api.sharePermissionList(SHARE_NAME);
    const data = resp.data || {};
    let perms = (data.acl && data.acl.acl) || [];
    if (perms.length === 0) {
      // 兼容另一种返回结构
      perms = data.permissions || [];
    }
    if (perms.length === 0) {
      console.log(`  （无权限条目，或共享不存在。返回: ${JSON.stringify(data)})`);
    }
    for (const p of perms) {
      const name = p.name != null ? p.name : '?';
      let perm = '无';
      if (p.is_writable) perm = '读写';
      else if (p.is_readonly) perm = '只读';
      else if (p.is_deny) perm = '拒绝';
      console.log(`  ${name}: ${perm}`);
    }
  } catch (e) {
    console.log(`  查询失败: ${e.message}`);
    console.log('  提示: 子目录权限不支持，仅共享文件夹级别（手册五章 5.1）。');
  }

  try {
    await api.logout();
  } catch (e) {
    console.log(`登出失败: ${e.message}`);
  }

  console.log('\n=== 用户与权限示例完成 ===');
}

main().catch(e => console.error(e));
