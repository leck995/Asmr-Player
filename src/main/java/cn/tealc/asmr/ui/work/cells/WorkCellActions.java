package cn.tealc.asmr.ui.work.cells;

import cn.tealc.asmr.model.AsmrWork;

/**
 * 作品 Cell 右键菜单行为回调接口。
 * <p>
 * 每个方法对应右键菜单的一项操作，由具体 Cell（BigWorkCell / SmallWorkCell / DuplicateWorkCell）
 * 提供实现。{@link WorkContextMenuFactory} 通过此接口绑定菜单项回调，使菜单构建逻辑只存在一处。
 *
 * @author Leck
 */
public interface WorkCellActions {

    /** 同步元数据 */
    void onSync(AsmrWork work);

    /** 修改作品信息 */
    void onEditInfo(AsmrWork work);

    /** 修改标签 */
    void onEditTags(AsmrWork work);

    /** 删除作品（含弹窗选择永久删除/回收站/仅删库） */
    void onDelete(AsmrWork work);

    /** 复制 作品编号到剪贴板 */
    void onCopyWorkId(AsmrWork work);

    /** 跳转到 DLsite 网页 */
    void onOpenWeb(AsmrWork work);

    /** 切换收藏状态 */
    void onToggleFavorite(AsmrWork work);

    /** 移动作品到其他目录（本地与 OpenList 均支持） */
    void onMove(AsmrWork work);

    /** 字幕重命名（本地与 OpenList 均支持） */
    void onSubtitleRename(AsmrWork work);

    /** 在系统文件管理器中打开作品文件夹（仅本地） */
    void onOpenFolder(AsmrWork work);
}
