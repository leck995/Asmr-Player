package cn.tealc.asmr.ui.player;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.asmr.Main;
import cn.tealc.asmr.factory.NodeFactory;
import cn.tealc.asmr.model.SubtitleCue;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.ui.player.LrcView;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.TimeFormatUtils;
import com.jfoenix.controls.JFXSlider;
import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.InjectViewModel;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import java.net.URL;
import java.util.ResourceBundle;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignG;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;

/**
 * 播放详情页：封面、曲目信息、进度条、歌词与控制按钮组成的整屏播放界面。
 * <p>
 * 由 {@code MainView} 响应 {@code TO_PLAY} 事件后以覆盖层方式滑入，
 * 自带内嵌标题栏（全屏/最小化/最大化/关闭 + Galgame 模式切换）。
 * <p>
 * 界面状态全部单向或双向绑定到 {@link PlayerDetailViewModel}，
 * 视图本身不直接访问播放器；进度条拖动期间用 {@code isMouseMove} 暂停回填，
 * 松开后调用 {@code seek} 跳转。返回按钮先用动画把页面移出屏幕，
 * 动画结束再通知 ViewModel 通知主视图移除本层。
 *
 * @author Leck
 */
public class PlayerDetailView extends StackPane implements FXMLView<PlayerDetailViewModel>, Initializable {
    @InjectViewModel
    private PlayerDetailViewModel viewModel;
    @FXML
    private StackPane root, lrcPane, albumPane;
    @FXML
    private ImageView album;
    @FXML
    private Label currentTimeLabel, totalTimeLabel;
    @FXML
    private Pane bgPane;
    @FXML
    private AnchorPane infoPane, progressPane;
    @FXML
    private Label singer;
    @FXML
    private Label songName;
    @FXML
    private Button nextBtn, preBtn, showSongListViewBtn, subtitleSyncBtn;
    /** 返回按钮：不再放在 FXML 右下角，改为构建到内嵌标题栏左侧 */
    private Button backBtn;
    @FXML
    private ToggleButton disorderBtn, loopBtn, playBtn, lrcBtn, volumeBtn;
    @FXML
    private JFXSlider songSlider;
    @FXML
    private Button changeCoverBtn;
    private Button galgameBtn;
    private LrcView lrcView;
    /** 进度条是否被按住：true 时不用播放进度回填滑条，避免拖动值被覆盖 */
    public Boolean isMouseMove = false;
    private ContextMenu volumePopup;

    public PlayerDetailView() {
        super();
        getStylesheets().add(CssLoader.getCss(CssLoader.playerDetail));
    }

