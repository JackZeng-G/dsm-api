#!/usr/bin/env node
/** 示例: 文件操作 */
const { DsmApi } = require('../../scripts/nodejs/dsm_api');

async function main() {
    const api = new DsmApi();
    console.log('=== DSM API 文件操作示例 ===');

    await api.login();

    console.log('\n--- 共享文件夹列表 ---');
    let resp = await api.fsListShares();
    const shares = resp.data?.shares || [];
    shares.forEach(s => console.log(`  ${s.name}: ${s.path}`));

    console.log('\n--- 创建测试文件夹 ---');
    resp = await api.fsCreateFolder('/data', `dsm_test_${Date.now()}`);
    console.log(JSON.stringify(resp));

    console.log('\n--- /data 目录内容 ---');
    resp = await api.fsList('/data');
    const files = resp.data?.files || [];
    files.forEach(f => console.log(`  [${f.isdir ? 'DIR' : 'FILE'}] ${f.name}`));

    await api.logout();
    console.log('\n=== 文件操作完成 ===');
}

main().catch(console.error);
