package cn.tealc.asmr.source.openlist;

import cn.tealc.asmr.model.SupportedAudioFormat;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.StorageSourceStatus;
import cn.tealc.asmr.service.StorageSourceService;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.StorageType;
import cn.tealc.asmr.model.OpenListAccess;
import cn.tealc.asmr.source.openlist.model.OpenListFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * OpenList/OpenList 网盘文件来源实现。
 * <p>
 * 实现 {@link MediaSource} 接口，通过 {@link OpenListClient} 调用 OpenList API 完成文件读写操作。
 * 每个实例绑定一个存储源的连接配置和令牌。
 * <p>
 * 路径约定：{@code AsmrFile.filepath} 存储的是 OpenList 上的完整路径（如 {@code /RJ123/audio/01.mp3}），
 * 所有方法接收的 path 参数也使用此格式。
 *
 * @author Leck
 */
public class OpenListMediaSource implements MediaSource {

    private static final Logger log = LoggerFactory.getLogger(OpenListMediaSource.class);

    /** 缩略图地址有效期；地址本身带签名时效，过期后需重新获取 */
    private static final long THUMB_URL_TTL_MS = 10L * 60 * 1000;

    /** 无缩略图时的复查间隔，避免重复请求没有 thumb 的文件 */
    private static final long THUMB_MISS_TTL_MS = 60L * 1000;

    /**
     * 缩略图地址内存缓存。按存储源 + 路径共享：工厂每次都会创建新的来源实例，
     * 缓存必须是静态的才能跨实例复用。
     */
    private static final ConcurrentHashMap<String, ThumbUrl> THUMB_URLS = new ConcurrentHashMap<>();

    private final OpenListClient client;
    private final Integer storageSourceId;
    /** 存储源配置的目标根路径，用于阻止删除/移出该范围 */
    private final String targetRoot;

    public OpenListMediaSource(OpenListAccess access) {
        this(null, access, null);
    }

    public OpenListMediaSource(Integer storageSourceId, OpenListAccess access) {
        this(storageSourceId, access, null);
    }

    public OpenListMediaSource(Integer storageSourceId, OpenListAccess access, String targetRoot) {
        this.storageSourceId = storageSourceId;
        this.targetRoot = targetRoot;
        this.client = new OpenListClient(access);
    }

    // ===================== 读取 =====================

    @Override
    public List<AsmrFile> listFiles(String dirPath) {
        return listFiles(dirPath, true);
    }

    @Override
    public List<AsmrFile> listFiles(String dirPath, boolean forceRefresh) {
        try {
            List<OpenListFile> files = client.listFiles(dirPath, forceRefresh);
            List<AsmrFile> result = new ArrayList<>(files.size());
            for (OpenListFile file : files) {
                result.add(toAsmrFile(file, dirPath));
            }
            return result;
        } catch (OpenListException e) {
            throw recordFailure(e, "列目录失败: " + dirPath);
        }
    }

    @Override
    public List<AsmrFile> listAudioFiles(String dirPath) {
        return listFiles(dirPath).stream()
                .filter(f -> !f.isDir() && SupportedAudioFormat.contains(f.getType()))
                .toList();
    }

    @Override
    public List<AsmrFile> listFilesRecursive(String dirPath, String extension) {
        List<AsmrFile> result = new ArrayList<>();
        String lowerExt = extension.toLowerCase();
        collectRecursive(dirPath, lowerExt, result);
        return result;
    }

    @Override
    public List<AsmrFile> listFilesRecursive(String dirPath, String[] extensions) {
        return listFilesRecursive(dirPath, extensions, null);
    }

    @Override
    public List<AsmrFile> listFilesRecursive(String dirPath, String[] extensions, String skipDir) {
        Set<String> extSet = Arrays.stream(extensions)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        List<AsmrFile> result = new ArrayList<>();
        collectRecursiveMulti(dirPath, extSet, skipDir, result);
        return result;
    }

    @Override
    public String readTextFile(String filePath) {
        try {
            return client.downloadText(filePath);
        } catch (OpenListException e) {
            throw recordFailure(e, "读取文本文件失败: " + filePath);
        }
    }

    @Override
    public byte[] readBinaryFile(String filePath) {
        try {
            return client.downloadBinary(filePath);
        } catch (OpenListException e) {
            throw recordFailure(e, "读取文件失败: " + filePath);
        }
    }

    @Override
    public boolean exists(String path) {
        try {
            return client.getFileInfo(path) != null;
        } catch (OpenListException e) {
            if (e.isNotFound()) {
                return false;
            }
            throw recordFailure(e, "读取文件信息失败: " + path);
        }
    }

    @Override
    public boolean isDirectory(String path) {
        try {
            OpenListFile info = client.getFileInfo(path);
            return info != null && info.isDir();
        } catch (OpenListException e) {
            if (e.isNotFound()) {
                return false;
            }
            throw recordFailure(e, "读取目录信息失败: " + path);
        }
    }

