package cn.tealc.asmr.ui.navigation;

/**
 * 根据路由创建页面实例。
 *
 * @author Leck
 */
@FunctionalInterface
public interface RouteViewFactory {

    RouteView create(Route route);
}
