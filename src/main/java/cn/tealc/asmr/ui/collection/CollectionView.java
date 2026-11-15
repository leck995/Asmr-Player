package cn.tealc.asmr.ui.collection;

import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.collection.CollectionSheetSummary;
import cn.tealc.asmr.model.collection.SheetData;
import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.mvvm.NotificationObserver;
import cn.tealc.asmr.ui.component.EmptyState;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogSpec;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.ui.navigation.CollectionDetailRoute;
import cn.tealc.asmr.ui.navigation.NavigationService;
import cn.tealc.asmr.utils.CoverUtils;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.FXResourcesLoader;
import cn.tealc.asmr.utils.TimeFormatUtils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

/**
 * 歌单卡片主页。
 */
public class CollectionView implements FXMLView<CollectionViewModel> {
    private static final Image DEFAULT_COVER = new Image(FXResourcesLoader.image("cover-main.jpg"), 400, 300, true, true);

    @InjectViewModel
    private CollectionViewModel viewModel;
    @FXML
    private AnchorPane root;
    @FXML
    private TilePane sheetTilePane;
    @FXML
    private PageHeader pageHeader;
    @FXML
    private EmptyState emptyState;
    private final NotificationObserver skinObserver = (key, payload) -> applyStyleSheet();

    public void initialize() {
        applyStyleSheet();
        Button createButton = new Button("创建歌单", new FontIcon(Material2OutlinedMZ.PLAYLIST_ADD));
        createButton.getStyleClass().add("accent");
        createButton.setOnAction(this::createSheet);
        pageHeader.setIcon(Material2OutlinedMZ.QUEUE_MUSIC);
        pageHeader.setTitle("歌单");
        pageHeader.setSubtitle("整理常用媒体，点击歌单卡片查看和管理内容。");
        pageHeader.setActions(createButton);
        emptyState.setIcon(Material2OutlinedMZ.QUEUE_MUSIC);
        emptyState.setTitle("暂无歌单");
        emptyState.setDescription("创建一个歌单，把常用媒体整理在一起。");
        emptyState.visibleProperty().bind(Bindings.isEmpty(viewModel.getSheetSummaries()));
        emptyState.managedProperty().bind(emptyState.visibleProperty());
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                EventBus.unsubscribe("CHANGE_SKIN", skinObserver);
            } else {
                EventBus.subscribe("CHANGE_SKIN", skinObserver);
            }
        });
        viewModel.getSheetSummaries().addListener((ListChangeListener<CollectionSheetSummary>) change -> refreshCards());
        refreshCards();
    }

    @FXML
    void createSheet(ActionEvent event) {
        showSheetDialog(null);
    }

    private void applyStyleSheet() {
        root.getStylesheets().setAll(CssLoader.getCss(CssLoader.collection));
    }

    /**
     * 从详情页返回时刷新歌单卡片。
     */
    public void refresh() {
        viewModel.refreshSheetSummaries();
    }

    private void refreshCards() {
        sheetTilePane.getChildren().setAll(viewModel.getSheetSummaries().stream()
                .map(this::createSheetCard)
                .toList());
    }

    private StackPane createSheetCard(CollectionSheetSummary summary) {
        SheetData sheet = summary.getSheet();
        ImageView cover = new ImageView(DEFAULT_COVER);
        cover.setFitWidth(228);
        cover.setFitHeight(154);
        cover.setPreserveRatio(false);
        cover.setSmooth(true);
        SVGPath coverClip = new SVGPath();
        coverClip.setContent("M 0 154 L 0 11 Q 0 0 11 0 L 217 0 Q 228 0 228 11 L 228 154 Z");
        cover.setClip(coverClip);
        loadCover(summary.getLatestWork(), cover);

        StackPane coverBox = new StackPane(cover);
        coverBox.getStyleClass().add("collection-cover");
        Label name = new Label(sheet.getSheetName());
        name.setMaxWidth(Double.MAX_VALUE);
        name.getStyleClass().add("collection-sheet-name");
        Label description = new Label(descriptionText(sheet));
        description.getStyleClass().add("collection-sheet-description");
        description.setTextOverrun(OverrunStyle.ELLIPSIS);
        description.setMaxWidth(Double.MAX_VALUE);
        description.visibleProperty().bind(description.textProperty().isNotEmpty());
        description.managedProperty().bind(description.visibleProperty());
        Label count = new Label(String.format("%d 个媒体", summary.getItemCount()));
        count.getStyleClass().add("collection-sheet-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button editButton = createCardActionButton(Material2OutlinedAL.EDIT, "编辑歌单");
        editButton.setOnAction(event -> showSheetDialog(sheet));
        Button deleteButton = createCardActionButton(Material2OutlinedAL.DELETE_OUTLINE, "删除歌单");
        deleteButton.getStyleClass().add("collection-card-action-danger");
        deleteButton.setOnAction(event -> confirmDeleteSheet(sheet));
        HBox metadata = new HBox(6, count, spacer, editButton, deleteButton);
        metadata.setAlignment(Pos.CENTER_LEFT);

        // 元信息行固定在卡片底部，避免描述有无导致各卡片高度不齐
        VBox info = new VBox(10, coverBox, name, description);
        BorderPane content = new BorderPane();
        content.setTop(info);
        content.setBottom(metadata);
        BorderPane.setMargin(metadata, new Insets(10, 0, 0, 0));
        content.getStyleClass().add("collection-sheet-card");
        StackPane card = new StackPane(content);
        card.getStyleClass().add("collection-sheet-card-wrapper");
        Tooltip tooltip = new Tooltip(sheetTooltip(sheet));
        tooltip.setWrapText(true);
        Tooltip.install(card, tooltip);
        ContextMenu contextMenu = createSheetContextMenu(sheet);
        card.setOnContextMenuRequested(event -> contextMenu.show(card, event.getScreenX(), event.getScreenY()));
        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                openDetail(sheet);
            }
        });
        return card;
    }

    private void loadCover(AsmrWork latestWork, ImageView cover) {
        if (latestWork == null) {
            return;
        }
        Thread.startVirtualThread(() -> {
            Image image = CoverUtils.getCoverForWork(latestWork, "main", "cover-main", 400, 300, true, DEFAULT_COVER);
            Platform.runLater(() -> cover.setImage(image));
        });
    }

    private void openDetail(SheetData sheet) {
        DI.get(NavigationService.class).push(new CollectionDetailRoute(sheet.getId()));
    }

    private ContextMenu createSheetContextMenu(SheetData sheet) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem edit = new MenuItem("编辑", new FontIcon(Material2OutlinedAL.EDIT));
        edit.setOnAction(event -> showSheetDialog(sheet));
        MenuItem delete = new MenuItem("删除", new FontIcon(Material2OutlinedAL.DELETE_OUTLINE));
        delete.setOnAction(event -> confirmDeleteSheet(sheet));
        contextMenu.getItems().addAll(edit, delete);
        return contextMenu;
    }

    private Button createCardActionButton(Ikon icon, String tooltip) {
        Button button = new Button();
        button.setGraphic(new FontIcon(icon));
        button.getStyleClass().add("collection-card-action");
        button.setTooltip(new Tooltip(tooltip));
        // 阻止点击冒泡到卡片，避免编辑/删除时同时打开详情
        button.setOnMouseClicked(MouseEvent::consume);
        return button;
    }

    private void confirmDeleteSheet(SheetData sheet) {
        Alerts.danger().title("删除歌单")
                .message(String.format("确认删除歌单 %s 吗？", sheet.getSheetName()))
                .ok("删除", ignored -> viewModel.deleteSheet(sheet.getId()))
                .cancel().show();
    }

    private void showSheetDialog(SheetData sheet) {
        boolean creating = sheet == null;
        TextField name = new TextField(creating ? "" : sheet.getSheetName());
        name.setPromptText("歌单名称");
        TextArea description = new TextArea(creating ? "" : sheet.getDescription() == null ? "" : sheet.getDescription());
        description.setPromptText("描述（可选）");
        description.setWrapText(true);
        description.setPrefRowCount(4);
        Label error = new Label();
        error.getStyleClass().add("collection-dialog-error");
        error.visibleProperty().bind(error.textProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());

        VBox body = new VBox(8, new Label("名称"), name, new Label("描述"), description);
        if (!creating) {
            VBox metadata = new VBox(3,
                    new Label("创建时间：" + TimeFormatUtils.formatDateTime(sheet.getCreateDateTime())),
                    new Label("修改时间：" + TimeFormatUtils.formatDateTime(sheet.getUpdateDateTime())));
            metadata.getStyleClass().add("collection-dialog-meta");
            body.getChildren().add(metadata);
        }
        body.getChildren().add(error);
        body.setPrefWidth(440);

        DialogAction save = DialogAction.of(creating ? "创建" : "保存", "accent", handle -> {
            String trimmed = name.getText().trim();
            if (trimmed.isEmpty()) {
                error.setText("歌单名称不能为空");
                return;
            }
            boolean status = creating
                    ? viewModel.createSheet(trimmed, description.getText().trim())
                    : viewModel.updateSheet(sheet.getId(), trimmed, description.getText().trim());
            if (status) {
                handle.close();
            } else {
                error.setText("保存失败，请重试");
            }
        }).defaultButton().disabledWhen(
                Bindings.createBooleanBinding(() -> name.getText().isBlank(), name.textProperty()));

        Dialogs.show(DialogSpec.builder()
                .title(creating ? "创建歌单" : "编辑歌单")
                .content(body)
                .width(500)
                .stylesheet(CssLoader.getCss(CssLoader.collection))
                .action(save)
                .cancel()
                .build());
    }

    private static String descriptionText(SheetData sheet) {
        return sheet.getDescription() == null ? "" : sheet.getDescription().replaceAll("\\s+", " ").trim();
    }

    private static String sheetTooltip(SheetData sheet) {
        StringBuilder text = new StringBuilder();
        String description = descriptionText(sheet);
        if (!description.isEmpty()) {
            text.append(description).append('\n');
        }
        text.append("创建：").append(TimeFormatUtils.formatDateTime(sheet.getCreateDateTime()));
        text.append('\n').append("修改：").append(TimeFormatUtils.formatDateTime(sheet.getUpdateDateTime()));
        return text.toString();
    }
}
