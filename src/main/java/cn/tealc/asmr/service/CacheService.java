package cn.tealc.asmr.service;

import cn.tealc.asmr.dao.CacheDao;
import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.model.CacheEntry;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.utils.AppPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;
import java.util.function.Supplier;
import java.util.HexFormat;

/**
 * 远程资源磁盘缓存服务。
 * <p>
 * 在访问远程资源（字幕文本、图片二进制）前先检测本地磁盘缓存：命中则直接读取本地文件，
 * 未命中才调远程下载器获取内容，下载成功后写入缓存供下次使用。
 * <p>
 * 缓存元数据存储在 SQLite（{@code asmr_cache_entry} 表），缓存文件存储在
 * {@code <dataDir>/cache/<text|binary>/<cacheKey>.<ext>} 目录下。
 * <p>
 * 支持按上限自动 LRU 淘汰（基于 {@code last_access_time}）、手动清空全部缓存、
 * 查询当前缓存总大小和条目数。缓存关闭（{@code Config.setting.cacheEnable}）时
 * 直接调远程下载器，不读写本地缓存。
 * <p>
 * 通过 {@link cn.tealc.asmr.di.DI#get(Class)} 获取单例，{@link CacheDao} 通过
 * {@code @Inject} 字段注入。写操作加 {@code synchronized} 保证线程安全
 * （SQLite 单连接 + 多后台线程调用）。
 *
 * @author Leck
 */
public class CacheService {

    private static final Logger LOG = LoggerFactory.getLogger(CacheService.class);

    /** 文本缓存子目录 */
    private static final String TEXT_SUBDIR = "text";

    /** 二进制缓存子目录 */
    private static final String BINARY_SUBDIR = "binary";

    @Inject
    private CacheDao dao;

