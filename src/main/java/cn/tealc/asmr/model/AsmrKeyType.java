package cn.tealc.asmr.model;

/**
 * 作品列表查询与浏览入口的类型取值（ALL/CATEGORY/TAG/CIRCLE/VA/FOLDER/ONLINE/FAVORITE/SEARCH）。
 * <p>
 * {@code WorkService} 的分页查询按其分派到对应的 {@code WorkDao} 取数方法；
 * {@link AnywhereDoor} 用它标识快捷入口的跳转目标，分类管理页（{@code CategoriesService}）
 * 用其中 CATEGORY/TAG/CIRCLE/VA 四种取值切换字典列表。
 *
 * @author Leck
 */
public enum AsmrKeyType {
    ALL,
    CATEGORY,
    TAG,
    CIRCLE,
    VA,
    FOLDER,
    ONLINE,
    FAVORITE,
    SEARCH;
}
