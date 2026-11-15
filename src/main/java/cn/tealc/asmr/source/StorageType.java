package cn.tealc.asmr.source;

/**
 * 存储来源类型枚举。
 * <p>
 * 用于区分媒体作品的文件来源，{@code AsmrWork.storageType} 字段的值对应此枚举的 code。
 * 目前定义了本地磁盘与 Alist/OpenList 两种来源，后续新增来源（如 WebDAV）时在此扩展。
 *
 * @author Leck
 */
public enum StorageType {
    /** 本地磁盘 */
    LOCAL(0),
    /** Alist/OpenList 网盘 */
    OPENLIST(1);

    private final int code;

    StorageType(int code) {
        this.code = code;
    }

    /**
     * 获取存储类型编码，与 {@code AsmrWork.storageType} 字段对应。
     *
     * @return 编码值
     */
    public int getCode() {
        return code;
    }

    /**
     * 根据编码值返回对应的存储类型枚举。
     * <p>
     * 传入 {@code null} 或无法识别的编码时，默认返回 {@link #LOCAL}。
     *
     * @param code 存储类型编码（来自 {@code AsmrWork.getStorageType()}）
     * @return 对应的枚举值
     */
    public static StorageType fromCode(Integer code) {
        if (code == null) return LOCAL;
        for (StorageType type : values()) {
            if (type.code == code) return type;
        }
        return LOCAL;
    }
}
