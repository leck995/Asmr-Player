package cn.tealc.asmr.task.background;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

/**
 * 后台任务类型枚举，关联显示图标与默认标签。
 * <p>
 * 新增任务类型时只需在此追加一项，UI 层通过 {@link #getIcon()} 和 {@link #getLabel()}
 * 自动获取对应图标与文案。
 *
 * @author Leck
 */
public enum TaskType {
    /** 媒体库扫描：递归列举子目录并逐个导入作品 */
    SCAN_LIBRARY(Material2OutlinedAL.FOLDER, "扫描媒体库"),
    /** DLSite 元数据刮削：单作品或批量抓取远程信息 */
    SCRAPE_META(Material2OutlinedAL.CLOUD_DOWNLOAD, "刮削元数据"),
    /** 批量重命名：按模板重命名作品目录 */
    RENAME(Material2OutlinedMZ.REFRESH, "批量重命名"),
    /** 字幕同步：三级递进查找并关联字幕文件 */
    SYNC_SUBTITLE(Material2OutlinedAL.CLOSED_CAPTION, "字幕同步"),
    /** 版本检查：查询 GitHub release 是否有新版本 */
    VERSION_CHECK(Material2OutlinedAL.AUTORENEW, "版本检查"),
    /** 通用任务：无特定类型归属 */
    GENERIC(Material2OutlinedAL.ADD_TASK, "后台任务");

    private final Ikon icon;
    private final String label;

    TaskType(Ikon icon, String label) {
        this.icon = icon;
        this.label = label;
    }

    /** 获取任务在 UI 上展示的图标。 */
    public Ikon getIcon() {
        return icon;
    }

    /** 获取任务类型的默认文案标签。 */
    public String getLabel() {
        return label;
    }
}
