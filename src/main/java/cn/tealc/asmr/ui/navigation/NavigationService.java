package cn.tealc.asmr.ui.navigation;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 轻量页面导航器。
 * <p>
 * 维护唯一的页面历史栈，并把当前路由对应的页面渲染到内容容器：
 * <ul>
 *   <li>{@link #openRoot(Route)}：根级切换，清空历史；仅用于首屏等不该产生历史的场景</li>
 *   <li>{@link #push(Route)}：常规切页，压入历史；侧栏、快捷入口与下钻都走这里</li>
 *   <li>{@link #back()} / {@link #forward()}：在历史中移动</li>
 * </ul>
 * 页面实例随历史缓存，返回时可以保留滚动位置、搜索词等界面状态。
 *
 * @author Leck
 */
public final class NavigationService {
    private static final Logger LOG = LoggerFactory.getLogger(NavigationService.class);
    /**
     * 历史栈上限。
     * <p>
     * 页面实例随历史驻留内存，作品列表页每页含数十张卡片及其封面图，
     * 因此只保留最近几步，足以覆盖正常的前进后退，又不会长期占住大量内存。
     */
    private static final int MAX_HISTORY = 5;

    private final List<Entry> history = new ArrayList<>();
    private final ReadOnlyObjectWrapper<Route> currentRoute = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper canGoBack = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper canGoForward = new ReadOnlyBooleanWrapper(false);

    private StackPane host;
    private RouteViewFactory factory;
    private Route pendingRoute;
    private int index = -1;

    /**
     * 绑定页面容器和页面工厂。绑定前发起的导航会延迟到绑定后执行。
     */
    public void attach(StackPane host, RouteViewFactory factory) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(factory, "factory");
        runOnFxThread(() -> {
            if (this.host != null) {
                throw new IllegalStateException("NavigationService 已经绑定过页面容器");
            }
            this.host = host;
            this.factory = factory;
            if (pendingRoute != null) {
                Route route = pendingRoute;
                pendingRoute = null;
                openRoot(route);
            } else if (!history.isEmpty()) {
                renderCurrent();
            }
        });
    }

    /**
     * 根级切换：清空现有历史后打开新页面。
     */
    public void openRoot(Route route) {
        Objects.requireNonNull(route, "route");
        runOnFxThread(() -> {
            if (host == null) {
                pendingRoute = route;
                return;
            }
            if (route.equals(currentRoute.get()) && index >= 0) {
                history.get(index).view().onShow();
                return;
            }
            clearHistory();
            append(route);
            renderCurrent();
        });
    }

    /**
     * 下钻：截断前进历史后压入新页面。
     */
    public void push(Route route) {
        Objects.requireNonNull(route, "route");
        runOnFxThread(() -> {
            if (host == null) {
                pendingRoute = route;
                return;
            }
            if (route.equals(currentRoute.get())) {
                return;
            }
            removeForward();
            if (index >= 0) {
                history.get(index).view().onHide();
            }
            append(route);
            renderCurrent();
        });
    }

    public void back() {
        runOnFxThread(() -> {
            if (!canGoBack.get()) {
                return;
            }
            history.get(index).view().onHide();
            index--;
            renderCurrent();
        });
    }

    public void forward() {
        runOnFxThread(() -> {
            if (!canGoForward.get()) {
                return;
            }
            history.get(index).view().onHide();
            index++;
            renderCurrent();
        });
    }

    /**
     * 清空全部历史并释放页面实例。
     */
    public void clear() {
        runOnFxThread(() -> {
            clearHistory();
            currentRoute.set(null);
            if (host != null) {
                host.getChildren().clear();
            }
        });
    }

    public ReadOnlyObjectProperty<Route> currentRouteProperty() {
        return currentRoute.getReadOnlyProperty();
    }

    public Route getCurrentRoute() {
        return currentRoute.get();
    }

    public ReadOnlyBooleanProperty canGoBackProperty() {
        return canGoBack.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty canGoForwardProperty() {
        return canGoForward.getReadOnlyProperty();
    }

    private void append(Route route) {
        RouteView view = factory.create(route);
        history.add(new Entry(route, view));
        index = history.size() - 1;
        if (history.size() > MAX_HISTORY) {
            Entry removed = history.removeFirst();
            removed.view().dispose();
            index--;
        }
    }

    private void renderCurrent() {
        Entry entry = history.get(index);
        Node node = entry.view().node();
        if (host.getChildren().size() != 1 || host.getChildren().getFirst() != node) {
            host.getChildren().setAll(node);
        }
        entry.view().onShow();
        currentRoute.set(entry.route());
        updateFlags();
        LOG.debug("导航到 {}", entry.route());
    }

    private void removeForward() {
        for (int i = history.size() - 1; i > index; i--) {
            Entry removed = history.remove(i);
            removed.view().dispose();
        }
        updateFlags();
    }

    private void clearHistory() {
        for (Entry entry : history) {
            entry.view().dispose();
        }
        history.clear();
        index = -1;
        updateFlags();
    }

    private void updateFlags() {
        canGoBack.set(index > 0);
        canGoForward.set(index >= 0 && index < history.size() - 1);
    }

    private static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private record Entry(Route route, RouteView view) {
    }
}