    /**
     * 解析可播放地址。
     * <p>
     * 音频与视频统一走 OpenList 的 /d/ 代理路径。网盘直链（raw_url）是按
     * 获取直链时的 User-Agent 签发的，而 VLC（内置与外部播放器）会用自身 UA
     * 发起请求，UA 不匹配时部分网盘（如 115）直接返回 403；/d/ 会按实际请求方
     * 的 UA 重新签发或代理取流，因此播放器只与 OpenList 通信即可。
     *
     * @param filePath 文件完整路径
     * @return 可播放地址，失败返回 null
     */
    @Override
    public String resolvePlayUrl(String filePath) {
        try {
            return client.getProxyPlayUrl(filePath);
        } catch (OpenListException e) {
            recordFailure(e, "获取播放地址失败: " + filePath);
            log.error("获取播放地址失败: {}", filePath, e);
            return null;
        }
    }

    @Override
    public String resolveThumbnailUrl(String filePath) {
        String normalized = normalizeRemotePath(filePath);
        String cacheKey = (storageSourceId == null ? "" : storageSourceId) + "|" + normalized;
        long now = System.currentTimeMillis();
        if (THUMB_URLS.size() > 4096) {
            THUMB_URLS.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        }
        ThumbUrl cached = THUMB_URLS.get(cacheKey);
        if (cached != null && cached.expiresAt() > now) {
            return cached.url();
        }
        try {
            String thumb = thumbOf(client.getFileInfo(normalized));
            if (thumb == null) {
                thumb = thumbFromParentListing(normalized);
            }
            THUMB_URLS.put(cacheKey, new ThumbUrl(thumb,
                    now + (thumb == null ? THUMB_MISS_TTL_MS : THUMB_URL_TTL_MS)));
            return thumb;
        } catch (OpenListException e) {
            if (e.isNotFound()) {
                THUMB_URLS.put(cacheKey, new ThumbUrl(null, now + THUMB_MISS_TTL_MS));
                return null;
            }
            throw recordFailure(e, "获取缩略图地址失败: " + filePath);
        }
    }

    private static String thumbOf(OpenListFile file) {
        if (file == null || file.getThumb() == null || file.getThumb().isBlank()) {
            return null;
        }
        return file.getThumb();
    }

    /**
     * 部分服务端的 /api/fs/get 不返回 thumb，退回列目录按文件名匹配。
     */
    private String thumbFromParentListing(String path) throws OpenListException {
        String name = fileName(path);
        for (OpenListFile file : client.listFiles(parentPath(path), false)) {
            if (name.equals(file.getName())) {
                return thumbOf(file);
            }
        }
        return null;
    }

    private record ThumbUrl(String url, long expiresAt) {
    }

    // ===================== 写入 =====================

    @Override
    public boolean delete(String path) {
        String normalized = normalizeRemotePath(path);
        requireMutablePath(normalized, "删除");
        String dir = parentPath(normalized);
        String name = fileName(normalized);
        try {
            return client.remove(dir, List.of(name));
        } catch (OpenListException e) {
            throw recordFailure(e, "删除文件失败: " + path);
        }
    }

    @Override
    public boolean rename(String path, String newName) {
        String normalized = normalizeRemotePath(path);
        requireMutablePath(normalized, "重命名");
        String name = validateName(newName);
        if (name.equals(fileName(normalized))) {
            return true;
        }
        try {
            return client.rename(normalized, name);
        } catch (OpenListException e) {
            throw recordFailure(e, "重命名文件失败: " + path);
        }
    }

    @Override
    public boolean move(String srcPath, String destDirPath) {
        String normalizedSource = normalizeRemotePath(srcPath);
        String normalizedDestination = normalizeRemotePath(destDirPath);
        requireMutablePath(normalizedSource, "移动");
        requireInsideTargetRoot(normalizedDestination, "目标目录超出存储源范围");
        if (normalizedDestination.equals(normalizedSource)
                || normalizedDestination.startsWith(normalizedSource + "/")) {
            throw new IllegalArgumentException("不能将文件移动到自身或其子目录");
        }
        String srcDir = parentPath(normalizedSource);
        if (srcDir.equals(normalizedDestination)) {
            return true;
        }
        String name = fileName(normalizedSource);
        try {
            return client.move(srcDir, normalizedDestination, List.of(name));
        } catch (OpenListException e) {
            throw recordFailure(e, "移动文件失败: " + srcPath);
        }
    }

    @Override
    public boolean createDirectory(String path) {
        try {
            return client.mkdir(path);
        } catch (OpenListException e) {
            recordFailure(e, "创建目录失败: " + path);
            log.error("创建目录失败: {}", path, e);
            return false;
        }
    }

    @Override
    public boolean writeTextFile(String filePath, String content) {
        try {
            return client.putTextFile(filePath, content);
        } catch (OpenListException e) {
            recordFailure(e, "写入文本文件失败: " + filePath);
            log.error("写入文本文件失败: {}", filePath, e);
            return false;
        }
    }

