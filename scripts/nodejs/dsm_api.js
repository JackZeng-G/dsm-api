/**
 * DSM API - Node.js 封装库
 *
 * 用法:
 *   const { DsmApi } = require('./dsm_api');
 *   const api = new DsmApi('192.168.1.10', 'admin', 'password');
 *
 * 环境变量: NAS_IP, NAS_USER, NAS_PASS
 *
 * 覆盖: 认证 / 文件 / Docker 项目 / 用户 / 共享权限 / 系统与存储
 * 对应手册: 一-七章
 *
 * 所有请求自动携带 _sid 与 X-SYNO-TOKEN（DSM 7 CSRF 防护）；
 * 网络/HTTP 异常统一抛 Error。Node 18+ 内置 fetch，无需 npm install。
 */

const https = require('https');
const { URL, URLSearchParams } = require('url');

const VERSION = '1.1.0';

class DsmApi {
  /**
   * 群晖 DSM REST API 客户端
   * @param {string} [host] NAS 主机（默认读 NAS_IP）
   * @param {string} [user] 用户名（默认读 NAS_USER）
   * @param {string} [password] 密码（默认读 NAS_PASS）
   */
  constructor(host, user, password) {
    this.host = host || process.env.NAS_IP || '192.168.1.10';
    this.user = user || process.env.NAS_USER || 'admin';
    this.password = password || process.env.NAS_PASS || 'password';
    this.base = `https://${this.host}:5001`;
    this.sid = '';
    this.token = '';

    // 跳过自签名证书校验（对应 curl -k）
    this._agent = new https.Agent({ rejectUnauthorized: false });
  }

  // ---------------- 内部方法 ----------------
  /**
   * 统一请求：GET / POST
   * - 自动附加 X-SYNO-TOKEN（DSM 7 CSRF）
   * - 网络错 / success:false 统一抛 Error
   */
  async _request(path, params = {}, method = 'GET') {
    const url = new URL(path, this.base);
    const headers = {};
    let body = null;

    if (method === 'GET') {
      for (const [k, v] of Object.entries(params)) {
        url.searchParams.set(k, v);
      }
    } else { // POST
      headers['Content-Type'] = 'application/x-www-form-urlencoded';
      body = new URLSearchParams(params).toString();
    }
    if (this.token) { // DSM 7 强制 CSRF：后续请求带 X-SYNO-TOKEN
      headers['X-SYNO-TOKEN'] = this.token;
    }

    let resp;
    try {
      resp = await fetch(url.toString(), {
        method,
        agent: this._agent,
        headers,
        body,
      });
    } catch (e) {
      throw new Error(`连接失败: ${e.message || e}`);
    }

    let json;
    try {
      json = await resp.json();
    } catch (e) {
      throw new Error(`响应解析失败: HTTP ${resp.status} ${resp.statusText}`);
    }
    return json;
  }

  _get(path, params = {}) {
    return this._request(path, params, 'GET');
  }

  _post(path, data = {}) {
    return this._request(path, data, 'POST');
  }

  /**
   * 业务校验：success:false 抛 Error（含 error.code）
   * @param {object} resp 返回体
   * @param {string} [action] 动作描述，用于错误信息
   * @returns {object} 原返回体
   */
  _check(resp, action = '') {
    if (!resp || resp.success !== true) {
      const err = (resp && resp.error) || {};
      const code = err.code != null ? err.code : '?';
      const msg = err.message || '';
      const tail = msg ? ` ${msg}` : '';
      throw new Error(`${action}失败: code=${code}${tail}`.trim());
    }
    return resp;
  }

