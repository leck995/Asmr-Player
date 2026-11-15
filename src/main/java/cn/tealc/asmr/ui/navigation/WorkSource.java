package cn.tealc.asmr.ui.navigation;

import cn.tealc.asmr.model.AsmrKeyType;

import java.util.Objects;

/**
 * 作品列表的数据来源。
 * <p>
 * 统一描述“哪一类作品”，替代散落在各处的 (key, type, mediaLibraryId) 三元组。
 *
 * @author Leck
 */
public record WorkSource(AsmrKeyType type, String key, Integer mediaLibraryId) {

    public WorkSource {
        Objects.requireNonNull(type, "type");
    }

    public static WorkSource all() {
        return new WorkSource(AsmrKeyType.ALL, null, null);
    }

    public static WorkSource favorites() {
        return new WorkSource(AsmrKeyType.FAVORITE, null, null);
    }

    public static WorkSource of(AsmrKeyType type, String key) {
        return new WorkSource(type, key, null);
    }

    public static WorkSource of(AsmrKeyType type, String key, Integer mediaLibraryId) {
        return new WorkSource(type, key, mediaLibraryId);
    }

    /**
     * 页面标题，例如“分类：耳语”“搜索：RJ123”。
     */
    public String title() {
        String value = key == null ? "" : key;
        return switch (type) {
            case ALL -> "全部";
            case SEARCH -> "搜索：" + value;
            case FAVORITE -> "收藏";
            case CATEGORY -> "分类：" + value;
            case CIRCLE -> "社团：" + value;
            case VA -> "声优：" + value;
            case TAG -> "标签：" + value;
            case FOLDER -> "文件夹：" + value;
            case ONLINE -> "网络：" + value;
        };
    }

    /**
     * 页头说明，描述当前列表的范围。
     */
    public String description() {
        String value = key == null ? "" : key;
        return switch (type) {
            case ALL -> "媒体库中的全部内容";
            case FAVORITE -> "你收藏的内容";
            case SEARCH -> "搜索 “" + value + "” 的结果";
            case CATEGORY -> "分类 “" + value + "” 下的内容";
            case CIRCLE -> "社团 “" + value + "” 下的内容";
            case VA -> "声优 “" + value + "” 下的内容";
            case TAG -> "标签 “" + value + "” 下的内容";
            case FOLDER -> "文件夹 “" + value + "” 中的内容";
            case ONLINE -> "网络来源 “" + value + "” 中的内容";
        };
    }
}