    /**
     * 读取文本资源：先查缓存命中读本地文件，未命中调 loader 下载后写入缓存。
     * <p>
     * 缓存关闭、缓存读写失败时静默降级到 loader，不影响正常功能。
     *
     * @param sourceIdentifier 来源标识（如 {@code "openlist:http://host:port"}），参与缓存键生成
     * @param sourcePath       远程文件完整路径
     * @param fileExt          文件扩展名（不含点，用于本地文件命名，可为空）
     * @param loader           远程下载器，返回文件文本内容
     * @return 文件文本内容，失败返回 null
     */
    public String readText(String sourceIdentifier, String sourcePath,
                           String fileExt, Supplier<String> loader) {
        if (!isCacheEnabled()) {
            return loader.get();
        }

        String cacheKey = generateKey(sourceIdentifier, sourcePath);
        CacheEntry entry = dao.findByKey(cacheKey);
        if (entry != null) {
            String cached = readLocalText(entry.getLocalPath());
            if (cached != null) {
                dao.updateAccessTime(entry.getCacheKey(), System.currentTimeMillis());
                return cached;
            }
            // 本地文件丢失，清理残余 DB 记录
            dao.deleteByKey(entry.getCacheKey());
        }

        // 远程下载
        String content = loader.get();
        if (content != null) {
            writeToCache(cacheKey, sourcePath, TEXT_SUBDIR, fileExt,
                    content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return content;
    }

    /**
     * 读取二进制资源：先查缓存命中读本地文件，未命中调 loader 下载后写入缓存。
     * <p>
     * 缓存关闭、缓存读写失败时静默降级到 loader，不影响正常功能。
     *
     * @param sourceIdentifier 来源标识
     * @param sourcePath       远程文件完整路径
     * @param fileExt          文件扩展名（不含点，用于本地文件命名，可为空）
     * @param loader           远程下载器，返回文件二进制内容
     * @return 文件二进制内容，失败返回 null
     */
    public byte[] readBinary(String sourceIdentifier, String sourcePath,
                             String fileExt, Supplier<byte[]> loader) {
        if (!isCacheEnabled()) {
            return loader.get();
        }

        String cacheKey = generateKey(sourceIdentifier, sourcePath);
        CacheEntry entry = dao.findByKey(cacheKey);
        if (entry != null) {
            byte[] cached = readLocalBinary(entry.getLocalPath());
            if (cached != null) {
                dao.updateAccessTime(entry.getCacheKey(), System.currentTimeMillis());
                return cached;
            }
            dao.deleteByKey(entry.getCacheKey());
        }

        byte[] content = loader.get();
        if (content != null) {
            writeToCache(cacheKey, sourcePath, BINARY_SUBDIR, fileExt, content);
        }
        return content;
    }

    /**
     * 检查缓存总大小是否超过上限，超过则按 LRU 策略淘汰最旧条目。
     * <p>
     * 在每次写入新缓存后调用。淘汰时同时删除本地缓存文件和 DB 记录。
     */
    public synchronized void evictIfNeeded() {
        long maxBytes = getMaxSizeBytes();
        long currentSize = dao.getTotalSize();
        if (currentSize <= maxBytes) {
            return;
        }

        // 一次最多淘汰 50 条，避免单次删除过多
        List<CacheEntry> candidates = dao.findLruEntries(50);
        for (CacheEntry entry : candidates) {
            deleteCacheFile(entry.getLocalPath());
            dao.deleteByKey(entry.getCacheKey());
            currentSize -= entry.getSize();
            if (currentSize <= maxBytes) {
                return;
            }
        }
    }

    /**
     * 查询当前缓存总大小。
     *
     * @return 缓存总大小（字节）
     */
    public long getCacheSize() {
        return dao.getTotalSize();
    }

    /**
     * 查询缓存条目总数。
     *
     * @return 条目数
     */
    public int getCacheCount() {
        return dao.getEntryCount();
    }

    /**
     * 清空全部缓存：删除数据目录下的 {@code cache/} 所有文件，并清空 DB 记录。
     */
    public synchronized void clearAll() {
        try {
            Path cacheRoot = AppPaths.cacheDir().toPath();
            if (Files.exists(cacheRoot)) {
                deleteDirectory(cacheRoot.toFile());
            }
        } catch (Exception e) {
            LOG.error("删除缓存目录失败", e);
        }
        dao.deleteAll();
        LOG.info("缓存已全部清空");
    }

    /**
     * 使指定远程路径及其子路径的本地缓存失效。
     * <p>
     * 用于目录删除、重命名和移动：这些操作会让目录下所有已缓存文件的路径失效。
     * 缓存表只记录 source_path，没有来源标识列，因此按缓存键中的来源标识逐条校验。
     *
     * @param sourceIdentifier 来源标识
     * @param sourcePath       远程目录完整路径
     */
    public synchronized void invalidatePrefix(String sourceIdentifier, String sourcePath) {
        if (sourceIdentifier == null || sourceIdentifier.isBlank()
                || sourcePath == null || sourcePath.isBlank()) {
            return;
        }
        for (CacheEntry entry : dao.findBySourcePathPrefix(sourcePath)) {
            if (!generateKey(sourceIdentifier, entry.getSourcePath()).equals(entry.getCacheKey())) {
                continue;
            }
            deleteCacheFile(entry.getLocalPath());
            dao.deleteByKey(entry.getCacheKey());
        }
    }

    // ===================== 内部工具 =====================

    /**
     * 判断缓存是否开启。
     */
    private boolean isCacheEnabled() {
        return Config.setting.cacheEnable.get();
    }

    /**
     * 获取缓存上限（字节）。
     */
    private long getMaxSizeBytes() {
        int mb = Config.setting.cacheMaxSizeMB.get();
        return (long) mb * 1024L * 1024L;
    }

    /**
     * 生成缓存键：SHA-256(sourceIdentifier + ":" + sourcePath) 前 32 个十六进制字符。
     */
    private String generateKey(String sourceIdentifier, String sourcePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = sourceIdentifier + ":" + sourcePath;
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (Exception e) {
            // fallback：用字符串 hashCode（不理想但保证不崩溃）
            LOG.error("生成缓存键失败，使用 fallback", e);
            return Integer.toHexString((sourceIdentifier + sourcePath).hashCode());
        }
    }

    /**
     * 构造本地缓存文件路径：{@code <dataDir>/cache/<type>/<cacheKey>.<ext>}
     */
    private String buildLocalPath(String fileType, String cacheKey, String ext) {
        String filename = (ext != null && !ext.isBlank())
                ? cacheKey + "." + ext
                : cacheKey;
        return new File(AppPaths.cacheDir(fileType), filename).getAbsolutePath();
    }

    /**
     * 将内容写入本地缓存文件，并记录到 DB，然后触发 LRU 淘汰。
     */
    private synchronized void writeToCache(String cacheKey, String sourcePath,
                                           String fileType, String fileExt, byte[] data) {
        String localPath = buildLocalPath(fileType, cacheKey, fileExt);
        try {
            Path path = Paths.get(localPath);
            Files.createDirectories(path.getParent());
            Files.write(path, data);

            long now = System.currentTimeMillis();
            CacheEntry entry = new CacheEntry(cacheKey, sourcePath, fileType,
                    localPath, data.length, now, now);
            dao.save(entry);
            evictIfNeeded();
        } catch (Exception e) {
            LOG.warn("写入缓存失败: {}", sourcePath, e);
        }
    }

    /**
     * 读取本地缓存文本文件。
     */
    private String readLocalText(String localPath) {
        try {
            Path path = Paths.get(localPath);
            if (Files.exists(path)) {
                return Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            LOG.warn("读取缓存文本失败: {}", localPath, e);
        }
        return null;
    }

    /**
     * 读取本地缓存二进制文件。
     */
    private byte[] readLocalBinary(String localPath) {
        try {
            Path path = Paths.get(localPath);
            if (Files.exists(path)) {
                return Files.readAllBytes(path);
            }
        } catch (Exception e) {
            LOG.warn("读取缓存二进制失败: {}", localPath, e);
        }
        return null;
    }

    /**
     * 删除单个缓存文件。
     */
    private void deleteCacheFile(String localPath) {
        try {
            Files.deleteIfExists(Paths.get(localPath));
        } catch (Exception e) {
            LOG.warn("删除缓存文件失败: {}", localPath, e);
        }
    }

    /**
     * 递归删除目录及其内容。
     */
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectory(child);
                }
            }
        }
        dir.delete();
    }
}
