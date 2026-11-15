package cn.tealc.asmr.ui.work.list;

import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.ui.component.EmptyState;
import cn.tealc.asmr.ui.component.PaginationBar;
import cn.tealc.asmr.ui.work.cells.BigWorkCell;
import cn.tealc.asmr.ui.work.cells.SmallWorkCell;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.List;

/**
 * 可嵌入的作品卡片列表。
 * <p>
 * 只负责卡片渲染、滚动、分页条和空态/加载态，数据由 {@link WorkListModel} 提供。
 * 通过 {@link #setScrollHeader(Node)} 可以把页头放进滚动区域，随卡片一起滚动。
 *
 * @author Leck
 */
public final class WorkListView extends BorderPane {

    /**
     * 侧键翻页的防抖间隔。
     * <p>
     * 鼠标侧键一次物理点击常会连发多个按下事件（硬件抖动或驱动重复上报），
     * 若逐个处理会一次翻过多页；在此间隔内的重复事件一律忽略。
     */
    private static final long SIDE_BUTTON_COOLDOWN_NANOS = 250_000_000L;

    /** 每行数量下限。 */
    private static final int MIN_COLUMNS = 2;
    /** 每行数量上限。 */
    private static final int MAX_COLUMNS = 8;
    /** 卡片可接受的最小宽度，低于此值则减少列数，避免内容被挤压。 */
    private static final double MIN_CARD_WIDTH = 180;
    /**
     * 分配宽度时预留的余量。
     * <p>
     * 卡片与间距都是小数，累加后可能与容器可用宽度产生极微小的超出，
     * 一旦超出，每行末尾那张就会被挤到下一行，表现为“设置 4 列却只排 3 列”。
     */
    private static final double WIDTH_TOLERANCE = 2;
    /**
     * 垂直滚动条预留宽度。
     * <p>
     * 始终按「滚动条存在」扣减，避免滚动条出现与否改变卡片宽度，
     * 进而引发内容高度变化、滚动条反复显隐的振荡。
     */
    private static final double SCROLLBAR_ALLOWANCE = 16;
    /** 容器尚未完成布局时使用的占位宽度。 */
    private static final double DEFAULT_CARD_WIDTH = 240;

    private final WorkListModel model;
    private final WorkListOptions options;
    /** 卡片容器；卡片宽度在布局前按可用宽度重算，使整行铺满、右侧不留空白。 */
    private final FlowPane grid = new FlowPane();
    private final VBox content = new VBox();
    private final ScrollPane scrollPane;
    private final Label status = new Label();
    private final EmptyState emptyState = new EmptyState();
    private final VBox statusBox = new VBox(status);
    private final PaginationBar paginationBar = new PaginationBar();
    private final ListChangeListener<AsmrWork> itemsListener = this::onItemsChanged;
    private Node scrollHeader;
    /** 上次由侧键触发翻页的时刻，用于过滤重复事件 */
    private long lastSideButtonNanos;
    /** 当前每个卡片的宽度，由容器宽度与列数算出 */
    private double currentCardWidth;
    /** 每行数量变化后重排卡片宽度 */
    private final ChangeListener<Number> columnsListener = (observable, oldValue, newValue) -> relayoutCards();

