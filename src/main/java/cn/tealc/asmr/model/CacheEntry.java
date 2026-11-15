package cn.tealc.asmr.model;

/**
 * 远程资源磁盘缓存条目模型。
 * <p>
 * 对应数据库表 {@code asmr_cache_entry}，记录每个被缓存的远程文件（字幕、图片）
 * 的元数据：缓存键、原始远程路径、本地存储路径、文件大小和最后访问时间等。
 * <p>
 * 用于 {@code CacheService} 的读写、LRU 淘汰和缓存大小统计。
 *
 * @author Leck
 */
public class CacheEntry {

    /** 缓存键，SHA-256(sourceIdentifier + ":" + sourcePath) 前 32 字符，主键 */
    private String cacheKey;

    /** 远程文件完整路径（如 /RJ123/audio/01.lrc），UNIQUE */
    private String sourcePath;

    /** 文件类型：{@code "text"} 或 {@code "binary"} */
    private String fileType;

    /** 本地缓存文件路径（如 resource/cache/text/abc123.lrc） */
    private String localPath;

    /** 文件大小（字节） */
    private long size;

    /** 最后访问时间（毫秒时间戳），用于 LRU 淘汰 */
    private long lastAccessTime;

    /** 创建时间（毫秒时间戳） */
    private long createTime;

    public CacheEntry() {
    }

    public CacheEntry(String cacheKey, String sourcePath, String fileType,
                      String localPath, long size, long lastAccessTime, long createTime) {
        this.cacheKey = cacheKey;
        this.sourcePath = sourcePath;
        this.fileType = fileType;
        this.localPath = localPath;
        this.size = size;
        this.lastAccessTime = lastAccessTime;
        this.createTime = createTime;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "CacheEntry{" +
                "cacheKey='" + cacheKey + '\'' +
                ", sourcePath='" + sourcePath + '\'' +
                ", fileType='" + fileType + '\'' +
                ", size=" + size +
                ", lastAccessTime=" + lastAccessTime +
                '}';
    }
}
