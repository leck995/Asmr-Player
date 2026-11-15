package cn.tealc.asmr.ui.galgame;

import cn.tealc.asmr.model.GalgameSaveData;
import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.ui.component.EmptyState;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogHandle;
import cn.tealc.asmr.ui.dialog.DialogPage;
import cn.tealc.asmr.utils.AppPaths;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.TimeFormatUtils;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Galgame 存档弹窗：以卡片网格展示存档，悬停预览字幕，点击卡片或跳转按钮读取存档，
 * 卡片内可删除存档。
 * <p>
 * 实现 {@link DialogPage}，标题、正文与动作由 {@code Dialogs} 分槽承载；由
 * {@link GalgameDetailView} 在点击“读取”时通过 {@code Dialogs.show(page)} 打开。
 * 卡片随存档列表、页码变化整体重建，保证序号连续；图标统一由 ikonli {@link FontIcon} 提供。
 *
 * @author Leck
 */
public class GalgameLoadView implements FXMLView<GalgameLoadViewModel>, Initializable, DialogPage {
    /** 卡片宽度。 */
    private static final double CARD_WIDTH = 300;
    /** 卡片内边距。 */
    private static final double CARD_PADDING = 8;
    /** 封面宽度：卡片宽度扣掉左右内边距。 */
    private static final double COVER_WIDTH = CARD_WIDTH - CARD_PADDING * 2;
    /** 封面高度，接近截图 16:9 比例。 */
    private static final double COVER_HEIGHT = 160;
    /** 封面圆角半径，裁剪形状取两倍值。 */
    private static final double COVER_RADIUS = 10;

    @InjectViewModel
    private GalgameLoadViewModel viewModel;

    @FXML
    private VBox root;
    @FXML
    private StackPane gridHolder;
    @FXML
    private FlowPane saveDataPane;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private Label titleLabel, messageLabel, pageLabel, previewIcon;
    @FXML
    private Button prevPageBtn, nextPageBtn;

    private final PageHeader header = new PageHeader();
    private final List<DialogAction> actions;
    private final EmptyState emptyState =
            EmptyState.of(Material2OutlinedMZ.SAVE, "暂无存档", "在 Galgame 界面点击保存按钮，创建第一个存档。");
    private DialogHandle dialogHandle;

    public GalgameLoadView() {
        header.setIcon(Material2OutlinedMZ.SAVE);
        header.setTitle("读取存档");
        header.setSubtitle("选择一个存档，从记录的位置继续播放。");

        actions = List.of(
                DialogAction.of("打开存档目录", "plain-action-button", handle -> openSaveFolder())
                        .graphic(icon(Material2OutlinedAL.FOLDER_OPEN, 16)),
                DialogAction.cancel("返回播放界面")
                        .graphic(icon(Material2OutlinedAL.CLOSE, 16)));
    }

    /**
     * 装配存档弹窗：绑定分页与预览，按当前存档构建卡片；列表或页码变化后重建卡片。
     */
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        configurePreview();
        configurePager();
        configureGrid();

