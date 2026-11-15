package cn.tealc.asmr.ui.library;

import atlantafx.base.theme.Styles;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.model.StorageSource;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.mvvm.JavaView;
import cn.tealc.asmr.service.StorageSourceService;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.source.StorageType;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogHandle;
import cn.tealc.asmr.ui.dialog.DialogSpec;
import cn.tealc.asmr.ui.component.EmptyState;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.utils.CssLoader;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 媒体库导入与管理界面。 */
public class UnifiedMediaLibraryView extends BorderPane implements JavaView<UnifiedMediaLibraryViewModel> {
    private static final DateTimeFormatter ACTIVITY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    @InjectViewModel private UnifiedMediaLibraryViewModel viewModel;
    private final StorageSourceService storageSourceService = DI.get(StorageSourceService.class);
    private final FlowPane cards = new FlowPane(12, 12);
    private final EmptyState empty = new EmptyState();
    private final Label count = new Label();
    private ScrollPane scroll;

    public void initialize() {
        setPadding(new Insets(12));
        Label title = new Label("媒体库");
        title.getStyleClass().add("storage-source-heading");
        count.getStyleClass().add("storage-source-secondary");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button refresh = iconButton("mdomz-refresh", "刷新媒体库");
        refresh.setOnAction(event -> viewModel.refresh());
        Button add = new Button("导入媒体库", new FontIcon("mdoal-add"));
        add.getStyleClass().add(Styles.ACCENT);
        add.setOnAction(event -> showImporter());
        HBox toolbar = new HBox(10, title, count, spacer, refresh, add);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 14, 0));
        setTop(toolbar);

        cards.getStyleClass().add("storage-source-cards");
        empty.setIcon(Material2OutlinedMZ.STORAGE);
        empty.setTitle("暂无媒体库");
        empty.setDescription("导入媒体库后，作品会显示在作品列表。");
        empty.setMouseTransparent(true);
        empty.setVisible(false);
        empty.setManaged(false);
        VBox content = new VBox(cards);
        scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("storage-source-scroll");
        scroll.viewportBoundsProperty().addListener((obs, before, bounds) ->
                cards.setPrefWrapLength(Math.max(1, bounds.getWidth() - 8)));
        setCenter(new StackPane(scroll, empty));
        viewModel.getLibraries().addListener((ListChangeListener<MediaLibrary>) change -> renderCards());
        renderCards();
    }

    private void renderCards() {
        Map<Integer, StorageSource> sources = storageSourceService.getAll().stream()
                .collect(Collectors.toMap(StorageSource::getId, Function.identity()));
        cards.getChildren().setAll(viewModel.getLibraries().stream()
                .map(library -> createCard(library, sources.get(library.getStorageSourceId()))).toList());
        boolean noLibraries = viewModel.getLibraries().isEmpty();
        empty.setManaged(noLibraries);
        empty.setVisible(noLibraries);
        count.setText(viewModel.getLibraries().size() + " 个");
    }

    private Node createCard(MediaLibrary library, StorageSource source) {
        boolean local = library.getStorageType() == StorageType.LOCAL;
        FontIcon kindIcon = new FontIcon(local ? "mdomz-storage" : "mdoal-cloud");
        kindIcon.getStyleClass().add("storage-source-kind-icon");
        StackPane iconBox = new StackPane(kindIcon);
        iconBox.getStyleClass().add(local ? "storage-source-icon-local" : "storage-source-icon-remote");
        iconBox.getStyleClass().add("storage-source-icon-box");
        Label name = new Label(library.getName());
        name.getStyleClass().add("storage-source-name");
        name.setMaxWidth(Double.MAX_VALUE);
        name.setTooltip(new Tooltip(library.getName()));
        Label sourceName = new Label(source == null ? "存储源不可用" : source.getName());
        sourceName.setTooltip(new Tooltip(sourceName.getText()));
        sourceName.getStyleClass().add("storage-source-kind");
        VBox identity = new VBox(4, name, sourceName);
        identity.setMinWidth(0);
        HBox.setHgrow(identity, Priority.ALWAYS);
        Button scrape = iconButton("mdoal-cloud_download", "刮削媒体库元数据");
        scrape.getStyleClass().addAll("storage-source-card-action", "media-library-scrape-action");
        scrape.setOnAction(event -> viewModel.scrape(library));
        Button rescan = iconButton("mdomz-search", "重新扫描媒体库");
        rescan.getStyleClass().addAll("storage-source-card-action", "media-library-rescan-action");
        rescan.setOnAction(event -> showRescanOptions(library));
        Button remove = iconButton("mdoal-delete_outline", "删除媒体库");
        remove.getStyleClass().addAll("storage-source-card-action", "storage-source-delete");
        remove.setOnAction(event -> showDeleteConfirmation(library));
        HBox actions = new HBox(2, rescan, scrape, remove);
        HBox heading = new HBox(10, iconBox, identity, actions);
        heading.setAlignment(Pos.TOP_LEFT);

        Label pathTitle = new Label("媒体库目录");
        pathTitle.getStyleClass().add("storage-source-caption");
        Label workCount = new Label();
        workCount.textProperty().bind(Bindings.createStringBinding(
                () -> "作品 " + viewModel.getWorkCount(library), viewModel.getWorkCounts()));
        workCount.getStyleClass().add("media-library-work-count");
        Label level = new Label("层级 " + library.getLevel());
        level.getStyleClass().add("storage-source-status-active");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox pathHeading = new HBox(8, pathTitle, workCount, spacer, level);
        pathHeading.setAlignment(Pos.CENTER_LEFT);
        Label path = new Label(library.getPath());
        path.setMinWidth(0);
        path.setMaxWidth(Double.MAX_VALUE);
        path.setTooltip(new Tooltip(library.getPath()));
        path.getStyleClass().add("storage-source-path");
        Label lastImported = new Label(formatActivity("最后导入", library.getLastImportedAt()));
        Label lastScraped = new Label(formatActivity("最后刮削", library.getLastScrapedAt()));
        VBox activities = new VBox(2, lastImported, lastScraped);
        activities.getStyleClass().add("media-library-activities");
        VBox details = new VBox(5, pathHeading, path, activities);
        details.getStyleClass().add("storage-source-details");

        VBox card = new VBox(14, heading, details);
        card.getStyleClass().addAll("storage-source-card", "media-library-card");
        card.setMinWidth(0);
        card.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.min(320, Math.max(0, scroll.getViewportBounds().getWidth() - 8)),
                scroll.viewportBoundsProperty()));
        card.setMaxWidth(320);
        return card;
    }

    private String formatActivity(String label, LocalDateTime time) {
        return label + "：" + (time == null ? "尚未完成" : ACTIVITY_TIME_FORMATTER.format(time));
    }

    private void showImporter() {
        ComboBox<StorageSource> sourceBox = new ComboBox<>();
        sourceBox.getItems().setAll(storageSourceService.getAll());
        sourceBox.setPromptText("选择存储源");
        sourceBox.setMaxWidth(Double.MAX_VALUE);
        sourceBox.getStyleClass().add("media-library-form-combo");
        TextField name = new TextField();
        name.setPromptText("选填，默认使用目录名称");
        TextField selectedPath = new TextField();
        selectedPath.setEditable(false);
        selectedPath.setPromptText("尚未选择目录");
        ComboBox<String> level = new ComboBox<>();
        level.getItems().setAll("目录/音声", "目录/目录/音声", "目录/目录/目录/音声", "目录/目录/目录/目录/音声");
        level.getSelectionModel().selectFirst();
        level.setMaxWidth(Double.MAX_VALUE);
        level.getStyleClass().add("media-library-form-combo");
        CheckBox titleCn = new CheckBox("使用目录名称作为中文标题");
        CheckBox forceUpdate = new CheckBox("强制更新已有作品");
        Label message = messageLabel();
        Button selectDirectory = new Button("选择目录", new FontIcon("mdoal-folder_open"));
        selectDirectory.setOnAction(event -> showDirectoryPicker(sourceBox.getValue(), selection -> {
            sourceBox.getSelectionModel().select(selection.source());
            selectedPath.setText(selection.directory().getFilepath());
            if (name.getText().isBlank()) name.setText(selection.directory().getFilename());
        }));
        sourceBox.valueProperty().addListener((obs, before, source) -> {
            if (before != source) selectedPath.clear();
        });
        HBox directoryInput = new HBox(8, selectedPath, selectDirectory);
        HBox.setHgrow(selectedPath, Priority.ALWAYS);
        VBox form = new VBox(12, field("存储源", sourceBox), field("媒体库名称", name),
                field("媒体库目录", directoryInput), field("扫描层级", level), titleCn, forceUpdate);
        form.setPrefWidth(560);
        form.setPadding(new Insets(4, 4, 4, 2));
        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);
        formScroll.setPrefViewportHeight(390);
        formScroll.getStyleClass().add("form-scroll");
        if (getScene() != null) {
            formScroll.maxHeightProperty().bind(Bindings.max(120, getScene().heightProperty().subtract(210)));
        } else {
            formScroll.setMaxHeight(460);
        }
        if (sourceBox.getItems().isEmpty()) message.setText("暂无存储源，请先创建存储源。");
        VBox body = new VBox(8, formScroll, message);
        showDialog("导入媒体库", body,
                DialogAction.of("导入", "default-button", handle -> {
                    try {
                        StorageSource source = sourceBox.getValue();
                        if (source == null) throw new IllegalArgumentException("请先选择存储源");
                        String path = selectedPath.getText();
                        if (path.isBlank()) throw new IllegalArgumentException("请选择要导入的目录");
                        if (!storageSourceService.containsPath(source, path)) throw new IllegalArgumentException("目录超出存储源目标路径或不可访问");
                        String libraryName = name.getText().isBlank() ? path : name.getText();
                        viewModel.add(source, libraryName, path, level.getSelectionModel().getSelectedIndex(),
                                titleCn.isSelected(), forceUpdate.isSelected());
                        handle.close();
                    } catch (RuntimeException error) {
                        message.setText(errorMessage(error));
                    }
                }),
                DialogAction.of("取消", "cancel-button", DialogHandle::close));
    }

    private void showDirectoryPicker(StorageSource initialSource, java.util.function.Consumer<DirectorySelection> onSelected) {
        ComboBox<StorageSource> sourceBox = new ComboBox<>();
        sourceBox.getItems().setAll(storageSourceService.getAll());
        sourceBox.setPromptText("选择存储源");
        sourceBox.setMaxWidth(Double.MAX_VALUE);
        sourceBox.getStyleClass().add("media-library-form-combo");
        if (initialSource != null) sourceBox.getSelectionModel().select(initialSource);
        TreeView<AsmrFile> tree = new TreeView<>();
        tree.setShowRoot(true);
        tree.setPrefHeight(280);
        Label message = messageLabel();
        Button refresh = iconButton("mdomz-refresh", "刷新目录");
        refresh.getStyleClass().add("storage-source-card-action");
        refresh.disableProperty().bind(sourceBox.valueProperty().isNull());
        refresh.setOnAction(event -> showSourceRoot(sourceBox.getValue(), tree, message));
        sourceBox.valueProperty().addListener((obs, before, source) -> showSourceRoot(source, tree, message));
        if (initialSource != null) showSourceRoot(initialSource, tree, message);
        Label directoryLabel = new Label("选择目录");
        directoryLabel.getStyleClass().add("form-field-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox directoryHeading = new HBox(8, directoryLabel, spacer, refresh);
        directoryHeading.setAlignment(Pos.CENTER_LEFT);
        VBox body = new VBox(12, field("存储源", sourceBox), directoryHeading, tree, message);
        body.setPrefWidth(560);
        showDialog("选择媒体库目录", body,
                DialogAction.of("选择", "default-button", handle -> {
                    try {
                        StorageSource source = sourceBox.getValue();
                        TreeItem<AsmrFile> selected = tree.getSelectionModel().getSelectedItem();
                        if (source == null || selected == null || selected.getValue() == null) {
                            throw new IllegalArgumentException("请选择存储源中的目录");
                        }
                        if (!storageSourceService.containsPath(source, selected.getValue().getFilepath())) {
                            throw new IllegalArgumentException("目录超出存储源目标路径或不可访问");
                        }
                        onSelected.accept(new DirectorySelection(source, selected.getValue()));
                        handle.close();
                    } catch (RuntimeException error) {
                        message.setText(errorMessage(error));
                    }
                }),
                DialogAction.of("取消", "cancel-button", DialogHandle::close));
    }

    private void showSourceRoot(StorageSource source, TreeView<AsmrFile> tree, Label message) {
        tree.setRoot(null);
        message.setText("");
        if (source == null) return;
        try {
            AsmrFile directory = new AsmrFile(source.getName(), source.getTargetPath(), "dir", true, source.getName(), 0, null);
            AtomicReference<TreeItem<AsmrFile>> root = new AtomicReference<>();
            root.set(new StorageSourceTreeItem(directory, MediaSourceFactory.forStorageSource(source), error -> {
                if (tree.getRoot() == root.get()) message.setText("读取目录失败：" + errorMessage(error));
            }));
            tree.setRoot(root.get());
            root.get().setExpanded(true);
        } catch (RuntimeException error) {
            message.setText(errorMessage(error));
        }
    }

    private void showRescanOptions(MediaLibrary library) {
        CheckBox titleCn = new CheckBox("使用目录名称作为中文标题");
        CheckBox forceUpdate = new CheckBox("强制更新已有作品");
        Label message = messageLabel();
        VBox body = new VBox(12, new Label("重新扫描「" + library.getName() + "」"), titleCn, forceUpdate, message);
        body.setPrefWidth(400);
        showDialog("重新扫描媒体库", body,
                DialogAction.of("重新扫描", "default-button", handle -> {
                    try {
                        StorageSource current = library.getStorageSourceId() == null ? null : storageSourceService.getById(library.getStorageSourceId());
                        viewModel.rescan(library, current, titleCn.isSelected(), forceUpdate.isSelected());
                        handle.close();
                    } catch (RuntimeException error) {
                        message.setText(errorMessage(error));
                    }
                }),
                DialogAction.of("取消", "cancel-button", DialogHandle::close));
    }

    private void showDeleteConfirmation(MediaLibrary library) {
        Label description = new Label("确定删除媒体库「" + library.getName() + "」？存储中的文件不会被删除。");
        description.setWrapText(true);
        CheckBox clearWorks = new CheckBox("同时删除作品记录");
        Label message = messageLabel();
        VBox body = new VBox(12, description, clearWorks, message);
        body.setPrefWidth(400);
        Alerts.danger().title("删除媒体库")
                .content(body)
                .stylesheet(CssLoader.getCss(CssLoader.mediaLibrary))
                .button("删除", "default-button", handle -> {
                    try {
                        viewModel.delete(library, clearWorks.isSelected());
                        handle.close();
                    } catch (RuntimeException error) {
                        message.setText(errorMessage(error));
                    }
                })
                .cancel()
                .show();
    }

    /**
     * 展示弹窗：统一走 Dialogs 体系，并附加本页业务样式。
     *
     * @param title   标题
     * @param body    内容
     * @param actions 动作按钮，按声明顺序从左到右排列；取消动作放最后以显示在最右侧
     */
    private void showDialog(String title, Node body, DialogAction... actions) {
        DialogSpec.Builder builder = DialogSpec.builder()
                .title(title)
                .content(body)
                .stylesheet(CssLoader.getCss(CssLoader.mediaLibrary));
        for (DialogAction action : actions) {
            builder.action(action);
        }
        Dialogs.show(builder.build());
    }

    private VBox field(String title, Node input) {
        Label label = new Label(title);
        label.getStyleClass().add("form-field-label");
        VBox box = new VBox(6, label, input);
        box.getStyleClass().add("form-field");
        return box;
    }

    private Button iconButton(String icon, String description) {
        Button button = new Button(null, new FontIcon(icon));
        button.getStyleClass().addAll(Styles.FLAT, "button-icon");
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        return button;
    }

    private Label messageLabel() {
        Label message = new Label();
        message.setWrapText(true);
        message.getStyleClass().add("form-message");
        message.visibleProperty().bind(message.textProperty().isNotEmpty());
        message.managedProperty().bind(message.visibleProperty());
        return message;
    }

    private String errorMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank() ? "操作失败" : error.getMessage();
    }

    private record DirectorySelection(StorageSource source, AsmrFile directory) {}
}
