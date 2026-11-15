package cn.tealc.asmr.ui.album;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.album.WorkAlbumSummary;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.mvvm.NotificationObserver;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.MetadataSyncService;
import cn.tealc.asmr.service.WorkAlbumService;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.work.list.WorkCellFactory;
import cn.tealc.asmr.ui.work.list.WorkListModel;
import cn.tealc.asmr.ui.work.list.WorkListOptions;
import cn.tealc.asmr.ui.work.list.WorkListView;
import cn.tealc.asmr.ui.work.list.WorkSortFilterButton;
import cn.tealc.asmr.utils.CssLoader;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignV;

/**
 * 作品集详情页，使用通用作品列表展示集内作品。
 *
 * @author Leck
 */
public class WorkAlbumDetailView extends BorderPane {
    private static final String[] TOPICS = {NotificationKey.WORK_ALBUM_CHANGED, NotificationKey.MAIN_GRID_REFRESH};

    private final int albumId;
    private final WorkAlbumService albumService = DI.get(WorkAlbumService.class);
    private final MetadataSyncService metadataSyncService = DI.get(MetadataSyncService.class);
    private final WorkListModel listModel = new WorkListModel(Config.setting.gridListCount.get());
    private final WorkListView listView;
    private final PageHeader pageHeader = new PageHeader();
    private final Label description = new Label();
    private final TextField search = new TextField();
    private final IntegerProperty sortType = new SimpleIntegerProperty(1);
    private final BooleanProperty sqlOrder = new SimpleBooleanProperty(true);
    private final BooleanProperty sqlSubtext = new SimpleBooleanProperty(false);
    private final BooleanProperty sqlNsfw = new SimpleBooleanProperty(true);
    private final BooleanProperty sqlNoMate = new SimpleBooleanProperty(false);
    private final BooleanProperty showLocal = new SimpleBooleanProperty(true);
    private final BooleanProperty showNetwork = new SimpleBooleanProperty(true);
    private final Button edit = WorkAlbumView.iconButton(Material2OutlinedAL.EDIT, "修改作品集");
    private final PauseTransition reload = new PauseTransition(Duration.millis(150));
    private final NotificationObserver observer = (key, payload) -> Platform.runLater(() -> onNotification(key));
    private final ChangeListener<Boolean> viewModeListener;

    private WorkAlbumSummary current;
    private Task<?> metadataTask;
    private long metadataGeneration;
    private boolean observingViewMode;
    private boolean loadedOnce;

    public WorkAlbumDetailView(int albumId) {
        this.albumId = albumId;
        getStyleClass().add("album-root");
        getStylesheets().addAll(CssLoader.getCss(CssLoader.workGrid), CssLoader.getCss(CssLoader.workAlbum));
        setPadding(new Insets(20, 24, 12, 24));
        setMinSize(0, 0);

        pageHeader.getStyleClass().add("album-page-header");
        pageHeader.setIcon(Material2OutlinedAL.COLLECTIONS_BOOKMARK);
        pageHeader.setTitle("作品集");
        pageHeader.setSubtitle("加载中...");

        description.getStyleClass().addAll("page-header-subtitle", "album-subtitle-description");
        description.setMinWidth(0);
        description.setMaxWidth(Double.MAX_VALUE);
        description.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox.setHgrow(description, Priority.ALWAYS);
        Tooltip descriptionTip = new Tooltip();
        descriptionTip.textProperty().bind(description.textProperty());
        description.setTooltip(descriptionTip);
        FontIcon subtitleSeparator = new FontIcon(Material2OutlinedAL.FIBER_MANUAL_RECORD);
        subtitleSeparator.getStyleClass().add("album-subtitle-separator");
        HBox subtitleExtra = new HBox(6, subtitleSeparator, description);
        subtitleExtra.setAlignment(Pos.CENTER_LEFT);
        subtitleExtra.setMinWidth(0);
        description.textProperty().addListener((observable, previous, value) ->
                pageHeader.setSubtitleExtra(value == null || value.isBlank() ? null : subtitleExtra));

        edit.setDisable(true);
        search.setPromptText("搜索集内作品");
        search.setPrefWidth(180);
        search.setMinWidth(140);
        search.setMaxWidth(200);
        WorkSortFilterButton sortButton = new WorkSortFilterButton(sortType, sqlOrder, sqlSubtext, sqlNsfw,
                sqlNoMate, showLocal, showNetwork);
        Button viewButton = createViewButton();
        Button syncButton = createSyncButton();
        HBox tools = new HBox(8, sortButton, viewButton, syncButton);
        tools.getStyleClass().addAll("grid-toolbar", "album-detail-tools");
        tools.setAlignment(Pos.CENTER_RIGHT);
        HBox actions = new HBox(8, search, tools, edit);
        actions.setAlignment(Pos.CENTER_RIGHT);
        pageHeader.setActions(actions);

        listView = new WorkListView(listModel, WorkListOptions.album(
                WorkCellFactory.adaptive(),
                new AlbumWorkCellContext(() -> albumId, () -> reloadList(false))));
        // 页头放在滚动区域内部，向下滚动时随作品列表一起移出视野
        listView.setScrollHeader(pageHeader);
        setCenter(listView);
        viewModeListener = (observable, oldValue, newValue) -> listView.rebuild();

        edit.setOnAction(event -> {
            if (current != null) {
                WorkAlbumDialogs.edit(current.album(), ignored -> reloadAll());
            }
        });

        reload.setOnFinished(event -> reloadList(true));
        ChangeListener<Object> filterListener = (observable, previous, value) -> scheduleReload();
        sortType.addListener(filterListener);
        sqlOrder.addListener(filterListener);
        sqlSubtext.addListener(filterListener);
        sqlNsfw.addListener(filterListener);
        sqlNoMate.addListener(filterListener);
        showLocal.addListener(filterListener);
        showNetwork.addListener(filterListener);
        search.textProperty().addListener((observable, previous, value) -> scheduleReload());

        sceneProperty().addListener((observable, previous, scene) -> {
            if (previous != null) {
                for (String topic : TOPICS) {
                    EventBus.unsubscribe(topic, observer);
                }
                reload.stop();
                metadataGeneration++;
                if (metadataTask != null) {
                    metadataTask.cancel();
                }
                if (observingViewMode) {
                    Config.setting.gridItemDetailModel.removeListener(viewModeListener);
                    observingViewMode = false;
                }
            }
            if (scene != null) {
                for (String topic : TOPICS) {
                    EventBus.subscribe(topic, observer);
                }
                Config.setting.gridItemDetailModel.addListener(viewModeListener);
                observingViewMode = true;
                loadMetadata();
                reloadList(!loadedOnce);
                loadedOnce = true;
            }
        });
    }

