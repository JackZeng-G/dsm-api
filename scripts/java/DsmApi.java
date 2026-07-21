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
 */
public class DsmApi {

    public static final String VERSION = "1.0.0";

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

    // ---- SSL 跳过 ----
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

    // ---- HTTP 方法 ----
    private String get(String path, Map<String, String> params) throws IOException {
        String query = params.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
        URL url = new URL(base + path + "?" + query);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        return readResponse(conn);
    }

    private String post(String path, Map<String, String> data) throws IOException {
        URL url = new URL(base + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        String body = data.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return readResponse(conn);
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        InputStream is = conn.getResponseCode() < 400
            ? conn.getInputStream()
            : conn.getErrorStream();
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return br.lines().collect(Collectors.joining("\n"));
        }
    }

    // ---- 认证 ----
    public String connectivityTest() throws IOException {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.API.Info", "version", "1",
            "method", "query", "query", "SYNO.API.Auth"
        ));
    }

    public String login(String session) throws IOException {
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

    public String logout() throws IOException {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.API.Auth", "version", "6",
            "method", "logout", "_sid", sid
        ));
    }

    // ---- API 发现 ----
    public String apiQuery(String api) throws IOException {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.API.Info", "version", "1",
            "method", "query", "query", api
        ));
    }

    // ---- 文件操作 ----
    public String fsInfo() throws IOException {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.FileStation.Info", "version", "2",
            "method", "get", "_sid", sid
        ));
    }

    public String fsListShares() throws IOException {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.FileStation.List", "version", "2",
            "method", "list_share", "_sid", sid
        ));
    }

    public String fsList(String folderPath) throws IOException {
        return fsList(folderPath, "");
    }

    public String fsList(String folderPath, String additional) throws IOException {
        Map<String, String> params = new java.util.HashMap<>(Map.of(
            "api", "SYNO.FileStation.List", "version", "2",
            "method", "list", "folder_path", folderPath, "_sid", sid
        ));
        if (!additional.isEmpty()) params.put("additional", additional);
        return get("/webapi/entry.cgi", params);
    }

    public String fsCreateFolder(String folderPath, String name) throws IOException {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.FileStation.CreateFolder", "version", "2",
            "method", "create", "folder_path", folderPath,
            "name", name, "force_parent", "true", "_sid", sid
        ));
    }

    public String fsRename(String path, String newName) throws IOException {
        return post("/webapi/entry.cgi", Map.of(
            "api", "SYNO.FileStation.Rename", "version", "2",
            "method", "rename", "path", path, "name", newName, "_sid", sid
        ));
    }

    public String fsCopyMoveStart(String path, String destPath,
                                   boolean removeSrc, boolean overwrite) throws IOException {
        return post("/webapi/entry.cgi", Map.of(
            "api", "SYNO.FileStation.CopyMove", "version", "3",
            "method", "start", "path", path,
            "dest_folder_path", destPath,
            "remove_src", String.valueOf(removeSrc),
            "overwrite", String.valueOf(overwrite),
            "_sid", sid
        ));
    }

    public String fsCopyMoveStatus(String taskid) throws IOException {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.FileStation.CopyMove", "version", "3",
            "method", "status", "taskid", taskid, "_sid", sid
        ));
    }

    // ---- Docker 管理 ----
    public String dockerProjectList() throws IOException {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Docker.Project", "version", "1",
            "method", "list", "_sid", sid
        ));
    }

    public String dockerProjectCreate(String name, String path, String sharePath)
            throws IOException {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Docker.Project", "version", "1",
            "method", "create", "name", name,
            "path", path, "share_path", sharePath, "_sid", sid
        ));
    }

    public String dockerContainerStop(String name) throws IOException {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Docker.Container", "version", "1",
            "method", "stop", "name", name, "_sid", sid
        ));
    }

    // ---- 用户管理 ----
    public String userList() throws IOException {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Core.User", "version", "1",
            "method", "list", "_sid", sid
        ));
    }

    // ---- 共享权限 ----
    public String sharePermissionList(String name) throws IOException {
        return get("/webapi/entry.cgi", Map.of(
            "api", "SYNO.Core.Share.Permission", "version", "1",
            "method", "list", "name", name,
            "offset", "0", "limit", "50", "action", "enum",
            "is_unite_permission", "false", "with_inherit", "false",
            "user_group_type", "local_user", "_sid", sid
        ));
    }

    // ---- 工具方法 ----
    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? "" : json.substring(start, end);
    }
}
