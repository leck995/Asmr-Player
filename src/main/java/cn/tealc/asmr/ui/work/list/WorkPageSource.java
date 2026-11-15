package cn.tealc.asmr.ui.work.list;

/**
 * 作品列表的数据源。
 * <p>
 * 主网格、收藏、分类和作品集各自提供实现，列表组件不关心查询细节。
 *
 * @author Leck
 */
@FunctionalInterface
public interface WorkPageSource {

    /**
     * 加载指定页。
     *
     * @param pageIndex 页码，从 1 开始
     * @param pageSize  每页数量
     * @return 当前页数据与总数
     */
    WorkPage load(int pageIndex, int pageSize);

    /**
     * 取消收藏后是否应从当前列表移除，收藏页返回 true。
     */
    default boolean removesOnUnfavorite() {
        return false;
    }
}
