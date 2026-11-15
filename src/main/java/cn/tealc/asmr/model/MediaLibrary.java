package cn.tealc.asmr.model;

import cn.tealc.asmr.source.StorageType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 媒体库数据模型。
 * <p>
 * 对应数据库表 {@code asmr_media_library}。一个媒体库代表一个音声作品的根目录，
 * 可以是本地磁盘路径（{@link StorageType#LOCAL}）或 OpenList 远程路径（{@link StorageType#OPENLIST}）。
 *
 * @author Leck
 */
public class MediaLibrary {

    /** 主键 ID */
    private Integer id;

    /** 自定义名称，如 "本地音声库" / "OpenList 服务器1" */
    private String name;

    /** 路径：本地为磁盘绝对路径，OpenList 为远程路径（如 /asmr/library1） */
    private String path;

    /** 目录层级，扫描时按此深度遍历子目录 */
    private int level;

    /** 存储类型枚举 */
    private StorageType storageType;

    /** 所属存储源 ID。新建媒体库必须关联一个存储源。 */
    private Integer storageSourceId;

    /** 最近一次完成导入的时间。 */
    private LocalDateTime lastImportedAt;

    /** 最近一次完成刮削的时间。 */
    private LocalDateTime lastScrapedAt;

    public MediaLibrary() {
    }

    /**
     * 全参构造器。
     *
     * @param name        自定义名称
     * @param path        路径
     * @param level       目录层级
     * @param storageType 存储类型枚举
     * @param storageSourceId 所属存储源 ID
     */
    public MediaLibrary(String name, String path, int level, StorageType storageType, Integer storageSourceId) {
        this.name = name;
        this.path = path;
        this.level = level;
        this.storageType = storageType;
        this.storageSourceId = storageSourceId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public void setStorageType(StorageType storageType) {
        this.storageType = storageType;
    }

    public Integer getStorageSourceId() {
        return storageSourceId;
    }

    public void setStorageSourceId(Integer storageSourceId) {
        this.storageSourceId = storageSourceId;
    }

    public LocalDateTime getLastImportedAt() {
        return lastImportedAt;
    }

    public void setLastImportedAt(LocalDateTime lastImportedAt) {
        this.lastImportedAt = lastImportedAt;
    }

    public LocalDateTime getLastScrapedAt() {
        return lastScrapedAt;
    }

    public void setLastScrapedAt(LocalDateTime lastScrapedAt) {
        this.lastScrapedAt = lastScrapedAt;
    }

    /**
     * 是否为本地媒体库。
     */
    public boolean isLocal() {
        return storageType == StorageType.LOCAL;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof MediaLibrary other) {
            return this.id != null && this.id.equals(other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name != null ? name : path;
    }
}
