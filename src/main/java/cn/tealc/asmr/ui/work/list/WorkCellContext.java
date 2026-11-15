package cn.tealc.asmr.ui.work.list;

import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.model.AsmrWork;

/**
 * 作品卡片向宿主回传的操作。
 * <p>
 * 卡片不再直接发布全局导航事件，由宿主决定如何响应。
 *
 * @author Leck
 */
public interface WorkCellContext {

    /**
     * 跳转到分类、标签、社团或声优对应的作品列表。
     */
    void navigate(AsmrKeyType type, String key);

    /**
     * 收藏状态发生变化后刷新数据。
     */
    void favoriteChanged(AsmrWork work);

    /**
     * 删除作品。不同列表可以实现为“移除记录”或“移出作品集”。
     */
    void delete(AsmrWork work);
}