    @Override
    public boolean openInSystem(String path) {
        // 远程来源不支持系统默认程序打开
        return false;
    }

    // ===================== 来源标识 =====================

    @Override
    public StorageType getType() {
        return StorageType.OPENLIST;
    }

    private OpenListException recordFailure(OpenListException exception, String operation) {
        if (storageSourceId != null && exception.isAuthenticationFailure()) {
            DI.get(StorageSourceService.class).updateStatus(storageSourceId, StorageSourceStatus.AUTH_EXPIRED,
                    "OpenList 令牌已失效，请重新填写");
        }
        log.error(operation, exception);
        return exception;
    }

    // ===================== 内部工具 =====================

    /**
     * 规范化远程路径：统一正斜杠、补前导斜杠、去掉尾部斜杠。
     */
    private static String normalizeRemotePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 取父目录。根级条目（如 {@code /foo}）的父目录是 {@code /}。
     */
    private static String parentPath(String path) {
        int index = path.lastIndexOf('/');
        return index <= 0 ? "/" : path.substring(0, index);
    }

    /**
     * 取最后一段名称。
     */
    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * 校验并返回去掉首尾空白后的新名称。
     */
    private static String validateName(String newName) {
        String name = newName == null ? "" : newName.trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("文件名无效");
        }
        return name;
    }

    /**
     * 拒绝删除/重命名/移动存储根目录或目标根目录本身及其上级。
     */
    private void requireMutablePath(String normalizedPath, String operation) {
        if ("/".equals(normalizedPath)) {
            throw new IllegalArgumentException("不能" + operation + "存储根目录");
        }
        if (targetRoot == null || targetRoot.isBlank()) {
            return;
        }
        String root = normalizeRemotePath(targetRoot);
        if (normalizedPath.equals(root) || root.startsWith(normalizedPath + "/")) {
            throw new IllegalArgumentException("不能" + operation + "存储源目标路径或其上级目录");
        }
    }

    /**
     * 拒绝把目标路径放到存储源目标路径之外。
     */
    private void requireInsideTargetRoot(String normalizedPath, String message) {
        if (targetRoot == null || targetRoot.isBlank()) {
            return;
        }
        String root = normalizeRemotePath(targetRoot);
        if (!root.equals("/") && !normalizedPath.equals(root)
                && !normalizedPath.startsWith(root + "/")) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 递归遍历目录，收集指定扩展名的文件。
     * OpenList 无递归 API，需手动递归调用 listFiles。
     */
    private void collectRecursive(String dirPath, String lowerExt, List<AsmrFile> result) {
        List<AsmrFile> items = listFiles(dirPath);
        for (AsmrFile item : items) {
            if (item.isDir()) {
                collectRecursive(item.getFilepath(), lowerExt, result);
            } else if (lowerExt.equalsIgnoreCase(item.getType())) {
                result.add(item);
            }
        }
    }

    /**
     * 递归遍历目录，一次同时收集多种扩展名的文件，跳过指定子目录。
     * OpenList 无递归 API，需手动递归调用 listFiles。
     *
     * @param dirPath 当前目录路径
     * @param extSet  目标扩展名集合（小写）
     * @param skipDir 要跳过的子目录路径，为 null 不跳过
     * @param result  收集结果
     */
    private void collectRecursiveMulti(String dirPath, Set<String> extSet,
                                        String skipDir, List<AsmrFile> result) {
        List<AsmrFile> items = listFiles(dirPath);
        for (AsmrFile item : items) {
            if (item.isDir()) {
                if (skipDir != null && item.getFilepath().equals(skipDir)) continue;
                collectRecursiveMulti(item.getFilepath(), extSet, skipDir, result);
            } else if (extSet.contains(item.getType().toLowerCase())) {
                result.add(item);
            }
        }
    }

    /**
     * 将 OpenList API 返回的文件对象转换为来源无关的 {@link AsmrFile}。
     *
     * @param file       OpenList 文件信息
     * @param parentPath 父目录路径
     * @return 统一文件模型
     */
    private static AsmrFile toAsmrFile(OpenListFile file, String parentPath) {
        String type = file.isDir() ? "dir" : OpenListClient.getExtension(file.getName());
        String filepath = joinPath(parentPath, file.getName());
        long size = file.isDir() ? 0 : file.getSize();
        String modified = file.getModified();
        AsmrFile result = new AsmrFile(file.getName(), filepath, type, file.isDir(), file.getName(), size, modified);
        result.setThumbnailUrl(thumbOf(file));
        return result;
    }

    /**
     * 拼接父路径和文件名，确保路径以 {@code /} 分隔。
     */
    private static String joinPath(String parent, String name) {
        if (parent == null || parent.isBlank() || parent.equals("/")) {
            return "/" + name;
        }
        return parent.endsWith("/") ? parent + name : parent + "/" + name;
    }
}
