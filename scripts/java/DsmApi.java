package com.dsm.api;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DSM API - Java 封装库
 *
 * 用法:
 *   DsmApi api = new DsmApi("192.168.1.10", "admin", "password");
 *   api.login("FileStation");
 *   System.out.println(api.fsListShares());
 *   api.logout();
 *
 * 环境变量: NAS_IP, NAS_USER, NAS_PASS
 *
 * 所有请求自动携带 _sid 与 X-SYNO-TOKEN（DSM 7 CSRF 防护）；
 * 信任所有证书（自签名）；网络/HTTP 异常与业务 success:false
 * 统一抛 RuntimeException（含 error.code）。
 */
public class DsmApi {

    public static final String VERSION = "1.1.0";

    private final String host;
    private final String user;
    private final String password;
    private final String base;
    private String sid = "";
    private String token = "";

    public DsmApi() {
        this(
            System.getenv().getOrDefault("NAS_IP", "192.168.1.10"),
            System.getenv().getOrDefault("NAS_USER", "admin"),
            System.getenv().getOrDefault("NAS_PASS", "password")
        );
    }

    public DsmApi(String host, String user, String password) {
        this.host = host;
        this.user = user;
        this.password = password;
        this.base = "https://" + host + ":5001";

        // 跳过自签名证书校验
        disableSSLVerification();
    }

    public String getHost() { return host; }
    public String getSid() { return sid; }
    public String getToken() { return token; }

    // ---------------- SSL 跳过（自签名证书）----------------
    private static void disableSSLVerification() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------- 请求核心 ----------------
    /**
     * 统一请求入口：构造 URL、注入 X-SYNO-TOKEN（DSM 7 CSRF）、读取响应、
     * 网络 IOException 与业务 success:false 均抛 RuntimeException（含 error.code）。
     */
    private String request(String method, String path, Map<String, String> params) {
        try {
            String url;
            String body = null;
            if ("GET".equals(method)) {
                url = base + path + (params == null || params.isEmpty()
                    ? "" : "?" + encodeParams(params));
            } else { // POST
                url = base + path;
                body = encodeParams(params);
            }
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            }
            if (token != null && !token.isEmpty()) {
                // DSM 7 强制 CSRF：后续请求带 X-SYNO-TOKEN
                conn.setRequestProperty("X-SYNO-TOKEN", token);
            }
            if (body != null) {
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            String resp = readResponse(conn);
            checkSuccess(resp, "");
            return resp;
        } catch (IOException e) {
            throw new RuntimeException("网络请求失败: " + e.getMessage(), e);
        }
    }

    private String get(String path, Map<String, String> params) {
        return request("GET", path, params);
    }

    private String post(String path, Map<String, String> params) {
        return request("POST", path, params);
    }

    private static String encodeParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "";
        return params.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
        InputStream is;
        try {
            int code = conn.getResponseCode();
            is = code < 400 ? conn.getInputStream() : conn.getErrorStream();
        } catch (IOException e) {
            throw new IOException("HTTP 请求失败: " + e.getMessage(), e);
        }
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return br.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * 业务校验：响应含 "success":false 时抛 RuntimeException（含 error.code）。
     * request() 读取响应后自动调用。
     */
    private static void checkSuccess(String json, String action) {
        if (json == null || json.isEmpty()) return;
        int sucIdx = json.indexOf("\"success\"");
        if (sucIdx == -1) return;
        int colon = json.indexOf(":", sucIdx);
        if (colon == -1) return;
        String tail = json.substring(colon + 1).trim();
        if (!tail.startsWith("false")) return;

        String code = "?";
        String msg = "";
        int errIdx = json.indexOf("\"error\"", colon);
        if (errIdx != -1) {
            int codeKey = json.indexOf("\"code\"", errIdx);
            if (codeKey != -1) {
                code = extractValueFrom(json, json.indexOf(":", codeKey));
            }
            int msgColon = json.indexOf("\"message\"", errIdx);
            if (msgColon != -1) {
                int mq = json.indexOf("\"", json.indexOf(":", msgColon));
                if (mq != -1) {
                    int me = json.indexOf("\"", mq + 1);
                    if (me != -1) msg = json.substring(mq + 1, me);
                }
            }
        }
        String prefix = action == null || action.isEmpty() ? "API" : action;
        throw new RuntimeException(prefix + "失败: code=" + code + (msg.isEmpty() ? "" : " " + msg));
    }

    private static String extractValueFrom(String json, int colonIdx) {
        if (colonIdx == -1) return "";
        int i = colonIdx + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        int j = i;
        while (j < json.length() && json.charAt(j) != ',' && json.charAt(j) != '}') j++;
        return json.substring(i, j).trim();
    }