    /**
     * FXML 注入完成后组装页面：内嵌标题栏、封面圆角裁剪、控制按钮与进度条绑定、歌词视图。
     * <p>
     * 全屏按钮直接切换 {@code Main.window} 的全屏状态，最小化与最大化交给原生
     * {@code HeaderBar} 的按钮类型处理，关闭按钮发布 {@code EXIT} 事件；
     * 双击封面或点击“切换封面”调用 {@code nextCover} 换背景图。
     * 时间标签在时长未知（{@code Double.MAX_VALUE} 或非正数）时显示 {@code 00:00}。
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // 内嵌标题栏：使用原生 HeaderBar（左侧返回按钮，右侧系统按钮）
        HeaderBar headerBar = new HeaderBar();
        headerBar.getStyleClass().addAll("headbar", "embed-headbar");
        // 返回按钮：置于标题栏左侧，沿用主标题栏的圆形导航按钮样式
        backBtn = new Button(null, new FontIcon(MaterialDesignC.CHEVRON_DOWN));
        backBtn.getStyleClass().add("header-nav-btn");
        backBtn.setTooltip(new Tooltip("返回"));
        backBtn.setAccessibleText("返回");
        HBox navBox = new HBox(backBtn);
        navBox.getStyleClass().add("header-nav");
        navBox.setAlignment(Pos.CENTER_LEFT);
        navBox.setPickOnBounds(false);
        headerBar.setLeft(navBox);
        // 系统按钮：全屏/最小化/最大化/关闭（从左到右）
        Button fullBtn = new Button(null, new FontIcon(MaterialDesignF.FULLSCREEN));
        Button minBtn = new Button(null, new FontIcon(MaterialDesignW.WINDOW_MINIMIZE));
        Button maxBtn = new Button(null, new FontIcon(MaterialDesignW.WINDOW_MAXIMIZE));
        Button closeBtn = new Button(null, new FontIcon(MaterialDesignC.CLOSE));
        HeaderBar.setButtonType(minBtn, HeaderButtonType.ICONIFY);
        HeaderBar.setButtonType(maxBtn, HeaderButtonType.MAXIMIZE);
        fullBtn.setOnAction(e -> Main.window.setFullScreen(!Main.window.isFullScreen()));
        closeBtn.setOnAction(e -> EventBus.publish(NotificationKey.EXIT));
        closeBtn.getStyleClass().add("close-btn");
        // 最大化状态切换图标（最大化↔还原）
        Main.window.maximizedProperty().addListener((obs, old, isMax) ->
                maxBtn.setGraphic(new FontIcon(isMax
                        ? MaterialDesignW.WINDOW_RESTORE
                        : MaterialDesignW.WINDOW_MAXIMIZE)));
        // 全屏状态切换图标（全屏↔退出全屏）
        Main.window.fullScreenProperty().addListener((obs, old, isFull) ->
                fullBtn.setGraphic(new FontIcon(isFull
                        ? MaterialDesignF.FULLSCREEN_EXIT
                        : MaterialDesignF.FULLSCREEN)));
        // galgame 按钮：放到系统按钮组左侧（最小化按钮前），带图标和 tooltip
        galgameBtn = new Button();
        galgameBtn.setGraphic(new FontIcon(MaterialDesignG.GAMEPAD_OUTLINE));
        galgameBtn.getStyleClass().add("header-icon-btn");
        galgameBtn.setTooltip(new Tooltip("Galgame模式"));
        galgameBtn.setOnAction(actionEvent -> viewModel.gotoGalgame());

        HBox systemBox = new HBox(galgameBtn, fullBtn, minBtn, maxBtn, closeBtn);
        systemBox.getStyleClass().add("system-func");
        HBox trailingBox = new HBox(systemBox);
        trailingBox.getStyleClass().add("trailing");
        headerBar.setRight(trailingBox);

        BorderPane border = new BorderPane();
        border.setTop(headerBar);
        border.setCenter(root.getChildren().get(2));
        root.getChildren().add(border);


        bgPane.backgroundProperty().bind(viewModel.backgroudProperty());

        album.imageProperty().bind(viewModel.coverProperty());
        Rectangle albumRectangle = new Rectangle();
        albumRectangle.widthProperty().bind(albumPane.widthProperty().add(-10));
        albumRectangle.heightProperty().bind(albumPane.heightProperty().add(-10));
        albumRectangle.arcWidthProperty().bind(Config.setting.detailAlbumRadiusSize);
        albumRectangle.arcHeightProperty().bind(Config.setting.detailAlbumRadiusSize);
        albumPane.setClip(albumRectangle);

        albumPane.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton() == MouseButton.PRIMARY && mouseEvent.getClickCount() == 2){
                viewModel.nextCover();

            }
        });

        album.fitWidthProperty().bind(root.widthProperty().multiply(0.45));
        album.fitHeightProperty().bind(root.heightProperty().multiply(0.45));
        infoPane.prefWidthProperty().bind(albumPane.widthProperty());
        progressPane.prefWidthProperty().bind(albumPane.widthProperty());



        disorderBtn.setTooltip(new Tooltip("乱序"));
        disorderBtn.selectedProperty().bindBidirectional(viewModel.disorderProperty());
        loopBtn.setTooltip(new Tooltip("循环"));
        loopBtn.selectedProperty().bindBidirectional(viewModel.loopProperty());
        showSongListViewBtn.setTooltip(new Tooltip("播放列表"));
        showSongListViewBtn.setOnMouseClicked(mouseEvent -> {
            viewModel.playlist();
        });
        subtitleSyncBtn.setTooltip(new Tooltip("字幕设置"));
        subtitleSyncBtn.setOnAction(actionEvent -> SubtitleSyncDialog.show());
        playBtn.selectedProperty().bindBidirectional(viewModel.playingProperty());
        playBtn.setOnAction(actionEvent -> {
            if (!viewModel.isReady()) {
                playBtn.setSelected(false);
                actionEvent.consume();
            }
        });
        lrcBtn.setTooltip(new Tooltip("桌面歌词"));
        lrcBtn.selectedProperty().bindBidirectional(viewModel.desktopLrcProperty());

        nextBtn.setOnMouseClicked(mouseEvent -> viewModel.next());
        preBtn.setOnMouseClicked(mouseEvent -> viewModel.pre());

        volumeBtn.selectedProperty().bind(viewModel.muteProperty());
        volumeBtn.addEventFilter(MouseEvent.MOUSE_PRESSED, new EventHandler<Event>() {
            @Override
            public void handle(Event event) {
                if (volumePopup == null) {
                    volumePopup = NodeFactory.createVolumePopupMenu();
                }
                NodeFactory.showVolumePopup(volumePopup, volumeBtn);
                event.consume();
            }
        });

        songSlider.setMin(0);
        songSlider.setMaxWidth(900);
        songSlider.maxProperty().bind(viewModel.totalTimeProperty());

        /*进度条随歌曲时间更改*/
        SimpleDoubleProperty currentTimeProperty = new SimpleDoubleProperty();
        currentTimeProperty.bind(viewModel.currentTimeProperty());
        currentTimeProperty.addListener((obs, oldValue, newValue) -> {
            if (!isMouseMove) {
                songSlider.setValue(newValue.doubleValue());
            }
        });
        //设置播放条显示格式
        songSlider.setValueFactory(slider -> Bindings
                .createStringBinding(() -> (
                        TimeFormatUtils.formatToClock(songSlider.getValue())
                ), slider.valueProperty()));
        /*进度条按下监听*/
        songSlider.setOnMousePressed(mouseEvent -> isMouseMove = true);
        /*进度条释放监听，跳转到指定时间*/
        songSlider.setOnMouseReleased(mouseEvent -> {
            if (!viewModel.isReady())
                return;
            isMouseMove = false;
            viewModel.seek(songSlider.getValue());
        });

