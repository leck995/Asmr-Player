package cn.tealc.asmr.ui.album;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.album.WorkAlbumSummary;
import cn.tealc.asmr.mvvm.NotificationObserver;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.ui.component.EmptyState;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.navigation.AlbumDetailRoute;
import cn.tealc.asmr.ui.navigation.NavigationService;
import cn.tealc.asmr.utils.CoverUtils;
import cn.tealc.asmr.utils.CoverViewport;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.FXResourcesLoader;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 集册概览页，展示作品集卡片。
 * <p>
 * 点击作品集通过 {@link AlbumDetailRoute} 进入独立的详情页。
 */
public class WorkAlbumView extends BorderPane {
    private static final Image DEFAULT_COVER = new Image(FXResourcesLoader.image("cover-main.jpg"), 400, 300, true, true);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String[] TOPICS = {NotificationKey.WORK_ALBUM_CHANGED, NotificationKey.MAIN_GRID_REFRESH};
    private final WorkAlbumViewModel viewModel = new WorkAlbumViewModel();
    private final TilePane tiles = new TilePane(18, 18);
    private final ScrollPane scroll = new ScrollPane(tiles);
    private final StackPane overviewBody;
    private final PageHeader pageHeader = new PageHeader();
    private final Label status = new Label();
    private final EmptyState emptyState = new EmptyState();
    private final TextField search = new TextField();
    private final ObjectProperty<WorkAlbumViewModel.Sort> sort =
            new SimpleObjectProperty<>(WorkAlbumViewModel.Sort.CREATED);
    private final Button sortButton = createSortButton();
    private final Button create = new Button("创建作品集", new FontIcon(Material2OutlinedAL.ADD));
    private final Button refreshButton = iconButton(Material2OutlinedAL.CACHED, "刷新");
    private final PauseTransition reload = new PauseTransition(Duration.millis(150));
    private final NotificationObserver observer = (key, payload) -> Platform.runLater(() -> scheduleRefresh());
    private Task<?> overviewTask;
    private long generation;

    public WorkAlbumView() {
        getStyleClass().add("album-root");
        getStylesheets().addAll(CssLoader.getCss(CssLoader.workGrid), CssLoader.getCss(CssLoader.workAlbum));
        setPadding(new Insets(20, 24, 12, 24));
        setMinSize(0, 0);

        pageHeader.getStyleClass().add("album-page-header");
        pageHeader.setIcon(Material2OutlinedAL.COLLECTIONS_BOOKMARK);
        pageHeader.setTitle("集册");
        pageHeader.setSubtitle("");
        create.getStyleClass().add("accent");
        search.setPromptText("搜索作品集");
        search.setPrefWidth(180);
        search.setMinWidth(140);
        search.setMaxWidth(200);
        HBox actions = new HBox(8, search, sortButton, refreshButton, create);
        actions.setAlignment(Pos.CENTER_RIGHT);
        pageHeader.setActions(actions);
        setTop(pageHeader);

        tiles.setAlignment(Pos.TOP_LEFT);
        tiles.setPadding(new Insets(4, 4, 18, 4));
        tiles.setPrefTileWidth(240);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("album-scroll");
        scroll.setMinSize(0, 0);
        status.getStyleClass().add("album-muted");
        status.setMouseTransparent(true);
        status.setWrapText(true);
        emptyState.setIcon(Material2OutlinedAL.COLLECTIONS_BOOKMARK);
        emptyState.setDescription("创建作品集后，可以把多个作品归入同一集册。");
        emptyState.setMouseTransparent(true);
        emptyState.setVisible(false);
        emptyState.managedProperty().bind(emptyState.visibleProperty());
        overviewBody = new StackPane(scroll, status, emptyState);
        overviewBody.setPadding(new Insets(12, 0, 0, 0));
        setCenter(overviewBody);

        reload.setOnFinished(event -> refresh());
        create.setOnAction(event -> WorkAlbumDialogs.edit(null, ignored -> scheduleRefresh()));
        refreshButton.setOnAction(event -> scheduleRefresh());
        search.textProperty().addListener((observable, previous, value) -> scheduleRefresh());
        sceneProperty().addListener((observable, previous, scene) -> {
            if (previous != null) {
                for (String topic : TOPICS) {
                    EventBus.unsubscribe(topic, observer);
                }
                reload.stop();
                generation++;
                if (overviewTask != null) {
                    overviewTask.cancel();
                }
            }
            if (scene != null) {
                for (String topic : TOPICS) {
                    EventBus.subscribe(topic, observer);
                }
                scheduleRefresh();
            }
        });
    }

    private Button createSortButton() {
        Button button = iconButton(MaterialDesignS.SORT, "排序：" + sort.get());
        ContextMenu menu = new ContextMenu();
        ToggleGroup group = new ToggleGroup();
        for (WorkAlbumViewModel.Sort option : WorkAlbumViewModel.Sort.values()) {
            RadioButton radio = new RadioButton(option.toString());
            radio.setToggleGroup(group);
            radio.setSelected(option == sort.get());
            radio.setOnAction(event -> sort.set(option));
            menu.getItems().add(new CustomMenuItem(radio));
        }
        button.setOnAction(event -> menu.show(button, Side.BOTTOM, 0, 0));
        sort.addListener((observable, previous, value) -> {
            button.setTooltip(new Tooltip("排序：" + value));
            scheduleRefresh();
        });
        return button;
    }