    public WorkListView(WorkListModel model, WorkListOptions options) {
        this.model = model;
        this.options = options;
        setMinSize(0, 0);

        grid.setAlignment(Pos.TOP_LEFT);
        grid.setHgap(options.hgap());
        grid.setVgap(options.vgap());
        grid.getStyleClass().add("work-list-grid");

        // 页头可选地放在滚动内容顶部，随卡片一起滚动
        content.getStyleClass().add("work-list-content");
        content.setSpacing(12);
        content.setMinSize(0, 0);
        content.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().add(grid);

        scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setMinSize(0, 0);
        // 以滚动面板外框宽度为基准：它不随垂直滚动条的出现而伸缩，
        // 否则「卡片变宽→内容变高→滚动条出现→卡片变窄」会来回振荡，导致列数不稳定
        scrollPane.widthProperty().addListener((observable, oldValue, newValue) ->
                applyCardWidth(newValue.doubleValue()));
        // 内边距随样式表生效后可能变化，需要重新校正
        grid.insetsProperty().addListener((observable, oldValue, newValue) ->
                applyCardWidth(scrollPane.getWidth()));

        status.getStyleClass().add("work-list-status");
        status.setWrapText(true);
        status.managedProperty().bind(status.visibleProperty());

        emptyState.getStyleClass().add("work-list-empty");
        emptyState.setIcon(Material2OutlinedMZ.MUSIC_OFF);
        emptyState.setTitle(options.emptyText());
        emptyState.setDescription(options.emptyDescription());
        emptyState.setMouseTransparent(true);
        emptyState.setVisible(false);
        emptyState.managedProperty().bind(emptyState.visibleProperty());

        statusBox.getStyleClass().add("work-list-status-box");
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setMouseTransparent(true);
        statusBox.visibleProperty().bind(status.visibleProperty());
        statusBox.managedProperty().bind(statusBox.visibleProperty());

        paginationBar.currentPageIndexProperty().bindBidirectional(model.pageIndexProperty());
        paginationBar.pageCountProperty().bind(model.pageCountProperty());
        paginationBar.setOnPageChange(index -> scrollPane.setVvalue(0));
        paginationBar.getStyleClass().add("work-list-pager");
        paginationBar.setMaxWidth(Region.USE_PREF_SIZE);
        paginationBar.setMaxHeight(Region.USE_PREF_SIZE);
        paginationBar.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> model.getPageCount() > 1 && atBottom(),
                model.pageCountProperty(),
                scrollPane.vvalueProperty(),
                scrollPane.viewportBoundsProperty(),
                content.layoutBoundsProperty()));

        StackPane body = new StackPane(scrollPane, statusBox, emptyState, paginationBar);
        StackPane.setAlignment(paginationBar, Pos.BOTTOM_CENTER);
        StackPane.setMargin(paginationBar, new Insets(0, 0, 16, 0));
        body.setMinSize(0, 0);
        setCenter(body);

        model.itemsProperty().addListener(itemsListener);
        model.loadingProperty().addListener((observable, oldValue, newValue) -> refreshStatus());
        model.loadedProperty().addListener((observable, oldValue, newValue) -> refreshStatus());
        model.errorProperty().addListener((observable, oldValue, newValue) -> refreshStatus());
        model.pageIndexProperty().addListener((observable, oldValue, newValue) -> scrollPane.setVvalue(0));
        model.sourceVersionProperty().addListener((observable, oldValue, newValue) -> scrollPane.setVvalue(0));

        content.setOnScroll(event -> {
            double deltaY = event.getDeltaY() * options.wheelFactor().getAsDouble();
            double height = content.getBoundsInLocal().getHeight();
            if (height > 0) {
                scrollPane.setVvalue(scrollPane.getVvalue() - deltaY / height);
            }
        });
        if (options.sideButtonPaging()) {
            content.setOnMousePressed(event -> {
                boolean back = event.getButton() == MouseButton.BACK;
                boolean forward = event.getButton() == MouseButton.FORWARD;
                if (!back && !forward) {
                    return;
                }
                // 一次物理点击可能连发多个按下事件，冷却期内的重复事件直接丢弃
                long now = System.nanoTime();
                if (now - lastSideButtonNanos < SIDE_BUTTON_COOLDOWN_NANOS) {
                    event.consume();
                    return;
                }
                lastSideButtonNanos = now;
                // 设置可反转方向：不同鼠标驱动对「后退/前进」侧键的映射习惯不一致
                boolean toPrevious = Config.setting.sideButtonPagingReversed.get() ? forward : back;
                if (toPrevious) {
                    model.previousPage();
                } else {
                    model.nextPage();
                }
                event.consume();
            });
        }

        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                model.detach();
                Config.setting.gridColumnsBig.removeListener(columnsListener);
                Config.setting.gridColumnsSmall.removeListener(columnsListener);
            } else {
                model.attach();
                // 大图与小图各有一套列数，两者都监听，切换显示模式后也能立即生效
                Config.setting.gridColumnsBig.addListener(columnsListener);
                Config.setting.gridColumnsSmall.addListener(columnsListener);
                // 页面重新显示时按最新列数校正一次
                relayoutCards();
            }
        });

        rebuild();
        refreshStatus();
    }

    /**
     * 每行数量变化后重新分配卡片宽度。
     * <p>
     * 供设置面板调整「每行数量」时调用；调用方无需重建卡片。
     */
    public void relayoutCards() {
        currentCardWidth = 0;
        applyCardWidth(scrollPane.getWidth());
    }

    private boolean atBottom() {
        double contentHeight = content.getBoundsInLocal().getHeight();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        return contentHeight <= viewportHeight + 1 || scrollPane.getVvalue() >= 0.999;
    }

    private void onItemsChanged(ListChangeListener.Change<? extends AsmrWork> change) {
        while (change.next()) {
            if (change.wasPermutated() || change.wasUpdated()) {
                rebuild();
                return;
            }
            if (change.wasReplaced()) {
                if (change.getRemovedSize() != change.getAddedSize()) {
                    rebuild();
                    return;
                }
                for (int index = change.getFrom(); index < change.getTo(); index++) {
                    refreshNode(index, change.getList().get(index));
                }
                continue;
            }
            if (change.wasRemoved()) {
                if (change.getRemovedSize() == 1) {
                    grid.getChildren().remove(change.getFrom());
                } else {
                    rebuild();
                    return;
                }
            }
            if (change.wasAdded()) {
                if (change.getRemovedSize() > 0) {
                    rebuild();
                    return;
                }
                addNodes(change.getFrom(), change.getAddedSubList());
            }
        }
        refreshStatus();
    }

    private void addNodes(int fromIndex, List<? extends AsmrWork> works) {
        int index = fromIndex;
        for (AsmrWork work : works) {
            Node cell = createCell(work);
            applyCardWidth(cell, currentCardWidth);
            grid.getChildren().add(index++, cell);
        }
    }

    /**
     * 按设定的每行数量平均分配卡片宽度。
     * <p>
     * 列数由设置决定，窗口变宽变窄时列数保持不变，卡片宽度随之伸缩，
     * 因此右侧不会留下空白，也不会因为余数凑不下而少放一张。
     * 窗口过窄导致卡片挤不下最小宽度时，自动减少列数以免出现横向溢出。
     * <p>
     * 基准宽度取自滚动面板外框，并**始终**按滚动条已占位来扣减，不再读取随滚动条
     * 伸缩的实际内容宽度。这样卡片宽度只由窗口尺寸与列数决定，不会因为
     * 「卡片变宽 → 内容变高 → 滚动条出现 → 可用宽度变小 → 卡片变窄」而反复振荡。
     *
     * @param containerWidth 滚动面板外框宽度
     */
    private void applyCardWidth(double containerWidth) {
        double hgap = options.hgap();
        if (containerWidth <= 0) {
            return;
        }
        // 容器左右内边距与垂直滚动条都不参与卡片排布，必须扣除，否则会多算一圈导致换行
        Insets padding = grid.getInsets();
        double availableWidth = containerWidth
                - padding.getLeft() - padding.getRight()
                - SCROLLBAR_ALLOWANCE
                - WIDTH_TOLERANCE;
        if (availableWidth <= 0) {
            return;
        }
        int columns = Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, Config.setting.getGridColumns()));
        // 卡片过窄时减少列数，宁可少放也不挤压内容
        while (columns > 1 && (availableWidth - hgap * (columns - 1)) / columns < MIN_CARD_WIDTH) {
            columns--;
        }
        double width = (availableWidth - hgap * (columns - 1)) / columns;
        if (Math.abs(width - currentCardWidth) < 0.5) {
            return;
        }
        currentCardWidth = width;
        for (Node node : grid.getChildren()) {
            applyCardWidth(node, width);
        }
    }

    /**
     * 把宽度下发给单个卡片；不支持自适应的卡片保持原尺寸。
     */
    private void applyCardWidth(Node cell, double width) {
        if (width > 0 && cell instanceof AdaptiveWidthCell adaptive) {
            adaptive.setCardWidth(width);
        }
    }

    private void refreshNode(int index, AsmrWork work) {
        if (index < 0 || index >= grid.getChildren().size()) {
            return;
        }
        Node node = grid.getChildren().get(index);
        if (node instanceof BigWorkCell big) {
            big.refreshData(work);
        } else if (node instanceof SmallWorkCell small) {
            small.refreshData(work);
        } else {
            grid.getChildren().set(index, createCell(work));
        }
    }

    private Node createCell(AsmrWork work) {
        return options.cellFactory().create(work, options.cellContext());
    }

    /**
     * 重建全部卡片，切换大图/小图时使用。
     */
    public void rebuild() {
        grid.getChildren().setAll(model.getItems().stream().map(this::createCell).toList());
        // 清掉缓存后按容器当前宽度重新分配，卡片尺寸随显示模式一并更新
        currentCardWidth = 0;
        applyCardWidth(scrollPane.getWidth());
        if (currentCardWidth <= 0) {
            // 容器尚未完成布局，先按默认宽度占位，待宽度变化时统一校正
            for (Node node : grid.getChildren()) {
                applyCardWidth(node, DEFAULT_CARD_WIDTH);
            }
        }
        refreshStatus();
    }

    private void refreshStatus() {
        String error = model.getError();
        if (error != null && !error.isBlank()) {
            showStatus(error);
            emptyState.setVisible(false);
            return;
        }
        if (model.isLoading()) {
            showStatus("加载中...");
            emptyState.setVisible(false);
            return;
        }
        status.setVisible(false);
        emptyState.setVisible(model.isLoaded() && model.getItems().isEmpty());
    }

    private void showStatus(String text) {
        status.setText(text);
        status.setVisible(true);
    }

    /**
     * 分页条实例。未内置到底部时，由宿主放到工具栏等位置。
     */
    public PaginationBar paginationBar() {
        return paginationBar;
    }

    /**
     * 把页头放进滚动区域顶部，随卡片一起滚动；传 null 则移出。
     * <p>
     * 页头不再固定，向下滚动时先移出视野，把完整窗口高度让给卡片。
     */
    public void setScrollHeader(Node header) {
        if (scrollHeader == header) {
            return;
        }
        content.getChildren().remove(scrollHeader);
        scrollHeader = header;
        if (header == null) {
            return;
        }
        content.getChildren().addFirst(header);
        if (header instanceof Region region) {
            region.setMinWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }

    public Node getScrollHeader() {
        return scrollHeader;
    }

    public WorkListModel model() {
        return model;
    }

    /**
     * 释放列表监听，页面从历史中移除时调用。
     */
    public void dispose() {
        model.itemsProperty().removeListener(itemsListener);
        Config.setting.gridColumnsBig.removeListener(columnsListener);
        Config.setting.gridColumnsSmall.removeListener(columnsListener);
        model.detach();
    }
}
