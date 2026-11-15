package cn.tealc.asmr.source.openlist;

import cn.tealc.asmr.model.OpenListAccess;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.source.openlist.model.OpenListFile;
import cn.tealc.asmr.source.openlist.model.OpenListListResponse;
import cn.tealc.asmr.source.openlist.model.OpenListRequest;
import cn.tealc.asmr.source.openlist.model.OpenListResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * OpenList/OpenList HTTP 客户端。
 * <p>
 * 封装 OpenList 全部 API 调用，负责：
 * <ul>
 *   <li>使用存储源提供的 OpenList 令牌完成认证</li>
 *   <li>目录列举、文件信息获取、创建目录、重命名、移动、删除、上传文本文件</li>
 *   <li>代理支持（读取 {@code Config.setting} 的代理配置）</li>
 * </ul>
 *
 * @author Leck
 */
public class OpenListClient {

    private static final Logger log = LoggerFactory.getLogger(OpenListClient.class);

    /** 全局共享 ObjectMapper，容忍 API 新增字段 */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 读取类请求超时 */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** 写操作超时：删除大目录、批量移动在网盘侧可能耗时较长 */
    private static final Duration MUTATION_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;

    private final String baseUrl;
    private final String token;

    /**
     * 构造绑定到单个存储源的客户端。
     */
    public OpenListClient(OpenListAccess access) {
        if (access == null || access.baseUrl() == null || access.baseUrl().isBlank()
                || access.token() == null || access.token().isBlank()) {
            throw new IllegalArgumentException("OpenList 地址或令牌不能为空");
        }
        this.baseUrl = access.baseUrl().replaceAll("/+$", "");
        this.token = access.token();
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5));
        if (Config.setting != null && Config.setting.proxyModel.get()) {
            String host = Config.setting.proxyHost.get();
            String port = Config.setting.proxyPort.get();
            if (host != null && port != null && !host.isBlank() && !port.isBlank()) {
                builder.proxy(ProxySelector.of(new InetSocketAddress(host, Integer.parseInt(port))));
            }
        }
        this.httpClient = builder.build();
    }

    // ===================== 文件操作 API =====================

    /**
     * 列出目录下所有文件和子目录，强制刷新服务端缓存。
     *
     * @param path 目录路径
     * @return 文件列表，空目录返回空列表
     * @throws OpenListException API 调用失败
     */
    public List<OpenListFile> listFiles(String path) {
        return listFiles(path, true);
    }

    /**
     * 列出目录下所有文件和子目录。
     * <p>
     * 调用 POST /api/fs/list。{@code refresh} 为 false 时允许 OpenList 使用自身
     * 目录缓存，避免浏览时每次都回源网盘；需要最新数据时传 true。
     *
     * @param path    目录路径
     * @param refresh 是否强制刷新服务端缓存
     * @return 文件列表，空目录返回空列表
     * @throws OpenListException API 调用失败
     */
    public List<OpenListFile> listFiles(String path, boolean refresh) {
        OpenListResponse<OpenListListResponse> response = post(
                "/api/fs/list",
                OpenListRequest.of(path, refresh),
                OpenListListResponse.class);
        OpenListListResponse data = response.getData();
        if (data == null || data.getContent() == null) {
            return List.of();
        }
        return data.getContent();
    }

    /**
     * 获取单个文件或目录信息（含签名直链 raw_url）。
     * <p>
     * 调用 POST /api/fs/get，强制刷新。
     *
     * @param path 文件路径
     * @return 文件信息，不存在时返回 null
     * @throws OpenListException 网络错误等
     */
    public OpenListFile getFileInfo(String path) {
        OpenListResponse<OpenListFile> response = post(
                "/api/fs/get",
                OpenListRequest.of(path, true),
                OpenListFile.class);
        return response.getData();
    }

    /**
     * 创建目录。
     * <p>
     * 调用 POST /api/fs/mkdir。
     *
     * @param path 目录路径
     * @return 成功返回 true
     * @throws OpenListException API 调用失败
     */
    public boolean mkdir(String path) {
        OpenListResponse<Void> response = post(
                "/api/fs/mkdir",
                OpenListRequest.of(path),
                Void.class);
        return response.isSuccess();
    }

    /**
     * 重命名文件或目录。
     * <p>
     * 调用 POST /api/fs/rename。
     *
     * @param path    原路径（完整路径）
     * @param newName 新名称（不含目录部分）
     * @return 成功返回 true
     * @throws OpenListException API 调用失败
     */
    public boolean rename(String path, String newName) {
        OpenListResponse<Void> response = postMutation(
                "/api/fs/rename",
                OpenListRequest.rename(path, newName),
                Void.class);
        return response.isSuccess();
    }

    /**
     * 移动文件到目标目录。
     * <p>
     * 调用 POST /api/fs/move。
     *
     * @param srcDir 源目录
     * @param dstDir 目标目录
     * @param names  要移动的文件名列表
     * @return 成功返回 true
     * @throws OpenListException API 调用失败
     */
    public boolean move(String srcDir, String dstDir, List<String> names) {
        OpenListResponse<Void> response = postMutation(
                "/api/fs/move",
                OpenListRequest.move(srcDir, dstDir, names),
                Void.class);
        return response.isSuccess();
    }

    /**
     * 删除文件或目录。
     * <p>
     * 调用 POST /api/fs/remove。
     *
     * @param dir   文件所在目录
     * @param names 要删除的文件名列表
     * @return 成功返回 true
     * @throws OpenListException API 调用失败
     */
    public boolean remove(String dir, List<String> names) {
        OpenListResponse<Void> response = postMutation(
                "/api/fs/remove",
                OpenListRequest.remove(dir, names),
                Void.class);
        return response.isSuccess();
    }

    /**
     * 上传文本文件（流式 PUT）。
     * <p>
     * 调用 PUT /api/fs/put，将文本内容以 UTF-8 上传到指定路径。
     *
     * @param path    目标完整路径
     * @param content 文本内容
     * @return 成功返回 true
     * @throws OpenListException 上传失败
     */
    public boolean putTextFile(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8);
        try {
            HttpRequest request = baseRequest("/api/fs/put", MUTATION_TIMEOUT)
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Length", String.valueOf(bytes.length))
                    .header("File-Path", encodedPath)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();
            HttpResponse<String> response = sendWithRetry(request);
            OpenListResponse<Void> result = MAPPER.readValue(
                    response.body(),
                    new TypeReference<>() {});
            return result.isSuccess();
        } catch (OpenListException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenListException("上传文件失败: " + path, e);
        }
    }

    /**
     * 通过 /d/ 代理路径直接下载文件内容（文本）。
     * <p>
     * 用于读取歌词等小文本文件，无需经过 /api/fs/get。
     *
     * @param path 文件完整路径
     * @return 文件内容，文件不存在或下载失败返回 null
     */
    /**
     * 通过 /api/fs/get 获取文件的签名直链（raw_url），再直接下载文件内容。
     * <p>
     * 不走 /d/ 代理路径，避免 /d/ 路径鉴权与 API 路径不一致导致 401。
     *
     * @param path 文件完整路径
     * @return 文件内容，文件不存在或下载失败返回 null
     */
    public String downloadText(String path) {
        try {
            // 先获取文件信息拿 raw_url
            OpenListFile file = getFileInfo(path);
            if (file == null) return null;

            String downloadUrl = file.getRawUrl();
            if (downloadUrl == null || downloadUrl.isBlank()) {
                // fallback 到 /d/ 路径
                downloadUrl = baseUrl() + "/d" + normalizePath(path);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                return response.body();
            }
            log.warn("下载文件失败: {}，HTTP {}", path, response.statusCode());
            return null;
        } catch (Exception e) {
            log.error("下载文件异常: {}", path, e);
            return null;
        }
    }

    /**
     * 下载二进制文件内容（如图片）。
     * <p>
     * 与 {@link #downloadText} 逻辑相同（同一个 httpClient、代理、raw_url 优先 + /d/ fallback），
     * 仅返回类型不同，使用 {@link HttpResponse.BodyHandlers#ofByteArray()}。
     *
     * @param path 文件完整路径
     * @return 文件二进制内容，文件不存在或下载失败返回 null
     */
    public byte[] downloadBinary(String path) {
        try {
            OpenListFile file = getFileInfo(path);
            if (file == null) return null;

            String downloadUrl = file.getRawUrl();
            if (downloadUrl == null || downloadUrl.isBlank()) {
                downloadUrl = baseUrl() + "/d" + normalizePath(path);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return response.body();
            }
            log.warn("下载二进制文件失败: {}，HTTP {}", path, response.statusCode());
            return null;
        } catch (Exception e) {
            log.error("下载二进制文件异常: {}", path, e);
            return null;
        }
    }

    /**
     * 获取文件签名直链（raw_url），仅供 Java 进程内请求使用（如直链可用性测试）。
     * <p>
     * 调用 /api/fs/get 取 raw_url，若为空则 fallback 到 /d/ 代理路径。
     * <p>
     * 注意：raw_url 是按申请方（本客户端 Java HttpClient）的 User-Agent 签发的，
     * VLC 等播放器会用自己的 UA，部分网盘会返回 403。播放地址请使用
     * {@link #getProxyPlayUrl(String)}。
     *
     * @param path 文件完整路径
     * @return 直链 URL
     * @throws OpenListException 获取失败
     */
    public String getPlayUrl(String path) {
        OpenListFile file = getFileInfo(path);
        if (file != null && file.getRawUrl() != null && !file.getRawUrl().isBlank()) {
            return file.getRawUrl();
        }
        return baseUrl() + "/d" + normalizePath(path);
    }

    /**
     * 获取播放器可用的代理播放地址（音频与视频通用，内置/外部播放器都适用）。
     * <p>
     * <b>为什么播放不能直接使用 {@link #getPlayUrl}</b>：
     * <ul>
     *   <li>网盘直链（如 115 的 {@code raw_url}）是<b>按申请方 User-Agent 签发</b>的。
     *       OpenList 调用 {@code DownloadWithUA(pickCode, userAgent)} 时会把请求方的 UA
     *       一并送给网盘参与签名，随后沿用这条 UA 拉流。播放器发出的是自己的 UA，
     *       与签名不匹配，网盘直接返回 {@code 403 invalid signature}。</li>
     *   <li>部分网盘还要求请求携带账号 Cookie。OpenList 在代理转发时会补上自身 Cookie，
     *       而播放器不可能持有该 Cookie，单独访问同样被拒。</li>
     * </ul>
     * 因此播放器只能访问 OpenList 自身：{@code /d/} 会按实际请求方的 UA 重新签发直链
     * 并 302 跳转，或在存储驱动要求代理时（115 等驱动的 MustProxy）由服务端拉流后转发。
     * <p>
     * 签名参数 {@code sign} 取自 {@code /api/fs/get} 返回的 {@code sign} 字段，
     * 由 OpenList 校验，与网盘无关，也不会因为网盘侧的短时效签名失效而失效。
     * <p>
     * 路径必须逐段 URL 编码：文件名常含中文、空格，未编码会导致 OpenList 端路径解析失败。
     * 其中 {@link URLEncoder} 将空格编码为 {@code +}，而路径中 {@code +} 会被当作字面加号，
     * 因此需要替换为 {@code %20}。
     *
     * @param path 文件完整路径（如 {@code /115/视频/a.mp4}）
     * @return 指向 OpenList 的代理播放地址；获取文件信息失败时返回 null
     * @throws OpenListException 网络错误或 API 调用失败
     */
    public String getProxyPlayUrl(String path) {
        OpenListFile file = getFileInfo(path);
        if (file == null) {
            return null;
        }
        String sign = file.getSign();
        String proxyUrl = baseUrl() + "/d" + encodePathForProxy(path);
        if (sign == null || sign.isBlank()) {
            // 未启用签名的 OpenList 配置下可直接访问，保持与 Web 界面一致
            return proxyUrl;
        }
        return proxyUrl + "?sign=" + sign;
    }

    /**
     * 按路径段做 URL 编码，供 /d/ 代理路径使用。
     * <p>
     * 逐段编码可保留层级分隔符 {@code /}，同时把每段中的中文、空格、特殊字符正确转义。
     *
     * @param path 原始路径
     * @return 编码后的路径，以 {@code /} 开头
     */
    private static String encodePathForProxy(String path) {
        String normalized = normalizePathStatic(path);
        StringBuilder builder = new StringBuilder();
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            // URLEncoder 面向表单编码，会把空格变成 +；路径中必须用 %20
            String encoded = URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
            builder.append('/').append(encoded);
        }
        return builder.isEmpty() ? "/" : builder.toString();
    }

    /**
     * {@link #normalizePath(String)} 的静态版本，供静态工具方法复用。
     *
     * @param path 原始路径
     * @return 以 {@code /} 开头且不以 {@code /} 结尾的路径
     */
    private static String normalizePathStatic(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    // ===================== 工具方法 =====================

    /**
     * 构造 OpenList base URL。
     *
     * @return {@code http://host:port}
     */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * 规范化路径，确保以 {@code /} 开头且不以 {@code /} 结尾。
     */
    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String p = path.startsWith("/") ? path : "/" + path;
        if (p.endsWith("/") && p.length() > 1) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /**
     * 构造基础请求 builder（含 Authorization、超时）。
     */
    private HttpRequest.Builder baseRequest(String apiPath, Duration timeout) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + apiPath))
                .timeout(timeout);
    }

    /**
     * 发送 POST JSON 请求。
     *
     * @param apiPath     API 路径（如 /api/fs/list）
     * @param requestBody 请求体对象（会被序列化为 JSON）
     * @param dataType    响应中 data 字段的目标类型
     * @return 解析后的响应
     * @throws OpenListException 网络/API 错误
     */
    private <T> OpenListResponse<T> post(String apiPath, Object requestBody, Class<T> dataType) {
        return post(apiPath, requestBody, dataType, TIMEOUT);
    }

    /**
     * 发送写操作 POST 请求，使用更长的超时。
     * <p>
     * 删除大目录、批量移动在网盘侧可能耗时超过读取类请求的 10 秒。
     * 写操作不做自动重试，避免重复删除/移动。
     */
    private <T> OpenListResponse<T> postMutation(String apiPath, Object requestBody, Class<T> dataType) {
        return post(apiPath, requestBody, dataType, MUTATION_TIMEOUT);
    }

    private <T> OpenListResponse<T> post(String apiPath, Object requestBody, Class<T> dataType,
                                         Duration timeout) {
        try {
            String json = MAPPER.writeValueAsString(requestBody);
            HttpResponse<String> response = sendPost(apiPath, json, timeout);
            return parseResponse(response, dataType);
        } catch (OpenListException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenListException("OpenList API 调用失败: " + apiPath, e);
        }
    }

    /**
     * 发送 POST JSON 请求（带当前 token）。
     */
    private HttpResponse<String> sendPost(String apiPath, String json, Duration timeout) throws Exception {
        HttpRequest request = baseRequest(apiPath, timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", authorizationValue())
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 发送带令牌的请求。
     */
    private HttpResponse<String> sendWithRetry(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response;
    }

    /**
     * 解析 API 响应，检查 code。
     * <p>
     * 只有 401 视为令牌失效；403 通常是账号对具体文件无权限或驱动只读，
     * 不应把整个存储源标记为不可用。目标不存在（404 / not found）单独标记，
     * 供 {@code exists} 区分“不存在”和网络错误。
     */
    private <T> OpenListResponse<T> parseResponse(HttpResponse<String> response, Class<T> dataType) throws Exception {
        if (response.statusCode() != 200) {
            boolean authenticationFailure = response.statusCode() == 401;
            boolean notFound = response.statusCode() == 404;
            throw new OpenListException(
                    "OpenList API HTTP " + response.statusCode() + ": " + response.body(),
                    authenticationFailure, notFound);
        }
        OpenListResponse<T> result = MAPPER.readValue(response.body(),
                MAPPER.getTypeFactory().constructParametricType(OpenListResponse.class, dataType));
        if (result == null) {
            throw new OpenListException("OpenList 响应解析失败");
        }
        if (!result.isSuccess()) {
            throw new OpenListException("OpenList API 错误: " + result.getMessage(),
                    result.isUnauthorized(), isNotFoundMessage(result.getMessage()));
        }
        return result;
    }

    private static boolean isNotFoundMessage(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("object not found") || normalized.contains("file not found")
                || normalized.contains("no such file") || normalized.contains("not exist")
                || normalized.contains("does not exist");
    }

    private String authorizationValue() {
        return token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
                ? token.substring("Bearer ".length()).trim()
                : token;
    }

    /**
     * 从文件名提取扩展名（不含点，小写）。
     *
     * @param filename 文件名
     * @return 扩展名，无扩展名返回空字符串
     */
    public static String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf(".");
        return dot == -1 ? "" : filename.substring(dot + 1).toLowerCase();
    }
}
