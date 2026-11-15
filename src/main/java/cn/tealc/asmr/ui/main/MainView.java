package cn.tealc.asmr.ui.main;

import atlantafx.base.controls.ModalPane;
import cn.tealc.asmr.Main;
import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.task.background.TaskManager;
import cn.tealc.asmr.ui.dialog.TaskListDialog;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.ui.dialog.SponsorDialog;
import cn.tealc.asmr.ui.guide.GuideDialog;
import cn.tealc.asmr.factory.ViewFactory;
import cn.tealc.asmr.model.AnywhereDoor;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.AnywhereDoorService;
import cn.tealc.asmr.task.CheckVersionTask;
import cn.tealc.asmr.ui.collection.CollectionDetailView;
import cn.tealc.asmr.ui.collection.CollectionView;
import cn.tealc.asmr.ui.collection.CollectionViewModel;
import cn.tealc.asmr.ui.album.WorkAlbumDetailView;
import cn.tealc.asmr.ui.album.WorkAlbumView;
import cn.tealc.asmr.ui.navigation.AlbumDetailRoute;
import cn.tealc.asmr.ui.navigation.AlbumsRoute;
import cn.tealc.asmr.ui.navigation.CategoriesRoute;
import cn.tealc.asmr.ui.navigation.CollectionDetailRoute;
import cn.tealc.asmr.ui.navigation.MediaLibraryRoute;
import cn.tealc.asmr.ui.navigation.NavigationService;
import cn.tealc.asmr.ui.navigation.PlaylistsRoute;
import cn.tealc.asmr.ui.navigation.Route;
import cn.tealc.asmr.ui.navigation.RouteView;
import cn.tealc.asmr.ui.navigation.SettingsRoute;
import cn.tealc.asmr.ui.navigation.ToolsRoute;
import cn.tealc.asmr.ui.navigation.WorkSource;
import cn.tealc.asmr.ui.navigation.WorksRoute;
import cn.tealc.asmr.ui.galgame.GalgameDetailView;
import cn.tealc.asmr.ui.galgame.GalgameDetailViewModel;
import cn.tealc.asmr.ui.player.PlaylistView;
import cn.tealc.asmr.ui.player.PlayerDetailView;
import cn.tealc.asmr.ui.player.PlayerDetailViewModel;
import cn.tealc.asmr.ui.setting.SettingView;
import cn.tealc.asmr.ui.tray.TrayManager;
import cn.tealc.asmr.utils.*;
import cn.tealc.asmr.ui.component.OverlayLayer;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;
import com.jfoenix.controls.JFXDialog;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.mvvm.FluentViewLoader;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.mvvm.JavaView;
import cn.tealc.asmr.mvvm.ViewTuple;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.effect.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.materialdesign2.MaterialDesignB;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * 主窗口根视图：内嵌标题栏 + 左侧导航 + 内容区 + 底部播放面板，另叠消息层、覆盖层与模态层。
 * <p>
 * 页面切换走 {@link NavigationService}（{@link #createRouteView} 把路由翻译成视图），
 * 播放页等整屏覆盖层直接压在 {@code root} 上，由 {@code TO_PLAY}/{@code MAIN_ROOT_REMOVE}
 * 事件驱动进出。全局事件订阅集中在 {@link #initEvent()}。
 * <p>
 * 未启用启动密码（{@code pKeyModel}）时以“全部作品”作为初始根路由，启用时本类不预打开；
 * {@code autoCheckVersion} 开启时在守护线程上跑一次版本检查。
 * <p>
 * 静态字段 {@code root} 是整屏页面的挂载栈（标题栏在最下，播放页与播放队列弹窗依次叠加），
 * 同时作为 {@link OverlayLayer} 的宿主。
 *
 * @author Leck
 */
public class MainView extends AnchorPane implements JavaView<MainViewModel>, Initializable {
    public static StackPane root;
    @InjectViewModel
    private MainViewModel viewModel;

    private VBox navPane;
    private VBox anywherePane;
    private StackPane content;

    private VBox messagePane;

    private HeaderBar headerBar;

    private ModalPane modalPane;

    private final NavigationService navigationService = DI.get(NavigationService.class);

    public MainView() {
        getStylesheets().add(CssLoader.getCss(CssLoader.main));
    }

    /**
     * 依次搭建主根栈（含标题栏）、消息层、覆盖层、模态层四层，然后打开初始根路由、
     * 按需启动版本检查守护线程，最后注册全局事件订阅。
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        BorderPane headerPane = createHeaderBar();
        root = new StackPane(headerPane);
        root.getStyleClass().add("root-pane");
        getChildren().add(root);
        AnchorPaneUtils.setPosition(root, 0.0, 0.0, 0.0, 0.0);

        messagePane = new VBox();
        messagePane.setPadding(new Insets(5.0));
        messagePane.setSpacing(5.0);
        getChildren().add(messagePane);
        AnchorPaneUtils.setPosition(messagePane, 60.0, 0.0, null, null);

        OverlayLayer overlayLayer = new OverlayLayer(root);
        getChildren().add(overlayLayer);
        AnchorPaneUtils.setPosition(overlayLayer, 0.0, 0.0, 0.0, 0.0);

        modalPane = new ModalPane();
        getChildren().add(modalPane);
        AnchorPaneUtils.setPosition(modalPane, 0.0, 0.0, 0.0, 0.0);

        if (!Config.setting.pKeyModel.get()) {
            navigationService.openRoot(new WorksRoute(WorkSource.all()));
        }

        if (Config.setting.autoCheckVersion.get()) {

            CheckVersionTask checkVersionTask = new CheckVersionTask();
            Thread th = new Thread(checkVersionTask);
            th.setDaemon(true);
            th.start();

        }
        initEvent();

    }


    /**
     * 注册主窗口订阅的全局事件。
     * <p>
     * {@code TO_PLAY} 无参时压入播放页（按 {@code galgameModel} 选择传统页或 Galgame 页），
     * 带参数 1 时切换为 Galgame 页、其他值切回传统页；{@code EXIT} 弹确认框，
     * 托盘可用时额外提供“隐藏到托盘”，否则给“最小化”；{@code MAIN_ROOT_REMOVE} 关闭最上层页面；
     * {@code MAIN_PLAY_LIST} 的布尔参数决定播放队列面板是否用 Galgame 样式；
     * {@code CHANGE_SKIN} 清除后重挂主样式表。
     */
    private void initEvent() {
        //切换到播放界面
        EventBus.subscribe(NotificationKey.TO_PLAY, (s, objects) -> {
            if (objects.length == 0) { //无参数的时候，说明是从dashboard传过来的事件
                if (Config.setting.galgameModel.get()) {
                    ViewTuple<GalgameDetailView, GalgameDetailViewModel> load = FluentViewLoader.javaView(GalgameDetailView.class).load();
                    addRootPane(load.getView());
                } else {
                    ViewTuple<PlayerDetailView, PlayerDetailViewModel> view = ViewFactory.playerDetailView();
                    addRootPane(view.getView());
                }
            } else { //有参数的时候，说明是播放界面传过来的事件，用于切换模式，1是galgame,非1是传统播放界面
                if ((int) objects[0] == 1) {
                    ViewTuple<GalgameDetailView, GalgameDetailViewModel> view = FluentViewLoader.javaView(GalgameDetailView.class).load();
                    replaceRootPane(view.getView());
                } else {
                    ViewTuple<PlayerDetailView, PlayerDetailViewModel> view = ViewFactory.playerDetailView();
                    replaceRootPane(view.getView());
                }
            }
        });

        //退出
        EventBus.subscribe(NotificationKey.EXIT, (s, objects) -> {
            String backgroundAction = TrayManager.isInstalled() ? "隐藏到托盘" : "最小化";
            Alerts.warning()
                    .title("提醒")
                    .message("确认退出吗？")
                    .button(backgroundAction, handle -> Main.hide())
                    .ok("确定", handle -> Main.exit())
                    .cancel()
                    .show();
        });


        //移除root的上层pane
        EventBus.subscribe(NotificationKey.MAIN_ROOT_REMOVE, (s, objects) -> {
            removeRootPane();
        });


        //显示播放列表
        EventBus.subscribe(NotificationKey.MAIN_PLAY_LIST, (s, objects) -> {
            createPlaylistPane((boolean) objects[0]);
        });

        //切换皮肤
        EventBus.subscribe(NotificationKey.CHANGE_SKIN, (s, objects) -> {
            getStylesheets().clear();
            getStylesheets().add(CssLoader.getCss(CssLoader.main));
        });
    }

    /**
     * 创建主窗口的 HeaderBar 及其内容容器。
     * 使用 JavaFX 26 原生 HeaderBar + StageStyle.EXTENDED 接管标题区，
     * 左侧放标题，右侧放功能按钮（搜索/媒体库/皮肤/工具/设置）与系统按钮（最小化/最大化/关闭）。
     *
     * @return 包含 HeaderBar（top）与主内容（center）的 BorderPane
     */
    private BorderPane createHeaderBar() {
        headerBar = new HeaderBar();
        headerBar.getStyleClass().add("headbar");

        // 左侧：全局前进/后退 + 图标 + 标题
        Region icon = new Region();
        icon.getStyleClass().add("icon");
        Text titleLabel = new Text("AsmrPlayer");
        titleLabel.getStyleClass().add("title");

        Button backBtn = new Button(null, new FontIcon(MaterialDesignC.CHEVRON_LEFT));
        backBtn.getStyleClass().add("header-nav-btn");
        backBtn.setTooltip(new Tooltip("后退"));
        backBtn.disableProperty().bind(navigationService.canGoBackProperty().not());
        backBtn.setOnAction(event -> navigationService.back());

        Button forwardBtn = new Button(null, new FontIcon(MaterialDesignC.CHEVRON_RIGHT));
        forwardBtn.getStyleClass().add("header-nav-btn");
        forwardBtn.setTooltip(new Tooltip("前进"));
        forwardBtn.disableProperty().bind(navigationService.canGoForwardProperty().not());
        forwardBtn.setOnAction(event -> navigationService.forward());

        HBox navBox = new HBox(2, backBtn, forwardBtn);
        navBox.getStyleClass().add("header-nav");
        navBox.setAlignment(Pos.CENTER_LEFT);
        navBox.setPickOnBounds(false);

        HBox navHost = new HBox(navBox);
        navHost.setAlignment(Pos.CENTER_LEFT);
        navHost.setPadding(new Insets(0, 0, 0, 20));
        navHost.setPickOnBounds(false);
        headerBar.setCenter(navHost);
        HeaderBar.setAlignment(navHost, Pos.CENTER_LEFT);

        HBox leadingBox = new HBox(icon, titleLabel);
        leadingBox.getStyleClass().add("leading");
        headerBar.setLeft(leadingBox);

        // 右侧功能按钮容器
        HBox trailingBox = new HBox();
        trailingBox.getStyleClass().add("trailing");

        ToggleButton skinBtn = new ToggleButton();
        // 主题切换按钮：选中（亮色）显示太阳，未选中（暗色）显示月亮
        skinBtn.getStyleClass().add("skin-btn");
        skinBtn.setSelected(Config.setting.themeModel.get());
        skinBtn.setGraphic(new FontIcon(skinBtn.isSelected()
                ? MaterialDesignW.WHITE_BALANCE_SUNNY
                : MaterialDesignW.WEATHER_NIGHT));
        skinBtn.selectedProperty().addListener((obs, wasSelected, isSelected) ->
                skinBtn.setGraphic(new FontIcon(isSelected
                        ? MaterialDesignW.WHITE_BALANCE_SUNNY
                        : MaterialDesignW.WEATHER_NIGHT)));
        trailingBox.getChildren().add(0, skinBtn);
        skinBtn.setOnAction(actionEvent -> {
            skinBtn.setDisable(true);
            Scene scene = Main.window.getScene();
            ImageView skinImageView = new ImageView();
            skinImageView.setFitWidth(scene.getWidth());
            skinImageView.setFitHeight(scene.getHeight());
            skinImageView.setPreserveRatio(false);
            skinImageView.setVisible(false);
            skinImageView.setImage(scene.snapshot(null));
            root.getChildren().add(skinImageView);
            Config.setting.themeModel.set(skinBtn.isSelected());
            EventBus.publish("CHANGE_SKIN");
            // 以 skinBtn 中心为圆心，截图从按钮位置向外收缩消失
            Bounds btnBounds = skinBtn.localToScene(skinBtn.getBoundsInLocal());
            double cx = btnBounds.getCenterX();
            double cy = btnBounds.getCenterY();
            double maxRadius = Math.hypot(
                    Math.max(cx, scene.getWidth() - cx),
                    Math.max(cy, scene.getHeight() - cy));
            Circle circle1 = new Circle(cx, cy, maxRadius);
            skinImageView.setClip(circle1);
            skinImageView.setVisible(true);
            Timeline tl = new Timeline(new KeyFrame(Duration.millis(600), new KeyValue(circle1.radiusProperty(), 0),
                    new KeyValue(skinBtn.rotateProperty(), 360)));
            tl.setOnFinished(event1 -> {
                skinImageView.setClip(null);
                root.getChildren().remove(skinImageView);
                skinBtn.setRotate(0);
                skinBtn.setDisable(false);

            });
            tl.play();
        });

        // —— 赞助按钮：位于主题切换按钮右侧，弹窗展示赞助说明与收款二维码 ——
        Button sponsorBtn = new Button(null, new FontIcon(MaterialDesignC.COFFEE));
        sponsorBtn.getStyleClass().add("sponsor-btn");
        sponsorBtn.setTooltip(new Tooltip("赞助"));
        sponsorBtn.setOnAction(event -> SponsorDialog.show());
        trailingBox.getChildren().add(trailingBox.getChildren().indexOf(skinBtn) + 1, sponsorBtn);

        javafx.scene.control.TextField textField = new TextField();
        textField.setFocusTraversable(false);
        textField.textProperty().bindBidirectional(viewModel.searchValueProperty());

        EventHandler<ActionEvent> handler = actionEvent -> {
            String temp = textField.getText();
            if (temp != null && temp.length() > 0) {
                String workId = WorkCodeUtils.extract(temp);
                if (workId != null) {
                    viewModel.search(workId, AsmrKeyType.SEARCH);
                } else {
                    viewModel.search(temp, AsmrKeyType.SEARCH);
                }
            }
        };
        textField.setOnAction(handler);
        Button searchBtn = new Button();
        searchBtn.setGraphic(new FontIcon(Material2OutlinedMZ.SEARCH));
        searchBtn.setOnAction(handler);

        HBox searchPane = new HBox(textField, searchBtn);
        searchPane.getStyleClass().add("search-pane");
        searchPane.setAlignment(Pos.CENTER_LEFT);
        trailingBox.getChildren().add(0, searchPane);

        // —— 后台任务管理按钮：位于搜索框与主题切换按钮之间 ——
        TaskManager taskManager = DI.get(TaskManager.class);
        Button taskCenterBtn = new Button();
        taskCenterBtn.setGraphic(new FontIcon(Material2OutlinedMZ.PENDING_ACTIONS));
        taskCenterBtn.getStyleClass().add("task-center-btn");
        taskCenterBtn.setTooltip(new Tooltip("后台任务"));
        // 后台任务入口始终显示
        taskCenterBtn.setVisible(true);
        taskCenterBtn.setManaged(true);
        // 计数徽标
        Label taskBadge = new Label();
        taskBadge.getStyleClass().add("task-badge");
        taskBadge.textProperty().bind(taskManager.runningCountProperty().asString());
        taskBadge.visibleProperty().bind(taskManager.runningCountProperty().greaterThan(0));
        taskBadge.setMouseTransparent(true);
        StackPane taskBtnPane = new StackPane(taskCenterBtn, taskBadge);
        StackPane.setAlignment(taskBadge, Pos.TOP_RIGHT);
        taskCenterBtn.setOnAction(e -> new TaskListDialog(taskManager).show());
        trailingBox.getChildren().add(1, taskBtnPane);

        // —— 使用教程：位于后台任务按钮右侧 ——
        Button guideBtn = new Button(null, new FontIcon(Material2OutlinedAL.HELP_OUTLINE));
        guideBtn.setTooltip(new Tooltip("使用教程"));
        guideBtn.setOnAction(e -> Dialogs.show(new GuideDialog()));
        trailingBox.getChildren().add(trailingBox.getChildren().indexOf(taskBtnPane) + 1, guideBtn);

        // 系统按钮：最小化/最大化/关闭
        Button minBtn = new Button(null, new FontIcon(Material2OutlinedMZ.MINUS));
        Button maxBtn = new Button(null, new FontIcon());
        Button closeBtn = new Button(null, new FontIcon(Material2OutlinedAL.CLOSE));
        HeaderBar.setButtonType(minBtn, HeaderButtonType.ICONIFY);
        HeaderBar.setButtonType(maxBtn, HeaderButtonType.MAXIMIZE);
        closeBtn.getStyleClass().add("close-btn");
        maxBtn.getStyleClass().add("max-btn");
        closeBtn.setOnAction(e -> EventBus.publish("EXIT"));
        HBox systemBox = new HBox(minBtn, maxBtn, closeBtn);
        systemBox.getStyleClass().add("system-func");
        trailingBox.getChildren().add(systemBox);

        headerBar.setRight(trailingBox);

        // 主内容
        BorderPane mainContent = (BorderPane) createMain();

        // 隐藏 EXTENDED 默认系统按钮区，由自定义 HeaderBar 接管；
        // 监听最大化状态切换图标（全屏↔还原）
        Platform.runLater(() -> {
            Stage window = (Stage) root.getScene().getWindow();
            HeaderBar.setPrefButtonHeight(window, 0);
            window.maximizedProperty().addListener((obs, aBoolean, t1) -> {
                if (t1) {
                    maxBtn.getStyleClass().add("full-exit");
                } else {
                    maxBtn.getStyleClass().remove("full-exit");
                }
            });
        });

        BorderPane border = new BorderPane();
        border.setTop(headerBar);
        border.setCenter(mainContent);
        return border;
    }

    /**
     * 组装主内容区：左侧导航 + 由导航服务托管的内容栈 + 底部播放面板。
     * <p>
     * 内容栈以“全部作品”页实例作为根路由的复用视图，其余路由各自新建页面。
     */
    private Parent createMain() {
        BorderPane borderPane = new BorderPane();
        createLeftTiledPane();
        borderPane.setLeft(navPane);
        content = new StackPane();
        ViewTuple<WorkGridView, WorkGridViewModel> viewTuple = FluentViewLoader.javaView(WorkGridView.class).load();
        WorkGridView workGrid = (WorkGridView) viewTuple.getView();
        navigationService.attach(content, route -> createRouteView(route, workGrid));
        borderPane.setCenter(content);
        borderPane.setBottom(createDashboard());
        return borderPane;
    }

    /**
     * 根据路由创建页面实例。
     * 全部作品复用主网格；收藏、搜索结果和分类结果各自创建独立的作品列表页面。
     */
    private RouteView createRouteView(Route route, WorkGridView workGrid) {
        if (route instanceof WorksRoute works) {
            if (works.source().type() == AsmrKeyType.ALL) {
                return worksPage(works, workGrid);
            }
            WorkGridView page = (WorkGridView) FluentViewLoader.javaView(WorkGridView.class).load().getView();
            return worksPage(works, page);
        }
        if (route instanceof CategoriesRoute) {
            return pageView(FluentViewLoader.javaView(CategoriesView.class).load().getView());
        }
        if (route instanceof PlaylistsRoute) {
            ViewTuple<CollectionView, CollectionViewModel> tuple =
                    FluentViewLoader.fxmlView(CollectionView.class).load();
            CollectionView page = tuple.getCodeBehind();
            return new RouteView() {
                private boolean initialized;

                @Override
                public Node node() {
                    return tuple.getView();
                }

                @Override
                public void onShow() {
                    if (initialized) {
                        page.refresh();
                    } else {
                        initialized = true;
                    }
                }
            };
        }
        if (route instanceof CollectionDetailRoute detail) {
            return pageView(new CollectionDetailView(detail.sheetId()));
        }
        if (route instanceof AlbumsRoute) {
            return pageView(new WorkAlbumView());
        }
        if (route instanceof AlbumDetailRoute detail) {
            return pageView(new WorkAlbumDetailView(detail.albumId()));
        }
        if (route instanceof MediaLibraryRoute) {
            return pageView(ViewFactory.mediaLibraryView().getView());
        }
        if (route instanceof ToolsRoute) {
            return pageView(ViewFactory.toolView().getView());
        }
        if (route instanceof SettingsRoute) {
            return pageView(FluentViewLoader.fxmlView(SettingView.class).load().getView());
        }
        throw new IllegalArgumentException("未知路由: " + route);
    }

    /**
     * 作品列表页路由：首次显示时应用数据来源，之后重新显示只刷新当前页。
     */
    private static RouteView worksPage(WorksRoute route, WorkGridView page) {
        return new RouteView() {
            private boolean initialized;

            @Override
            public Node node() {
                return page;
            }

            @Override
            public void onShow() {
                if (!initialized) {
                    initialized = true;
                    page.applySource(route.source());
                } else {
                    page.reload();
                }
            }
        };
    }

    private static RouteView pageView(Node node) {
        return () -> node;
    }

    /**
     * 创建左侧导航栏：全部、分类、收藏、歌单、集册、管理、设置、工具箱八个根入口，
     * 下方是可折叠的快捷入口（任意门）面板。
     * <p>
     * 由 {@link #createMain()} 调用。按钮只做 {@code push} 路由，选中态由
     * {@link #syncNavigationSelection} 依据当前路由回写；“快捷入口”按钮不加入
     * {@code ToggleGroup}，仅切换面板显隐。快捷入口列表带变更监听，增删后自动重建。
     */
    private void createLeftTiledPane() {
        navPane = new VBox();
        navPane.getStyleClass().add("main-nav");
        navPane.setMinWidth(Region.USE_PREF_SIZE);
        navPane.setMaxWidth(Region.USE_PREF_SIZE);
        navPane.setMinHeight(0);
        ToggleGroup toggleGroup = new ToggleGroup();
        ToggleButton allBtn = new ToggleButton("全部");
        ToggleButton categoryBtn = new ToggleButton("分类");
        ToggleButton starBtn = new ToggleButton("收藏");
        ToggleButton collectionsBtn = new ToggleButton("歌单");
        allBtn.getStyleClass().add("grid-left-all-btn");
        allBtn.setPrefWidth(250);
        allBtn.setGraphic(new FontIcon(Material2OutlinedAL.APPS));
        Tooltip allTooltip = new Tooltip("全部");
        allTooltip.setShowDelay(Duration.millis(500));
        allBtn.setTooltip(allTooltip);
        allBtn.setToggleGroup(toggleGroup);
        allBtn.setAlignment(Pos.CENTER_LEFT);
        allBtn.setEllipsisString("");
        allBtn.setOnAction(actionEvent -> {
            allBtn.setSelected(true);
            navigationService.push(new WorksRoute(WorkSource.all()));
        });

        starBtn.getStyleClass().add("grid-left-star-btn");
        starBtn.setPrefWidth(250);
        starBtn.setGraphic(new FontIcon(Material2OutlinedAL.FAVORITE_BORDER));
        Tooltip starTooltip = new Tooltip("收藏");
        starTooltip.setShowDelay(Duration.millis(500));
        starBtn.setTooltip(starTooltip);
        starBtn.setToggleGroup(toggleGroup);
        starBtn.setAlignment(Pos.CENTER_LEFT);
        starBtn.setEllipsisString("");
        starBtn.setOnAction(actionEvent -> {
            starBtn.setSelected(true);
            navigationService.push(new WorksRoute(WorkSource.favorites()));
        });

        collectionsBtn.getStyleClass().add("grid-left-collections-btn");
        collectionsBtn.setPrefWidth(250);
        collectionsBtn.setGraphic(new FontIcon(Material2OutlinedAL.LIST));
        Tooltip collectionsTip = new Tooltip("歌单");
        collectionsTip.setShowDelay(Duration.millis(500));
        collectionsBtn.setTooltip(collectionsTip);
        collectionsBtn.setToggleGroup(toggleGroup);
        collectionsBtn.setAlignment(Pos.CENTER_LEFT);
        collectionsBtn.setEllipsisString("");
        collectionsBtn.setOnAction(actionEvent -> {
            collectionsBtn.setSelected(true);
            navigationService.push(new PlaylistsRoute());
        });

        ToggleButton albumsBtn = new ToggleButton("集册");
        albumsBtn.getStyleClass().add("grid-left-albums-btn");
        albumsBtn.setPrefWidth(250);
        albumsBtn.setGraphic(new FontIcon(Material2OutlinedAL.COLLECTIONS_BOOKMARK));
        albumsBtn.setTooltip(new Tooltip("集册"));
        albumsBtn.setToggleGroup(toggleGroup);
        albumsBtn.setAlignment(Pos.CENTER_LEFT);
        albumsBtn.setEllipsisString("");
        albumsBtn.setOnAction(event -> {
            albumsBtn.setSelected(true);
            navigationService.push(new AlbumsRoute());
        });


        categoryBtn.getStyleClass().add("grid-left-category-btn");
        categoryBtn.setPrefWidth(250);
        categoryBtn.setGraphic(new FontIcon(Material2OutlinedAL.CATEGORY));
        Tooltip categoryTooltip = new Tooltip("分类");
        categoryTooltip.setShowDelay(Duration.millis(500));
        categoryBtn.setTooltip(categoryTooltip);
        categoryBtn.setToggleGroup(toggleGroup);
        categoryBtn.setAlignment(Pos.CENTER_LEFT);
        categoryBtn.setEllipsisString("");
        categoryBtn.setOnAction(actionEvent -> {
            categoryBtn.setSelected(true);
            navigationService.push(new CategoriesRoute());
        });


        // 管理按钮：加载 MediaLibraryView 到内容区。
        ToggleButton mediaLibraryBtn = new ToggleButton("管理");
        mediaLibraryBtn.getStyleClass().add("grid-left-media-library-btn");
        mediaLibraryBtn.setPrefWidth(250);
        mediaLibraryBtn.setGraphic(new FontIcon(MaterialDesignB.BOOKSHELF));
        Tooltip mediaLibraryTip = new Tooltip("管理媒体库、存储源与快捷入口");
        mediaLibraryTip.setShowDelay(Duration.millis(500));
        mediaLibraryBtn.setTooltip(mediaLibraryTip);
        mediaLibraryBtn.setToggleGroup(toggleGroup);
        mediaLibraryBtn.setAlignment(Pos.CENTER_LEFT);
        mediaLibraryBtn.setEllipsisString("");
        mediaLibraryBtn.setOnAction(actionEvent -> {
            mediaLibraryBtn.setSelected(true);
            navigationService.push(new MediaLibraryRoute());
        });

        ToggleButton toolsBtn = new ToggleButton("工具箱");
        toolsBtn.getStyleClass().add("grid-left-tools-btn");
        toolsBtn.setPrefWidth(250);
        toolsBtn.setGraphic(new FontIcon(MaterialDesignB.BRIEFCASE_VARIANT_OUTLINE));
        Tooltip toolsTip = new Tooltip("重命名、重复检测与数据清理");
        toolsTip.setShowDelay(Duration.millis(500));
        toolsBtn.setTooltip(toolsTip);
        toolsBtn.setToggleGroup(toggleGroup);
        toolsBtn.setAlignment(Pos.CENTER_LEFT);
        toolsBtn.setEllipsisString("");
        toolsBtn.setOnAction(actionEvent -> {
            toolsBtn.setSelected(true);
            navigationService.push(new ToolsRoute());
        });

        ToggleButton settingsBtn = new ToggleButton("设置");
        settingsBtn.getStyleClass().add("grid-left-settings-btn");
        settingsBtn.setPrefWidth(250);
        settingsBtn.setGraphic(new FontIcon(Material2OutlinedMZ.SETTINGS));
        Tooltip settingsTip = new Tooltip("设置");
        settingsTip.setShowDelay(Duration.millis(500));
        settingsBtn.setTooltip(settingsTip);
        settingsBtn.setToggleGroup(toggleGroup);
        settingsBtn.setAlignment(Pos.CENTER_LEFT);
        settingsBtn.setEllipsisString("");
        settingsBtn.setOnAction(actionEvent -> {
            settingsBtn.setSelected(true);
            navigationService.push(new SettingsRoute());
        });

        anywherePane = new VBox();
        anywherePane.setMinWidth(0);
        ScrollPane shortcutScroll = new ScrollPane(anywherePane);
        shortcutScroll.getStyleClass().add("main-nav-shortcuts-scroll");
        shortcutScroll.setFitToWidth(true);
        shortcutScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        shortcutScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // 快捷入口只使用剩余高度，不将内容高度传递给主窗口。
        shortcutScroll.setMinSize(0, 0);
        shortcutScroll.setPrefHeight(0);
        VBox.setVgrow(shortcutScroll, Priority.ALWAYS);
        shortcutScroll.visibleProperty().bind(anywherePane.visibleProperty());
        shortcutScroll.managedProperty().bind(anywherePane.managedProperty());
        ToggleButton folderBtn = new ToggleButton("快捷入口");
        folderBtn.setGraphic(new FontIcon(Material2OutlinedAL.ARROW_FORWARD));
        folderBtn.setPrefWidth(250);
        folderBtn.setEllipsisString("");
        folderBtn.getStyleClass().add("grid-left-parent-btn");
        folderBtn.setAlignment(Pos.CENTER_LEFT);
        folderBtn.setSelected(true);
        folderBtn.setOnAction(actionEvent -> {
            toggleGroup.selectToggle(null);
            anywherePane.setVisible(!anywherePane.isVisible());
            anywherePane.setManaged(anywherePane.isVisible());
        });

        Separator separator = new Separator(Orientation.HORIZONTAL);

        navPane.getChildren().addAll(allBtn, starBtn, categoryBtn, collectionsBtn, albumsBtn, mediaLibraryBtn, settingsBtn, toolsBtn, separator, folderBtn, shortcutScroll);

        navigationService.currentRouteProperty().addListener((observable, oldRoute, route) ->
                syncNavigationSelection(route, allBtn, starBtn, collectionsBtn, albumsBtn, categoryBtn, mediaLibraryBtn, toolsBtn, settingsBtn));

        var shortcuts = DI.get(AnywhereDoorService.class).getEntries();
        refreshAnywhereDoors(shortcuts, toggleGroup);
        shortcuts.addListener((ListChangeListener<AnywhereDoor>) change ->
                refreshAnywhereDoors(shortcuts, toggleGroup));
    }

    /**
     * 路由变化时同步左侧根导航的选中状态。
     */
    private static void syncNavigationSelection(Route route, ToggleButton allBtn, ToggleButton starBtn,
                                                ToggleButton collectionsBtn, ToggleButton albumsBtn,
                                                ToggleButton categoryBtn, ToggleButton mediaLibraryBtn,
                                                ToggleButton toolsBtn, ToggleButton settingsBtn) {
        ToggleButton target = null;
        if (route instanceof WorksRoute works) {
            if (works.source().type() == AsmrKeyType.ALL) {
                target = allBtn;
            } else if (works.source().type() == AsmrKeyType.FAVORITE) {
                target = starBtn;
            }
        } else if (route instanceof CategoriesRoute) {
            target = categoryBtn;
        } else if (route instanceof PlaylistsRoute || route instanceof CollectionDetailRoute) {
            target = collectionsBtn;
        } else if (route instanceof AlbumsRoute) {
            target = albumsBtn;
        } else if (route instanceof MediaLibraryRoute) {
            target = mediaLibraryBtn;
        } else if (route instanceof ToolsRoute) {
            target = toolsBtn;
        } else if (route instanceof SettingsRoute) {
            target = settingsBtn;
        }
        if (target != null) {
            target.setSelected(true);
        }
    }

    /**
     * 重建快捷入口面板。
     * <p>
     * 先把旧按钮摘出 {@code ToggleGroup} 并记下选中项 id，重建后按 id 恢复选中，
     * 使列表增删不会丢选中态。
     *
     * @param shortcuts 当前快捷入口列表（可观察，条目增删后本方法会被再次调用）
     * @param group     与根入口共享的选中分组
     */
    private void refreshAnywhereDoors(List<AnywhereDoor> shortcuts, ToggleGroup group) {
        Object selectedId = null;
        for (Node child : anywherePane.getChildren()) {
            ToggleButton button = (ToggleButton) child;
            if (button.isSelected()) selectedId = button.getUserData();
            button.setToggleGroup(null);
        }
        anywherePane.getChildren().clear();
        for (AnywhereDoor shortcut : shortcuts) {
            ToggleButton button = createToggleButton(shortcut, group);
            anywherePane.getChildren().add(button);
            if (Objects.equals(selectedId, shortcut.getId())) button.setSelected(true);
        }
    }


    /**
     * 为一条快捷入口生成左侧导航按钮。
     * <p>
     * 按入口类型追加样式类区分颜色，tooltip 展示“名称 + 键”，点击后以
     * {@code (类型, 键, 媒体库)} 构造 {@link WorkSource} 跳转作品列表；
     * 入口 id 存在 {@code userData} 里，供 {@link #refreshAnywhereDoors} 恢复选中态。
     * 除 {@link #createLeftTiledPane()} 首次装配外，也由快捷入口列表变更时的
     * {@link #refreshAnywhereDoors} 调用。
     *
     * @param anywhereDoor  快捷入口数据（类型、键、显示名、所属媒体库）
     * @param toggleGroup   与其他根入口共享的分组，保证同时只选中一个
     * @return 可直接加入导航栏的按钮
     */
    private ToggleButton createToggleButton(AnywhereDoor anywhereDoor, ToggleGroup toggleGroup) {
        ToggleButton toggleButton = new ToggleButton(anywhereDoor.getDisplayName());
        Tooltip tooltip = new Tooltip(anywhereDoor.getDisplayName() + "\n" + anywhereDoor.getKey());

        switch (anywhereDoor.getType()) {
            case FOLDER -> toggleButton.getStyleClass().add("grid-left-anywhere-folder-btn");
            case TAG -> toggleButton.getStyleClass().add("grid-left-anywhere-tag-btn");
            case VA -> toggleButton.getStyleClass().add("grid-left-anywhere-va-btn");
            case CATEGORY -> toggleButton.getStyleClass().add("grid-left-anywhere-category-btn");
            case CIRCLE -> toggleButton.getStyleClass().add("grid-left-anywhere-circle-btn");
            case ONLINE -> toggleButton.getStyleClass().add("grid-left-anywhere-online-btn");
        }


        toggleButton.setPrefWidth(250);
        toggleButton.setMinWidth(0);
        toggleButton.setMaxWidth(Double.MAX_VALUE);
        toggleButton.setEllipsisString("");
        toggleButton.setGraphic(new FontIcon());
        toggleButton.setUserData(anywhereDoor.getId());
        tooltip.setShowDelay(Duration.millis(500));
        toggleButton.setTooltip(tooltip);
        toggleButton.setToggleGroup(toggleGroup);
        toggleButton.setAlignment(Pos.CENTER_LEFT);
        toggleButton.setOnAction(actionEvent -> {
            toggleButton.setSelected(true);
            navigationService.push(new WorksRoute(
                    new WorkSource(anywhereDoor.getType(), anywhereDoor.getKey(), anywhereDoor.getMediaLibraryId())));
        });
        return toggleButton;
    }


    /**
     * 加载底部播放面板（{@link DashboardView}），作为主内容区 {@code BorderPane} 的 bottom。
     *
     * @return 播放面板视图节点
     */
    private Node createDashboard() {
        ViewTuple<DashboardView, DashboardViewModel> dashboard = FluentViewLoader.fxmlView(DashboardView.class).load();
        return dashboard.getView();
    }


    /**
     * 弹出播放队列面板。
     * <p>
     * 队列容器背景置为透明，固定 390 宽并用平移量贴到窗口右下角（预留 24 边距），
     * 高度取当前高度减 200；关闭时清空容器子节点，避免复用残留。
     *
     * @param galgame 是否按 Galgame 模式套用队列样式
     */
    private void createPlaylistPane(boolean galgame) {
        PlaylistView playlistView = new PlaylistView(galgame);
        JFXDialog dialog = new JFXDialog(root, playlistView, JFXDialog.DialogTransition.CENTER);
        StackPane dialogContainer = (StackPane) dialog.getChildren().getFirst();
        dialogContainer.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, null, null)));
        dialogContainer.setPrefHeight(this.getHeight() - 200);
        double dialogWidth = 390;
        double rightMargin = 24;
        dialogContainer.setPrefWidth(dialogWidth);
        dialogContainer.setMinWidth(dialogWidth);
        double offsetX = this.getWidth() / 2;
        dialogContainer.setTranslateX(offsetX - dialogWidth / 2 - rightMargin);
        dialogContainer.setAlignment(Pos.BOTTOM_RIGHT);
        dialog.setAlignment(Pos.BOTTOM_RIGHT);
        dialog.show();

        dialog.setOnDialogClosed(event -> {
            StackPane node = (StackPane) event.getSource();
            node.getChildren().clear();
            node = null;
        });
    }


    /**
     * 把整屏页面从屏幕底部滑入并压在当前页之上（150 毫秒动画），用于进入播放页。
     * <p>
     * 起始偏移取场景高度，因此页面初始位于视口下方外；动画只改 {@code translateY}，
     * 结束后节点留在 {@code root} 顶层。
     *
     * @param node 待覆盖的整屏页面
     */
    public void addRootPane(Node node) {
        Scene scene = Main.window.getScene();
        node.setTranslateY(scene.getHeight());
        root.getChildren().add(node);
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(150), new KeyValue(node.translateYProperty(), 0)));
        timeline.play();
    }


    /**
     * 把最上层页面动画移出屏幕并移除，用于退出播放页。
     * <p>
     * 注意条件判断读的是 {@code MainView} 自身的子节点数（标题根、消息层、覆盖层、
     * 模态层常驻，恒大于 1），真正被移除的始终是 {@code root} 的最后一个页面。
     */
    public void removeRootPane() {
        if (getChildren().size() > 1) { //最底层的不消除，始终保留一个
            Node last = root.getChildren().getLast();
            Scene scene = Main.window.getScene();
            Timeline timeline = new Timeline(
                    new KeyFrame(
                            Duration.millis(150),
                            new KeyValue(
                                    last.translateYProperty(), scene.getHeight()
                            )));
            timeline.setOnFinished(actionEvent -> {
                root.getChildren().removeLast();
            });
            timeline.play();
        }
    }

    /**
     * 用新页面替换当前最上层页面（传统播放页与 Galgame 页互切时走这里）。
     * <p>
     * 起始偏移固定 600 像素，不随场景高度变化；动画结束后删掉新页面下面的那一层，
     * 使 {@code root} 上的整屏页面数保持不变。
     *
     * @param node 替换进来的整屏页面
     */
    public void replaceRootPane(Node node) {
        node.setTranslateY(600);
        root.getChildren().add(node);
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(150), new KeyValue(node.translateYProperty(), 0)));
        timeline.play();
        timeline.setOnFinished(actionEvent -> {
            int index = root.getChildren().size();
            if (index > 2) {
                root.getChildren().remove(index - 2);
            }
        });
    }


}