    private void scheduleRefresh() {
        if (getScene() != null) {
            generation++;
            if (overviewTask != null) {
                overviewTask.cancel();
            }
            reload.playFromStart();
        }
    }

    private void refresh() {
        if (getScene() == null) {
            return;
        }
        refreshOverview();
    }

    private void refreshOverview() {
        long request = ++generation;
        if (overviewTask != null) {
            overviewTask.cancel();
        }
        status.setText("加载中...");
        status.setVisible(true);
        emptyState.setVisible(false);
        overviewTask = AlbumTasks.run(
                () -> viewModel.load(null, search.getText(), sort.get(), 1).albums(), albums -> {
                    if (request != generation || getScene() == null) {
                        return;
                    }
                    boolean searching = !search.getText().isBlank();
                    pageHeader.setSubtitle(albums.size() + " 个作品集");
                    tiles.getChildren().setAll(albums.stream().map(this::albumCard).toList());
                    status.setVisible(false);
                    emptyState.setIcon(searching
                            ? Material2OutlinedMZ.SEARCH_OFF : Material2OutlinedAL.COLLECTIONS_BOOKMARK);
                    emptyState.setTitle(searching ? "没有匹配的作品集" : "暂无作品集");
                    emptyState.setDescription(searching
                            ? "换个关键词试试。" : "创建作品集后，可以把多个作品归入同一集册。");
                    emptyState.setVisible(albums.isEmpty());
                }, failure -> {
                    if (request != generation || getScene() == null) {
                        return;
                    }
                    tiles.getChildren().clear();
                    emptyState.setVisible(false);
                    status.setText("加载失败，请重试");
                    status.setVisible(true);
                    WorkAlbumDialogs.error(failure);
                });
    }

    private VBox albumCard(WorkAlbumSummary summary) {
        ImageView cover = new ImageView(DEFAULT_COVER);
        cover.setFitWidth(240);
        cover.setFitHeight(180);
        CoverViewport.configure(cover);
        Rectangle clip = new Rectangle(240, 180);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        cover.setClip(clip);
        if (summary.coverWork() != null) {
            long request = generation;
            AlbumTasks.run(() -> CoverUtils.getCoverForWork(summary.coverWork(), "main", "cover-main", 400, 300, true, DEFAULT_COVER),
                    image -> {
                        if (request == generation && getScene() != null) {
                            cover.setImage(image);
                        }
                    }, ignored -> {
                    });
        }
        Label name = new Label(summary.album().name());
        name.getStyleClass().add("album-card-name");
        name.setMaxWidth(Double.MAX_VALUE);
        name.setTooltip(new Tooltip(summary.album().name()));
        Label count = new Label(summary.workCount() + " 个作品");
        count.getStyleClass().add("album-count");
        Label excerpt = new Label(summary.album().description().replaceAll("\\s+", " "));
        excerpt.getStyleClass().add("album-muted");
        excerpt.setMaxWidth(Double.MAX_VALUE);
        excerpt.setMinHeight(20);
        Label created = new Label("创建 " + format(summary.album().createdAt()));
        Label added = new Label("最后添加 " + format(summary.album().lastAddedAt()));
        created.getStyleClass().add("album-card-time");
        added.getStyleClass().add("album-card-time");
        Button modify = iconButton(Material2OutlinedAL.EDIT, "修改作品集");
        Button delete = iconButton(Material2OutlinedAL.DELETE_OUTLINE, "删除作品集");
        delete.getStyleClass().add("album-danger");
        modify.setOnAction(event -> WorkAlbumDialogs.edit(summary.album(), ignored -> scheduleRefresh()));
        delete.setOnAction(event -> WorkAlbumDialogs.delete(summary.album()));
        modify.setOnMouseClicked(event -> event.consume());
        delete.setOnMouseClicked(event -> event.consume());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(6, count, spacer, modify, delete);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox information = new VBox(6, name, excerpt, created, added, actions);
        information.setPadding(new Insets(10, 12, 10, 12));
        VBox card = new VBox(cover, information);
        card.getStyleClass().add("album-card");
        card.setMinWidth(240);
        card.setPrefWidth(240);
        card.setMaxWidth(240);
        card.setFocusTraversable(true);
        card.setAccessibleText(summary.album().name());
        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                openAlbum(summary);
            }
        });
        card.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                openAlbum(summary);
                event.consume();
            }
        });
        return card;
    }

    private void openAlbum(WorkAlbumSummary summary) {
        DI.get(NavigationService.class).push(new AlbumDetailRoute(summary.album().id()));
    }

    static Button iconButton(Ikon icon, String tooltip) {
        Button button = new Button(null, new FontIcon(icon));
        button.getStyleClass().addAll("flat", "album-icon-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        button.setMinSize(30, 30);
        button.setPrefSize(30, 30);
        button.setMaxSize(30, 30);
        return button;
    }

    private static String format(LocalDateTime value) {
        return value == null ? "尚未添加" : TIME.format(value);
    }
}