    private void scheduleReload() {
        if (getScene() != null) {
            reload.playFromStart();
        }
    }

    private void reloadList(boolean resetPage) {
        listModel.setSource(new AlbumWorkPageSource(albumService, albumId, search.getText(),
                sortType.get(), sqlOrder.get(), sqlSubtext.get(), sqlNsfw.get(), sqlNoMate.get(),
                showLocal.get(), showNetwork.get()), resetPage);
    }

    private void reloadAll() {
        loadMetadata();
        reloadList(true);
    }

    private void loadMetadata() {
        long request = ++metadataGeneration;
        if (metadataTask != null) {
            metadataTask.cancel();
        }
        metadataTask = AlbumTasks.run(() -> albumService.summaries(), summaries -> {
            if (request != metadataGeneration || getScene() == null) {
                return;
            }
            current = summaries.stream()
                    .filter(summary -> summary.album().id() == albumId)
                    .findFirst()
                    .orElse(null);
            if (current == null) {
                pageHeader.setTitle("作品集不存在");
                pageHeader.setSubtitle("");
                description.setText("");
                edit.setDisable(true);
                return;
            }
            pageHeader.setTitle(current.album().name());
            pageHeader.setSubtitle(current.workCount() + " 个作品");
            description.setText(current.album().description());
            edit.setDisable(false);
        }, WorkAlbumDialogs::error);
    }

    private void onNotification(String key) {
        if (getScene() == null) {
            return;
        }
        if (NotificationKey.WORK_ALBUM_CHANGED.equals(key)) {
            loadMetadata();
            listModel.reload();
        } else if (NotificationKey.MAIN_GRID_REFRESH.equals(key)) {
            listModel.reload();
        }
    }

    private Button createViewButton() {
        Button button = new Button();
        button.graphicProperty().bind(Bindings.when(Config.setting.gridItemDetailModel)
                .then(new FontIcon(MaterialDesignV.VIEW_COMFY))
                .otherwise(new FontIcon(MaterialDesignV.VIEW_MODULE)));
        button.setTooltip(new Tooltip("切换大图/小图"));
        button.getStyleClass().addAll("tool-btn", "tool-view-btn");
        button.setOnAction(event ->
                Config.setting.gridItemDetailModel.set(!Config.setting.gridItemDetailModel.get()));
        return button;
    }

    private Button createSyncButton() {
        Button button = new Button();
        button.setGraphic(new FontIcon(MaterialDesignC.CLOUD_SYNC));
        button.setTooltip(new Tooltip("智能同步"));
        button.getStyleClass().addAll("tool-btn", "tool-sync-btn");
        button.setOnAction(event -> syncCurrentPage());
        return button;
    }

    private void syncCurrentPage() {
        if (Config.setting.sourcesUrl.get() == null || !Config.setting.sourcesUrl.get().startsWith("https://")) {
            EventBus.message(new MessageInfo(MessageType.WARNING, "请先正确设置同步网址"));
            return;
        }
        var works = listModel.getItems();
        if (works.isEmpty()) {
            EventBus.message(new MessageInfo(MessageType.WARNING, "当前页面没有可同步的作品"));
            return;
        }
        String path = works.get(0).getParentPath();
        metadataSyncService.syncBatch(works, path);
    }

}
