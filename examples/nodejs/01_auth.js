#!/usr/bin/env node
/**
 * 示例: 认证与连通性测试
 * 用法: NAS_IP=192.168.1.10 NAS_USER=admin NAS_PASS=password node 01_auth.js
 */
const { DsmApi } = require('../../scripts/nodejs/dsm_api');

async function main() {
    const api = new DsmApi();
    console.log('=== DSM API 认证示例 ===');
    console.log(`NAS: ${api.host}`);

    // 1. 连通性测试
    console.log('\n--- 1. 连通性测试 ---');
    let resp = await api.connectivityTest();
    console.log(`成功: ${resp.success}`);

    // 2. 登录
    console.log('\n--- 2. 登录 ---');
    resp = await api.login();
    console.log(`成功: ${resp.success}, SID: ${api.sid.substring(0, 20)}...`);

    // 3. 登出
    console.log('\n--- 3. 登出 ---');
    resp = await api.logout();
    console.log(`成功: ${resp.success}`);

    console.log('\n=== 认证流程完成 ===');
}

main().catch(console.error);
