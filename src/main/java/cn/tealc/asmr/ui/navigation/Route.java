package cn.tealc.asmr.ui.navigation;

/**
 * 主内容区的页面路由。
 * <p>
 * 只描述“当前显示哪个页面”，页面实例由 {@link RouteViewFactory} 创建。
 *
 * @author Leck
 */
public sealed interface Route permits WorksRoute, CategoriesRoute, PlaylistsRoute, CollectionDetailRoute, AlbumsRoute, AlbumDetailRoute, MediaLibraryRoute, ToolsRoute, SettingsRoute {

    /**
     * 页面标题，用于导航状态展示。
     */
    String title();
}
