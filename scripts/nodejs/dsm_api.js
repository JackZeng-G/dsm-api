/**
 * DSM API - Node.js 封装库
 *
 * 用法:
 *   const { DsmApi } = require('./dsm_api');
 *   const api = new DsmApi('192.168.1.10', 'admin', 'password');
 *
 * 环境变量: NAS_IP, NAS_USER, NAS_PASS
 */

const VERSION = '1.0.0';

class DsmApi {
  constructor(host, user, password) {
    this.host = host || process.env.NAS_IP || '192.168.1.10';
    this.user = user || process.env.NAS_USER || 'admin';
    this.password = password || process.env.NAS_PASS || 'password';
    this.base = `https://${this.host}:5001`;
    this.sid = '';
    this.token = '';

    // 跳过自签名证书校验
    this._agent = new (require('https').Agent)({
      rejectUnauthorized: false,
    });
  }

  // ---- 内部方法 ----
  async _request(path, params = {}, method = 'GET') {
    const url = new URL(path, this.base);
    if (method === 'GET') {
      for (const [k, v] of Object.entries(params)) {
        url.searchParams.set(k, v);
      }
    }

    const options = {
      method,
      agent: this._agent,
      headers: {},
    };

    if (method === 'POST') {
      options.headers['Content-Type'] = 'application/x-www-form-urlencoded';
      options.body = new URLSearchParams(params).toString();
    }

    const resp = await fetch(url.toString(), options);
    return resp.json();
  }

  _get(path, params = {}) {
    return this._request(path, params, 'GET');
  }

  _post(path, data = {}) {
    return this._request(path, data, 'POST');
  }

  // ---- 认证 ----
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
    if (resp.success) {
      this.sid = resp.data?.sid || '';
      this.token = resp.data?.synotoken || '';
    }
    return resp;
  }

  async logout() {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.API.Auth', version: '6',
      method: 'logout', _sid: this.sid,
    });
  }

  // ---- API 发现 ----
  async apiQuery(api = 'all') {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.API.Info', version: '1',
      method: 'query', query: api,
    });
  }

  // ---- 文件操作 ----
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

  async fsCopyMoveStart(path, destPath, removeSrc = false, overwrite = false) {
    return this._post('/webapi/entry.cgi', {
      api: 'SYNO.FileStation.CopyMove', version: '3',
      method: 'start', path, dest_folder_path: destPath,
      remove_src: String(removeSrc), overwrite: String(overwrite),
      _sid: this.sid,
    });
  }

  async fsCopyMoveStatus(taskid) {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.FileStation.CopyMove', version: '3',
      method: 'status', taskid, _sid: this.sid,
    });
  }

  // ---- Docker 管理 ----
  async dockerProjectList() {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Docker.Project', version: '1',
      method: 'list', _sid: this.sid,
    });
  }

  async dockerProjectCreate(name, path, sharePath) {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Docker.Project', version: '1',
      method: 'create', name, path, share_path: sharePath, _sid: this.sid,
    });
  }

  async dockerContainerStop(name) {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Docker.Container', version: '1',
      method: 'stop', name, _sid: this.sid,
    });
  }

  // ---- 用户管理 ----
  async userList() {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Core.User', version: '1',
      method: 'list', _sid: this.sid,
    });
  }

  // ---- 共享权限 ----
  async sharePermissionList(name = 'data') {
    return this._get('/webapi/entry.cgi', {
      api: 'SYNO.Core.Share.Permission', version: '1',
      method: 'list', name, offset: '0', limit: '50',
      action: 'enum', is_unite_permission: 'false',
      with_inherit: 'false', user_group_type: 'local_user',
      _sid: this.sid,
    });
  }
}

// ---- 便捷导入 ----
const defaultApi = new DsmApi();
function getApi() { return defaultApi; }

module.exports = { DsmApi, getApi, VERSION };
