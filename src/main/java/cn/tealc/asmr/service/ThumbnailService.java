package cn.tealc.asmr.service;

import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.source.StorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 远程图片缩略图服务。
 * <p>
 * 只使用来源提供的缩略图地址（如 OpenList 的 {@code thumb}），下载结果经
 * {@link CacheService} 落到本地缓存；来源没有缩略图或下载失败时返回 null，
 * 不回退到原图。返回字节而非 JavaFX Image，由调用方决定如何展示。
 *
 * @author Leck
 */
public class ThumbnailService {

    private static final Logger LOG = LoggerFactory.getLogger(ThumbnailService.class);

    /** 缩略图在缓存中与同路径原图区分开，避免共用缓存键互相覆盖 */
    private static final String CACHE_PATH_SUFFIX = "#thumb";

    private static final String CACHE_EXTENSION = "thumb";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0";

    @Inject
    private CacheService cacheService;

    private HttpClient httpClient;

    /**
     * 加载文件缩略图，优先使用列表响应中已有的地址，避免额外的文件信息请求。
     *
     * @param work 所属作品（用于确定存储源）
     * @param file 文件条目
     * @return 缩略图字节；不支持或不可用时返回 null
     */
    public byte[] load(AsmrWork work, AsmrFile file) {
        if (file == null) {
            return null;
        }
        return load(work, file.getFilepath(), file.getThumbnailUrl());
    }

    /**
     * 按路径加载缩略图，没有现成地址时会向来源查询。
     *
     * @param work     所属作品（用于确定存储源）
     * @param filePath 文件路径
     * @return 缩略图字节；不支持或不可用时返回 null
     */
    public byte[] load(AsmrWork work, String filePath) {
        return load(work, filePath, null);
    }

    private byte[] load(AsmrWork work, String filePath, String knownThumbUrl) {
        if (work == null || filePath == null || filePath.isBlank()
                || work.getStorageSourceId() == null
                || work.getStorageType() == null
                || work.getStorageType() != StorageType.OPENLIST.getCode()) {
            return null;
        }
        String thumbUrl = knownThumbUrl;
        if (thumbUrl == null || thumbUrl.isBlank()) {
            thumbUrl = resolveThumbUrl(work, filePath);
        }
        if (thumbUrl == null || thumbUrl.isBlank()) {
            return null;
        }
        String url = thumbUrl;
        return cacheService.readBinary(
                "openlist:" + work.getStorageSourceId(),
                filePath + CACHE_PATH_SUFFIX,
                CACHE_EXTENSION,
                () -> downloadWithRetry(work, filePath, url));
    }

    /**
     * 缩略图地址带签名时效，首次下载失败时重新解析一次再试。
     */
    private byte[] downloadWithRetry(AsmrWork work, String filePath, String staleUrl) {
        byte[] data = download(staleUrl);
        if (data != null) {
            return data;
        }
        String freshUrl = resolveThumbUrl(work, filePath);
        if (freshUrl == null || freshUrl.isBlank() || freshUrl.equals(staleUrl)) {
            return null;
        }
        return download(freshUrl);
    }

    private static String resolveThumbUrl(AsmrWork work, String filePath) {
        try {
            MediaSource source = MediaSourceFactory.forWork(work);
            return source.resolveThumbnailUrl(filePath);
        } catch (RuntimeException exception) {
            LOG.warn("解析缩略图地址失败: {}", filePath, exception);
            return null;
        }
    }

    private byte[] download(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            LOG.warn("缩略图地址协议不受支持: {}", url);
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient().send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return response.body();
            }
            LOG.warn("下载缩略图失败: HTTP {}，地址 {}", response.statusCode(), url);
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.warn("下载缩略图被中断: {}", url);
            return null;
        } catch (IOException | IllegalArgumentException exception) {
            LOG.warn("下载缩略图失败: {}", url, exception);
            return null;
        }
    }

    private synchronized HttpClient httpClient() {
        if (httpClient == null) {
            HttpClient.Builder builder = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5));
            if (Config.setting != null && Config.setting.proxyModel.get()) {
                String host = Config.setting.proxyHost.get();
                String port = Config.setting.proxyPort.get();
                if (host != null && !host.isBlank() && port != null && !port.isBlank()) {
                    builder.proxy(ProxySelector.of(new InetSocketAddress(host, Integer.parseInt(port))));
                }
            }
            httpClient = builder.build();
        }
        return httpClient;
    }
}
