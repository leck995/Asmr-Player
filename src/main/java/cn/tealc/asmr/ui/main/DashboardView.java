package cn.tealc.asmr.ui.main;

import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.factory.NodeFactory;
import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.TimeFormatUtils;
import com.jfoenix.controls.JFXSlider;
import javafx.beans.binding.Bindings;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * 主窗口底部播放控制条。
 * <p>
 * 纯视图层：所有属性绑定到 {@link DashboardViewModel}，按钮事件转发为 ViewModel 命令调用，
 * 仅保留需要直接操作 UI 节点的逻辑（音量弹窗定位、圆角裁剪、拖拽标志、皮肤切换）。
 *
 * @author Leck
 */
public class DashboardView implements FXMLView<DashboardViewModel>, Initializable {
    @FXML
    private StackPane root;
    @FXML
    private ImageView album;
    @FXML
    private HBox basePlayerTitlePane;
    @FXML
    private Label currentTime;
    @FXML
    private StackPane detailHiddenBtn, gotoDetailBtn, coverPane;
    @FXML
    private ToggleButton disorderBtn;
    @FXML
    private ToggleButton loopBtn;
    @FXML
    private ToggleButton starBtn;
    @FXML
    private Button nextBtn;
    @FXML
    private ToggleButton playBtn;
    @FXML
    private Button preBtn;
    @FXML
    private Button showSongListViewBtn;
    @FXML
    private Label singer;
    @FXML
    private Label songName;
    @FXML
    private JFXSlider songSlider;
    @FXML
    private ToggleButton lrcBtn;
    @FXML
    private Label totalTime;
    @FXML
    private ToggleButton volumeBtn;
    @FXML
    private HBox centerPane, leftPane;
    @FXML
    private Button audioChannelBtn;

    @InjectViewModel
    private DashboardViewModel viewModel;

    /** 进度条拖拽中标志，拖拽期间不同步播放器时间到滑块 */
    private boolean isMouseDragged = false;
    private ContextMenu volumePopup;

    public DashboardView() {
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        root.getStylesheets().add(CssLoader.getCss(CssLoader.dashboard));

        EventBus.subscribe("CHANGE_SKIN", (s, objects) -> {
            root.getStylesheets().clear();
            root.getStylesheets().add(CssLoader.getCss(CssLoader.dashboard));
        });

        // ==================== 属性绑定 ====================
        disorderBtn.selectedProperty().bindBidirectional(viewModel.disorderProperty());
        loopBtn.selectedProperty().bindBidirectional(viewModel.loopProperty());
        playBtn.selectedProperty().bindBidirectional(viewModel.playingProperty());
        lrcBtn.selectedProperty().bindBidirectional(viewModel.desktopLrcShowProperty());
        volumeBtn.selectedProperty().bind(viewModel.muteProperty());

        // 动态图标切换（播放/暂停由 playing 属性驱动，其余 ToggleButton 由 CSS :selected 切换图标）
        playBtn.graphicProperty().bind(Bindings.when(viewModel.playingProperty())
                .then(new FontIcon(Material2OutlinedMZ.PAUSE_CIRCLE_FILLED))
                .otherwise(new FontIcon(Material2OutlinedMZ.PLAY_CIRCLE_FILLED)));

        songName.textProperty().bind(viewModel.titleProperty());
        singer.textProperty().bind(viewModel.artistProperty());
        album.imageProperty().bind(viewModel.coverProperty());

        songSlider.setMin(0);
        songSlider.setValue(0);
        songSlider.maxProperty().bind(viewModel.totalTimeProperty());

        // 时间显示格式化
        currentTime.textProperty().bind(
                Bindings.createStringBinding(() ->
                        TimeFormatUtils.formatToClock(songSlider.getValue()),
                        songSlider.valueProperty())
        );
        totalTime.textProperty().bind(
                Bindings.createStringBinding(() ->
                        TimeFormatUtils.formatToClock(viewModel.getTotalTime()),
                        viewModel.totalTimeProperty())
        );
        songSlider.setValueFactory(slider ->
                Bindings.createStringBinding(() ->
                        TimeFormatUtils.formatToClock(slider.getValue()),
                        slider.valueProperty())
        );

        coverPane.setClip(creatClip());

        // ==================== 命令绑定 ====================
        playBtn.setOnAction(actionEvent -> {
            if (!viewModel.isReady()) {
                playBtn.setSelected(false);
                actionEvent.consume();
            }
        });
        nextBtn.setOnMouseClicked(mouseEvent -> viewModel.next());
        preBtn.setOnMouseClicked(mouseEvent -> viewModel.pre());
        showSongListViewBtn.setOnMouseClicked(mouseEvent -> viewModel.openPlaylist());
        gotoDetailBtn.setOnMouseClicked(mouseEvent -> viewModel.gotoDetail());
        audioChannelBtn.setOnAction(actionEvent -> viewModel.openAudioChannel());

        // 进度条拖拽
        songSlider.setOnMousePressed(mouseEvent -> isMouseDragged = true);
        songSlider.setOnMouseReleased(mouseEvent -> {
            viewModel.seek(songSlider.getValue());
            isMouseDragged = false;
        });

        // 播放器时间变化同步滑块（拖拽中除外）
        viewModel.currentTimeProperty().addListener((obs, oldValue, newValue) -> {
            if (!isMouseDragged) {
                songSlider.setValue(newValue.doubleValue());
            }
        });

        // ==================== 音量弹窗（需要 UI 坐标定位） ====================
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
    }

    /**
     * 创建封面圆角裁剪。
     */
    private Rectangle creatClip() {
        Rectangle rectangle = new Rectangle();
        rectangle.widthProperty().bind(coverPane.widthProperty());
        rectangle.heightProperty().bind(coverPane.heightProperty());
        rectangle.setArcHeight(15);
        rectangle.setArcWidth(15);
        return rectangle;
    }
}