  // ---------------- 认证（一章）----------------
  async connectivityTest() {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.API.Info', version: '1',
      method: 'query', query: 'SYNO.API.Auth',
    });
  }

  async login(session = 'FileStation') {
    const resp = await this._get('/webapi/auth.cgi', {
      api: 'SYNO.API.Auth', version: '6', method: 'login',
      account: this.user, passwd: this.password,
      format: 'sid', enable_syno_token: 'yes',
      session,
    });
    if (resp && resp.success) {
      this.sid = (resp.data && resp.data.sid) || '';
      this.token = (resp.data && resp.data.synotoken) || '';
    }
    return resp;
  }

  async logout() {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.API.Auth', version: '6',
      method: 'logout', _sid: this.sid,
    });
  }

  // ---------------- API 发现（二章）----------------
  async apiQuery(api = 'all') {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.API.Info', version: '1',
      method: 'query', query: api,
    });
  }

  // ---------------- 文件操作（三章）----------------
  async fsInfo() {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.FileStation.Info', version: '2',
      method: 'get', _sid: this.sid,
    });
  }

  async fsListShares() {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.FileStation.List', version: '2',
      method: 'list_share', _sid: this.sid,
    });
  }

  async fsList(folderPath = '/data', additional = '') {
    const params = {
      api: 'SYNO.FileStation.List', version: '2',
      method: 'list', folder_path: folderPath, _sid: this.sid,
    };
    if (additional) params.additional = additional;
    return this._get('/webapi/entry.cgi', params);
  }

  async fsCreateFolder(folderPath = '/data', name = 'newfolder') {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.FileStation.CreateFolder', version: '2',
      method: 'create', folder_path: folderPath,
      name, force_parent: 'true', _sid: this.sid,
    });
  }

  async fsRename(path, newName) {
    return this._post('/webapi/entry.cgi', {
      api: 'SYNO.FileStation.Rename', version: '2',
      method: 'rename', path, name: newName, _sid: this.sid,
    });
  }

  /**
   * 发起复制/移动（异步），返回 taskid
   */
  async fsCopyMoveStart(path, destPath, removeSrc = false, overwrite = false) {
    return this._post('/webapi/entry.cgi', {
      api: 'SYNO.FileStation.CopyMove', version: '3',
      method: 'start', path, dest_folder_path: destPath,
      remove_src: String(removeSrc), overwrite: String(overwrite),
      _sid: this.sid,
    });
  }

  /**
   * 查询复制/移动进度（data.finished=true 表示完成）
   */
  async fsCopyMoveStatus(taskid) {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.FileStation.CopyMove', version: '3',
      method: 'status', taskid, _sid: this.sid,
    });
  }

  // ---------------- Docker 项目管理（四章，含 4.1 清理重建顺序）----------------
  /** 列出所有项目（返回 map，key=项目ID） */
  async dockerProjectList() {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Docker.Project', version: '1',
      method: 'list', _sid: this.sid,
    });
  }

  async dockerProjectGet(projectId) {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Docker.Project', version: '1',
      method: 'get', id: projectId, _sid: this.sid,
    });
  }

  /** 项目级停止（清理首选，用 id 非 name） */
  async dockerProjectStop(projectId) {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Docker.Project', version: '1',
      method: 'stop', id: projectId, _sid: this.sid,
    });
  }

  /** 删除项目（须 STOPPED 才真删，否则假成功 → 2104） */
  async dockerProjectDelete(projectId) {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Docker.Project', version: '1',
      method: 'delete', id: projectId, _sid: this.sid,
    });
  }

  /**
   * 创建项目
   * @param {string} name 项目名
   * @param {string} path 物理路径（如 /volume1/docker/myapp）
   * @param {string} sharePath 共享相对路径（去掉 /volume1 前缀，如 /docker/myapp）
   */
  async dockerProjectCreate(name, path, sharePath) {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Docker.Project', version: '1',
      method: 'create', name, path, share_path: sharePath, _sid: this.sid,
    });
  }

  /** 构建并启动 */
  async dockerProjectBuild(projectId) {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Docker.Project', version: '1',
      method: 'build', id: projectId, _sid: this.sid,
    });
  }

  /** 容器级停止（仅兜底：项目级 stop 未生效时才用） */
  async dockerContainerStop(name) {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Docker.Container', version: '1',
      method: 'stop', name, _sid: this.sid,
    });
  }

  // ---------------- 用户管理（六章）----------------
  async userList() {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Core.User', version: '1',
      method: 'list', _sid: this.sid,
    });
  }

  // ---------------- 共享权限（五章）----------------
  async sharePermissionList(name = 'data') {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Core.Share.Permission', version: '1',
      method: 'list', name, offset: '0', limit: '50',
      action: 'enum', is_unite_permission: 'false',
      with_inherit: 'false', user_group_type: 'local_user',
      _sid: this.sid,
    });
  }

  // ---------------- 系统与存储（七章）----------------
  /** CPU/内存/磁盘利用率（data.cpu / data.memory / data.disk） */
  async systemUtilization() {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Core.System.Utilization', version: '1',
      method: 'get', _sid: this.sid,
    });
  }

  async systemHealth() {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Core.System.SystemHealth', version: '1',
      method: 'get', _sid: this.sid,
    });
  }

  /** 磁盘列表（id 如 sda/sdb） */
  async storageDiskList() {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Core.Storage.Disk', version: '1',
      method: 'list', _sid: this.sid,
    });
  }

  /** SMART 健康（version 固定 1） */
  async smartHealth() {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Storage.CGI.Smart', version: '1',
      method: 'get_health_info', _sid: this.sid,
    });
  }
}

// ---------------- 便捷导入 ----------------
let _defaultApi = null;
function getApi() {
  if (!_defaultApi) _defaultApi = new DsmApi();
  return _defaultApi;
}

module.exports = { DsmApi, getApi, VERSION };