    // ---------------- 认证（一章）----------------
    public String connectivityTest() {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.API.Info", "version", "1",
            "method", "query", "query", "SYNO.API.Auth"
        ));
    }

    public String login(String session) {
        String body = get("/webapi/auth.cgi", Map.of(
            "api", "SYNO.API.Auth", "version", "6", "method", "login",
            "account", user, "passwd", password,
            "format", "sid", "enable_syno_token", "yes",
            "session", session
        ));

        sid = extractJsonString(body, "sid");
        token = extractJsonString(body, "synotoken");
        return body;
    }

    /** 默认 session=FileStation */
    public String login() {
        return login("FileStation");
    }

    public String logout() {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.API.Auth", "version", "6",
            "method", "logout", "_sid", sid
        ));
    }

    // ---------------- API 发现（二章）----------------
    public String apiQuery(String api) {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.API.Info", "version", "1",
            "method", "query", "query", api
        ));
    }

    // ---------------- 文件操作（三章）----------------
    public String fsInfo() {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.FileStation.Info", "version", "2",
            "method", "get", "_sid", sid
        ));
    }

    public String fsListShares() {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.FileStation.List", "version", "2",
            "method", "list_share", "_sid", sid
        ));
    }

    public String fsList(String folderPath) {
        return fsList(folderPath, "");
    }

    public String fsList(String folderPath, String additional) {
        Map<String, String> params = new java.util.HashMap<>(Map.of(
            "api", "SYNO.FileStation.List", "version", "2",
            "method", "list", "folder_path", folderPath, "_sid", sid
        ));
        if (additional != null && !additional.isEmpty()) params.put("additional", additional);
        return get("/webapi/entry.cgi", params);
    }

    public String fsCreateFolder(String folderPath, String name) {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.FileStation.CreateFolder", "version", "2",
            "method", "create", "folder_path", folderPath,
            "name", name, "force_parent", "true", "_sid", sid
        ));
    }

    public String fsRename(String path, String newName) {
        return post("/webapi/entry.cgi", Map.of(
            "api", "SYNO.FileStation.Rename", "version", "2",
            "method", "rename", "path", path, "name", newName, "_sid", sid
        ));
    }

    public String fsCopyMoveStart(String path, String destPath,
                                   boolean removeSrc, boolean overwrite) {
        return post("/webapi/entry.cgi", Map.of(
            "api", "SYNO.FileStation.CopyMove", "version", "3",
            "method", "start", "path", path,
            "dest_folder_path", destPath,
            "remove_src", String.valueOf(removeSrc).toLowerCase(),
            "overwrite", String.valueOf(overwrite).toLowerCase(),
            "_sid", sid
        ));
    }

    public String fsCopyMoveStatus(String taskid) {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.FileStation.CopyMove", "version", "3",
            "method", "status", "taskid", taskid, "_sid", sid
        ));
    }

    // ---------------- Docker 项目管理（四章，含 4.1 清理重建顺序）----------------
    public String dockerProjectList() {
        // 列出所有项目（返回 map，key=项目ID）
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Docker.Project", "version", "1",
            "method", "list", "_sid", sid
        ));
    }

    public String dockerProjectGet(String projectId) {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Docker.Project", "version", "1",
            "method", "get", "id", projectId, "_sid", sid
        ));
    }

    /** 项目级停止（清理首选，用 id 非 name）*/
    public String dockerProjectStop(String projectId) {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Docker.Project", "version", "1",
            "method", "stop", "id", projectId, "_sid", sid
        ));
    }

    /** 删除项目（须 STOPPED 才真删，否则假成功 → 2104）*/
    public String dockerProjectDelete(String projectId) {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Docker.Project", "version", "1",
            "method", "delete", "id", projectId, "_sid", sid
        ));
    }

    /** 创建项目（path=物理路径，share_path=去掉 /volume1 前缀）*/
    public String dockerProjectCreate(String name, String path, String sharePath) {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Docker.Project", "version", "1",
            "method", "create", "name", name,
            "path", path, "share_path", sharePath, "_sid", sid
        ));
    }

    /** 构建并启动 */
    public String dockerProjectBuild(String projectId) {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Docker.Project", "version", "1",
            "method", "build", "id", projectId, "_sid", sid
        ));
    }

    /** 容器级停止（仅兜底：项目级 stop 未生效时才用）*/
    public String dockerContainerStop(String name) {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Docker.Container", "version", "1",
            "method", "stop", "name", name, "_sid", sid
        ));
    }

    // ---------------- 用户管理（六章）----------------
    public String userList() {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Core.User", "version", "1",
            "method", "list", "_sid", sid
        ));
    }

    // ---------------- 共享权限（五章）----------------
    public String sharePermissionList(String name) {
        // 参数超过 Map.of 的 10 对上限，改用 HashMap
        Map<String, String> params = new java.util.HashMap<>();
        params.put("api", "SYNO.Core.Share.Permission");
        params.put("version", "1");
        params.put("method", "list");
        params.put("name", name);
        params.put("offset", "0");
        params.put("limit", "50");
        params.put("action", "enum");
        params.put("is_unite_permission", "false");
        params.put("with_inherit", "false");
        params.put("user_group_type", "local_user");
        params.put("_sid", sid);
        return get("/webapi/entry.cgi", params);
    }

    // ---------------- 系统与存储（七章）----------------
    /** CPU/内存/磁盘利用率（data.cpu / data.memory / data.disk）*/
    public String systemUtilization() {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Core.System.Utilization", "version", "1",
            "method", "get", "_sid", sid
        ));
    }

    public String systemHealth() {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Core.System.SystemHealth", "version", "1",
            "method", "get", "_sid", sid
        ));
    }

    /** 磁盘列表（id 如 sda/sdb）*/
    public String storageDiskList() {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Core.Storage.Disk", "version", "1",
            "method", "list", "_sid", sid
        ));
    }

    /** SMART 健康（version 固定 1）*/
    public String smartHealth() {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Storage.CGI.Smart", "version", "1",
            "method", "get_health_info", "_sid", sid
        ));
    }

    // ---------------- JSON 工具（无外部依赖）----------------
    /** 提取 "key":"value" 形式的字符串值；未找到返回空串。 */
    public static String extractJsonString(String json, String key) {
        if (json == null || json.isEmpty()) return "";
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? "" : json.substring(start, end);
    }

    /** 提取 "key":<value> 形式的标量（数字/布尔/无引号字符串），到逗号或 } 结束。 */
    public static String extractJsonValue(String json, String key) {
        if (json == null || json.isEmpty()) return "";
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return "";
        return extractValueFrom(json, start + search.length() - 1);
    }
}
