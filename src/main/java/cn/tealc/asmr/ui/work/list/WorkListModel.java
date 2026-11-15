package cn.tealc.asmr.ui.work.list;

import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.mvvm.NotificationObserver;
import cn.tealc.asmr.config.NotificationKey;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.IntSupplier;

/**
 * 作品列表的分页、加载与局部更新状态。
 * <p>
 * 数据在虚拟线程加载，结果回到 JavaFX 线程；连续切换数据源时用代际号丢弃过期结果。
 *
 * @author Leck
 */
public final class WorkListModel {
    private static final Logger LOG = LoggerFactory.getLogger(WorkListModel.class);

    private final ObservableList<AsmrWork> items = FXCollections.observableArrayList();
    private final IntegerProperty pageIndex = new SimpleIntegerProperty(1);
    private final IntegerProperty pageCount = new SimpleIntegerProperty(1);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final ReadOnlyBooleanWrapper loaded = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyIntegerWrapper sourceVersion = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyIntegerWrapper total = new ReadOnlyIntegerWrapper(0);
    private final StringProperty error = new SimpleStringProperty("");
    private final IntSupplier pageSizeSupplier;

    private WorkPageSource source;
    private long generation;
    private boolean suppressPageReload;
    private boolean attached;

    private final NotificationObserver workUpdateObserver = (key, payload) -> {
        if (payload.length > 0 && payload[0] instanceof AsmrWork work) {
            update(work);
        }
    };
    private final NotificationObserver workRemoveObserver = (key, payload) -> {
        if (payload.length == 0 || !(payload[0] instanceof AsmrWork work)) {
            return;
        }
        boolean favoriteChange = payload.length > 1 && Boolean.TRUE.equals(payload[1]);
        if (favoriteChange) {
            update(work);
        } else {
            remove(work);
        }
    };
    private final NotificationObserver refreshObserver = (key, payload) -> reload();

    public WorkListModel(int pageSize) {
        this(() -> pageSize);
    }

    public WorkListModel(IntSupplier pageSizeSupplier) {
        if (pageSizeSupplier.getAsInt() <= 0) {
            throw new IllegalArgumentException("pageSize 必须大于 0");
        }
        this.pageSizeSupplier = pageSizeSupplier;
        pageIndex.addListener((observable, oldValue, newValue) -> {
            if (!suppressPageReload && newValue.intValue() > 0) {
                startLoad();
            }
        });
    }

    /**
     * 切换数据源。resetPage 为 true 时回到第一页。
     */
    public void setSource(WorkPageSource source, boolean resetPage) {
        onFx(() -> {
            this.source = source;
            this.error.set("");
            this.loaded.set(false);
            this.total.set(0);
            this.pageCount.set(1);
            sourceVersion.set(sourceVersion.get() + 1);
            if (resetPage) {
                suppressPageReload = true;
                pageIndex.set(1);
                suppressPageReload = false;
            }
            startLoad();
        });
    }

    /**
     * 重新加载当前页。
     */
    public void reload() {
        onFx(this::startLoad);
    }

    /**
     * 数据变化后就地更新对应卡片；收藏页取消收藏时直接移除。
     */
    public void update(AsmrWork work) {
        if (work == null) {
            return;
        }
        onFx(() -> {
            int index = indexOf(work.getId());
            if (index < 0) {
                return;
            }
            if (source != null && source.removesOnUnfavorite() && !work.getFavorites()) {
                removeAt(index);
            } else {
                items.set(index, work);
            }
        });
    }

    /**
     * 从当前列表移除作品。
     */
    public void remove(AsmrWork work) {
        if (work == null) {
            return;
        }
        onFx(() -> {
            int index = indexOf(work.getId());
            if (index >= 0) {
                removeAt(index);
            }
        });
    }

    public void previousPage() {
        onFx(() -> {
            // 加载期间忽略翻页请求，避免连按把页码推远、结果回来时已跳过若干页
            if (loading.get() || pageIndex.get() <= 1) {
                return;
            }
            pageIndex.set(pageIndex.get() - 1);
        });
    }