        viewModel.getSaves().addListener((ListChangeListener<GalgameSaveData>) change -> rebuildCards());
        rebuildCards();
    }

    @Override
    public PageHeader header() {
        return header;
    }

    @Override
    public Node content() {
        return root;
    }

    @Override
    public List<DialogAction> actions() {
        return actions;
    }

    @Override
    public double width() {
        return viewModel.getContentMaxWidth();
    }

    @Override
    public List<String> stylesheets() {
        return List.of(CssLoader.getCss(CssLoader.galgame));
    }

    @Override
    public void onDialogAttached(DialogHandle handle) {
        this.dialogHandle = handle;
    }

    private void configurePreview() {
        previewIcon.setGraphic(icon(Material2OutlinedMZ.SUBTITLES, 18));
        titleLabel.textProperty().bind(Bindings.createStringBinding(
                () -> defaultIfBlank(viewModel.getSelectedSaveTitle(), "存档预览"),
                viewModel.selectedSaveTitleProperty()));
        messageLabel.textProperty().bind(Bindings.createStringBinding(
                () -> defaultIfBlank(viewModel.getSelectedSaveText(), "将鼠标移到存档卡片上，预览章节与字幕。"),
                viewModel.selectedSaveTextProperty()));
    }

    private void configurePager() {
        prevPageBtn.setGraphic(icon(Material2OutlinedAL.CHEVRON_LEFT, 18));
        prevPageBtn.setTooltip(new Tooltip("上一页"));
        prevPageBtn.setOnAction(event -> viewModel.setPageIndex(viewModel.getPageIndex() - 1));
        prevPageBtn.disableProperty().bind(viewModel.pageIndexProperty().lessThanOrEqualTo(0));

        nextPageBtn.setGraphic(icon(Material2OutlinedAL.CHEVRON_RIGHT, 18));
        nextPageBtn.setTooltip(new Tooltip("下一页"));
        nextPageBtn.setOnAction(event -> viewModel.setPageIndex(viewModel.getPageIndex() + 1));
        nextPageBtn.disableProperty().bind(Bindings.createBooleanBinding(
                () -> viewModel.getPageIndex() >= Math.max(1, viewModel.getPageCount()) - 1,
                viewModel.pageIndexProperty(),
                viewModel.pageCountProperty()));

        pageLabel.textProperty().bind(Bindings.createStringBinding(
                () -> (viewModel.getPageIndex() + 1) + " / " + Math.max(1, viewModel.getPageCount()),
                viewModel.pageIndexProperty(),
                viewModel.pageCountProperty()));

        viewModel.pageIndexProperty().addListener((observable, oldValue, newValue) -> {
            scrollPane.setVvalue(0);
            viewModel.updateSelectedSave(null);
        });
    }

    private void configureGrid() {
        saveDataPane.setAlignment(Pos.TOP_LEFT);

        emptyState.setMouseTransparent(true);
        emptyState.visibleProperty().bind(Bindings.isEmpty(viewModel.getSaves()));
        emptyState.managedProperty().bind(emptyState.visibleProperty());
        gridHolder.getChildren().add(emptyState);
    }

    private void rebuildCards() {
        saveDataPane.getChildren().clear();
        int index = 1;
        for (GalgameSaveData save : viewModel.getSaves()) {
            saveDataPane.getChildren().add(createCard(save, index++));
        }
    }

    /**
     * 构建一张存档卡片：封面缩略图、序号、章节、日期与时间，以及跳转和删除两个操作。
     * <p>
     * 悬停卡片会把章节与字幕推给预览区，移出时清空；单击卡片或按回车/空格读取存档。
     *
     * @param save  存档数据
     * @param index 卡片序号，从 1 开始
     * @return 存档卡片
     */
    private VBox createCard(GalgameSaveData save, int index) {
        ImageView thumb = new ImageView();
        thumb.setFitWidth(COVER_WIDTH);
        thumb.setFitHeight(COVER_HEIGHT);
        thumb.setPreserveRatio(false);
        thumb.setSmooth(true);
        thumb.setClip(roundedClip(COVER_WIDTH, COVER_HEIGHT, COVER_RADIUS));
        if (save.getThumb() != null && !save.getThumb().isBlank()) {
            String thumbUrl = new File(save.getThumb()).toURI().toString();
            thumb.setImage(new Image(thumbUrl, COVER_WIDTH, COVER_HEIGHT, true, true, true));
        }

        Label indexLabel = new Label(String.valueOf(index));
        indexLabel.getStyleClass().add("save-card-index");
        StackPane.setAlignment(indexLabel, Pos.TOP_LEFT);
        StackPane.setMargin(indexLabel, new Insets(8));

        StackPane cover = new StackPane(thumb, indexLabel);
        cover.getStyleClass().add("save-card-cover");
        cover.setMinSize(COVER_WIDTH, COVER_HEIGHT);
        cover.setPrefSize(COVER_WIDTH, COVER_HEIGHT);
        cover.setMaxSize(COVER_WIDTH, COVER_HEIGHT);

        Label chapterLabel = new Label(defaultIfBlank(save.getChapter(),
                defaultIfBlank(save.getTitle(), "未命名存档")));
        chapterLabel.getStyleClass().add("save-card-title");
        chapterLabel.setMinWidth(0);
        chapterLabel.setMaxWidth(Double.MAX_VALUE);

        Label dateLabel = new Label(defaultIfBlank(save.getSaveDate(), "未知日期"),
                icon(Material2OutlinedAL.CALENDAR_TODAY, 12));
        dateLabel.getStyleClass().add("save-card-meta");
        Label timeLabel = new Label(TimeFormatUtils.formatToClockFromMills(save.getTime()),
                icon(Material2OutlinedAL.ACCESS_TIME, 12));
        timeLabel.getStyleClass().add("save-card-meta");

        HBox metaRow = new HBox(12, dateLabel, timeLabel);
        metaRow.getStyleClass().add("save-card-meta-row");

        VBox info = new VBox(4, chapterLabel, metaRow);
        info.setMinWidth(0);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button jumpBtn = new Button();
        jumpBtn.getStyleClass().addAll("save-card-action", "save-card-jump");
        jumpBtn.setGraphic(icon(Material2OutlinedMZ.PLAY_ARROW, 18));
        jumpBtn.setTooltip(new Tooltip("跳转到存档"));
        jumpBtn.setOnAction(event -> jumpTo(save));
        jumpBtn.setOnMouseClicked(event -> event.consume());

        Button deleteBtn = new Button();
        deleteBtn.getStyleClass().addAll("save-card-action", "save-card-delete");
        deleteBtn.setGraphic(icon(Material2OutlinedAL.DELETE_OUTLINE, 18));
        deleteBtn.setTooltip(new Tooltip("删除存档"));
        deleteBtn.setOnAction(event -> confirmDelete(save));
        deleteBtn.setOnMouseClicked(event -> event.consume());

        HBox body = new HBox(8, info, jumpBtn, deleteBtn);
        body.setAlignment(Pos.CENTER_LEFT);
        body.getStyleClass().add("save-card-body");

        VBox card = new VBox(8, cover, body);
        card.getStyleClass().add("save-card");
        card.setPadding(new Insets(CARD_PADDING));
        card.setMinWidth(CARD_WIDTH);
        card.setPrefWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);
        card.setFocusTraversable(true);
        card.setOnMouseEntered(event -> viewModel.updateSelectedSave(save));
        card.setOnMouseExited(event -> viewModel.updateSelectedSave(null));
        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                jumpTo(save);
            }
        });
        card.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                jumpTo(save);
            }
        });
        return card;
    }

    private void jumpTo(GalgameSaveData save) {
        if (viewModel.jumpTo(save) && dialogHandle != null) {
            dialogHandle.close();
        }
    }

    private void confirmDelete(GalgameSaveData save) {
        Alerts.danger()
                .title("删除存档")
                .message("删除后无法恢复，确定删除这个存档吗？")
                .ok("删除", handle -> {
                    viewModel.updateSelectedSave(null);
                    viewModel.delete(save);
                })
                .cancel("取消")
                .show();
    }

    private void openSaveFolder() {
        try {
            File saveDir = AppPaths.saveDir();
            saveDir.mkdirs();
            Desktop.getDesktop().open(saveDir);
        } catch (IOException e) {
            Alerts.warning()
                    .title("无法打开存档目录")
                    .message(String.valueOf(e.getMessage()))
                    .cancel("确定")
                    .show();
        }
    }

    private static Rectangle roundedClip(double width, double height, double radius) {
        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        return clip;
    }

    private static FontIcon icon(Ikon ikon, int size) {
        FontIcon icon = new FontIcon(ikon);
        icon.setIconSize(size);
        return icon;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
