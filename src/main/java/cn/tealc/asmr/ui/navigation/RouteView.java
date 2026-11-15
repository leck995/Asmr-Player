package cn.tealc.asmr.ui.navigation;

import javafx.scene.Node;

/**
 * 路由对应的页面实例。
 * <p>
 * 页面实例随历史缓存，因此需要显式处理显示、隐藏和销毁；
 * 全局事件订阅应在 {@link #onHide()} 或 {@link #dispose()} 中释放。
 *
 * @author Leck
 */
public interface RouteView {

    /**
     * 页面根节点。
     */
    Node node();

    /**
     * 页面成为当前页面时调用。
     */
    default void onShow() {
    }

    /**
     * 页面离开当前显示位置时调用，但实例仍保留在历史中。
     */
    default void onHide() {
    }

    /**
     * 页面从历史中移除时调用，之后不应再被复用。
     */
    default void dispose() {
    }
}