        if (currentTimeProperty.getValue() != null)
            songSlider.setValue(currentTimeProperty.getValue());

        songName.textProperty().bind(viewModel.titleProperty());


        singer.textProperty().bind(viewModel.artistProperty());
        currentTimeLabel.textProperty().bind(Bindings
                .createStringBinding(() -> {
                    double t = viewModel.getCurrentTime();
                    return (t < 0 || t == Double.MAX_VALUE) ? "00:00"
                            : TimeFormatUtils.formatToClock(t);
                }, viewModel.currentTimeProperty()));
        totalTimeLabel.textProperty().bind(Bindings
                .createStringBinding(() -> {
                    double t = viewModel.totalTimeProperty().get();
                    return (t <= 0 || t == Double.MAX_VALUE) ? "00:00"
                            : TimeFormatUtils.formatToClock(t);
                }, viewModel.totalTimeProperty()));
        initLrcView();

        viewModel.lrcBeansProperty().addListener((obs, oldVal, newVal) -> lrcView.setItems(newVal));
        lrcView.setItems(viewModel.getLrcBeans());


        backBtn.setOnAction(actionEvent -> {
            currentTimeProperty.unbind();

            Timeline timeline = new Timeline(new KeyFrame(Duration.millis(240), new KeyValue(root.translateYProperty(), Main.window.getHeight())));
            timeline.setOnFinished(event -> {
                viewModel.back();
            });
            timeline.play();

        });


        changeCoverBtn.setTooltip(new Tooltip("切换封面"));
        changeCoverBtn.setOnAction(actionEvent -> {
            viewModel.nextCover();
        });
        // 切换封面按钮默认隐藏，鼠标悬停封面时显示
        changeCoverBtn.setVisible(false);
        changeCoverBtn.setManaged(false);
        albumPane.setOnMouseEntered(e -> {
            changeCoverBtn.setVisible(true);
            changeCoverBtn.setManaged(true);
        });
        albumPane.setOnMouseExited(e -> {
            changeCoverBtn.setVisible(false);
            changeCoverBtn.setManaged(false);
        });
    }


    /**
     * 创建歌词视图并放入歌词容器。
     * <p>
     * 高亮行号取自 ViewModel 转发给播放器的 {@code lrcSelectedIndexProperty}，
     * 双击歌词行时用该行起始时间（秒）回调 {@code seek}。
     */
    private void initLrcView(){
        lrcView=new LrcView(viewModel.lrcSelectedIndexProperty(), viewModel::seek);
        lrcView.getStyleClass().add("lrc-view");
        lrcPane.getChildren().add(lrcView);
    }
}