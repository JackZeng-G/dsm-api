#!/usr/bin/env node
/** 示例: Docker 管理 */
const { DsmApi } = require('../../scripts/nodejs/dsm_api');

async function main() {
    const api = new DsmApi();
    console.log('=== DSM API Docker 示例 ===');

    await api.login();
    console.log('\n--- Docker 项目列表 ---');
    console.log(JSON.stringify(await api.dockerProjectList()));

    await api.logout();
    console.log('\n=== Docker 示例完成 ===');
}

main().catch(console.error);
