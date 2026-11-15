package cn.tealc.asmr.model;

import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.utils.URLUtils;

import java.util.Objects;

/**
 * 主界面快捷入口（任意门）条目。
 * <p>
 * 一条入口由 {@link AsmrKeyType} + {@code key} 确定跳转目标，点击后按类型分派到
 * {@code WorkService} 的对应分页查询。持久化在 {@code asmr_anywhere_door} 表，
 * 由 {@code AnywhereDoorService} 管理。
 *
 * @author Leck
 */
public class AnywhereDoor {
    private int id;
    private int sortOrder;
    /** 归属媒体库 ID，可为 null，表示该入口不限定媒体库 */
    private Integer mediaLibraryId;
    private String key;
    private AsmrKeyType type;
    private String name;

    public AnywhereDoor() {
    }

    public AnywhereDoor(String key) {
        this.key = key;
    }

    public AnywhereDoor(String key, AsmrKeyType type) {
        this.key = key;
        this.type = type;
    }

    public AnywhereDoor(String key, AsmrKeyType type, String name) {
        this.key = key;
        this.type = type;
        this.name = name;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Integer getMediaLibraryId() { return mediaLibraryId; }
    public void setMediaLibraryId(Integer mediaLibraryId) { this.mediaLibraryId = mediaLibraryId; }

    /** 优先显示别名，目录入口默认显示目录名称。 */
    public String getDisplayName() {
        if (name != null && !name.isBlank()) return name;
        if (key == null) return "";
        if (type == AsmrKeyType.FOLDER || type == AsmrKeyType.ONLINE) {
            String directoryName = URLUtils.getFileName(key);
            if (!directoryName.isBlank()) return directoryName;
        }
        return key;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public AsmrKeyType getType() {
        return type;
    }

    public void setType(AsmrKeyType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AnywhereDoor other && type == other.type && Objects.equals(key, other.key)
                && Objects.equals(mediaLibraryId, other.mediaLibraryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, key, mediaLibraryId);
    }
}