    public void nextPage() {
        onFx(() -> {
            // 加载期间忽略翻页请求，避免连按把页码推远、结果回来时已跳过若干页
            if (loading.get() || pageIndex.get() >= pageCount.get()) {
                return;
            }
            pageIndex.set(pageIndex.get() + 1);
        });
    }

    /**
     * 挂载全局数据变更订阅，重复调用无副作用。
     */
    public void attach() {
        onFx(() -> {
            if (attached) {
                return;
            }
            attached = true;
            EventBus.subscribe(NotificationKey.MAIN_GRID_WORK_UPDATE, workUpdateObserver);
            EventBus.subscribe(NotificationKey.MAIN_GRID_WORK_REMOVE, workRemoveObserver);
            EventBus.subscribe(NotificationKey.MAIN_GRID_REFRESH, refreshObserver);
        });
    }

    /**
     * 解除全局数据变更订阅。
     */
    public void detach() {
        onFx(() -> {
            if (!attached) {
                return;
            }
            attached = false;
            EventBus.unsubscribe(workUpdateObserver);
            EventBus.unsubscribe(workRemoveObserver);
            EventBus.unsubscribe(refreshObserver);
        });
    }

    public void dispose() {
        detach();
    }

    private void startLoad() {
        WorkPageSource current = source;
        if (current == null) {
            loading.set(false);
            return;
        }
        long request = ++generation;
        int requestedPage = Math.max(1, pageIndex.get());
        int pageSize = getPageSize();
        loading.set(true);
        loaded.set(false);
        error.set("");
        Thread.startVirtualThread(() -> {
            try {
                WorkPage result = current.load(requestedPage, pageSize);
                Platform.runLater(() -> applyResult(request, requestedPage, current, result, pageSize));
            } catch (RuntimeException exception) {
                LOG.error("加载作品列表失败", exception);
                Platform.runLater(() -> applyFailure(request));
            }
        });
    }

    private void applyResult(long request, int requestedPage, WorkPageSource requestedSource, WorkPage result,
                             int pageSize) {
        if (request != generation || source != requestedSource) {
            return;
        }
        int pages = Math.max(1, (result.total() + pageSize - 1) / pageSize);
        pageCount.set(pages);
        if (requestedPage > pages) {
            loading.set(false);
            suppressPageReload = true;
            pageIndex.set(pages);
            suppressPageReload = false;
            startLoad();
            return;
        }
        total.set(result.total());
        items.setAll(result.works());
        loading.set(false);
        loaded.set(true);
    }

    private void applyFailure(long request) {
        if (request != generation) {
            return;
        }
        loading.set(false);
        loaded.set(true);
        error.set("加载失败，请重试");
    }

    private void removeAt(int index) {
        items.remove(index);
        total.set(Math.max(0, total.get() - 1));
    }

    private int indexOf(int workId) {
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).getId() == workId) {
                return index;
            }
        }
        return -1;
    }

    private static void onFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    public ObservableList<AsmrWork> getItems() {
        return items;
    }

    public ObservableList<AsmrWork> itemsProperty() {
        return items;
    }

    public int getPageSize() {
        return pageSizeSupplier.getAsInt();
    }

    public int getPageIndex() {
        return pageIndex.get();
    }

    public IntegerProperty pageIndexProperty() {
        return pageIndex;
    }

    public int getPageCount() {
        return pageCount.get();
    }

    public IntegerProperty pageCountProperty() {
        return pageCount;
    }

    public boolean isLoading() {
        return loading.get();
    }

    public ReadOnlyBooleanProperty loadingProperty() {
        return loading;
    }

    public boolean isLoaded() {
        return loaded.get();
    }

    public ReadOnlyBooleanProperty loadedProperty() {
        return loaded.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty sourceVersionProperty() {
        return sourceVersion.getReadOnlyProperty();
    }

    public int getTotal() {
        return total.get();
    }

    public ReadOnlyIntegerProperty totalProperty() {
        return total.getReadOnlyProperty();
    }

    public String getError() {
        return error.get();
    }

    public ReadOnlyStringProperty errorProperty() {
        return error;
    }
}
