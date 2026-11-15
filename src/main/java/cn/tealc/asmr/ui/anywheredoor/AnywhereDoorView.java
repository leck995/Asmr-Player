package cn.tealc.asmr.ui.anywheredoor;

import atlantafx.base.theme.Styles;
import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.AnywhereDoor;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.ui.component.EmptyState;
import cn.tealc.asmr.ui.library.StorageSourceTreeItem;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.ui.dialog.DialogHandle;
import cn.tealc.asmr.ui.dialog.DialogSpec;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.utils.CssLoader;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
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

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;

/** 快捷入口卡片流及创建、编辑、删除弹窗。 */
public class AnywhereDoorView implements FXMLView<AnywhereDoorViewModel>, Initializable {
    @InjectViewModel private AnywhereDoorViewModel viewModel;
    @FXML private BorderPane root;
    @FXML private FlowPane cards;
    @FXML private ScrollPane scroll;
    @FXML private Label count;
    @FXML private EmptyState empty;
    @FXML private Label message;
    private final ListChangeListener<AnywhereDoor> entriesChanged = change -> renderCards();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        root.getStylesheets().add(CssLoader.getCss(CssLoader.mediaLibrary));
        bindMessage(message);
        empty.setIcon(Material2OutlinedMZ.STAR_BORDER);
        empty.setTitle("暂无快捷入口");
        empty.setDescription("创建文件夹入口后，会显示在这里。");
        empty.setMouseTransparent(true);
        empty.setVisible(false);
        empty.setManaged(false);
        scroll.viewportBoundsProperty().addListener((obs, before, bounds) ->
                cards.setPrefWrapLength(Math.max(1, bounds.getWidth() - 8)));
        viewModel.getPathList().addListener(new WeakListChangeListener<>(entriesChanged));
        renderCards();
    }

    private void renderCards() {
        cards.getChildren().clear();
        for (int index = 0; index < viewModel.getPathList().size(); index++) {
            cards.getChildren().add(createCard(viewModel.getPathList().get(index), index));
        }
        boolean noEntries = viewModel.getPathList().isEmpty();
        empty.setManaged(noEntries);
        empty.setVisible(noEntries);
        count.setText(viewModel.getPathList().size() + " 个");
    }

    private Node createCard(AnywhereDoor entry, int index) {
        FontIcon icon = new FontIcon(iconLiteral(entry.getType()));
        icon.getStyleClass().add("storage-source-kind-icon");
        StackPane iconBox = new StackPane(icon);
        iconBox.getStyleClass().addAll("storage-source-icon-box",
                entry.getType() == AsmrKeyType.ONLINE ? "storage-source-icon-remote" : "storage-source-icon-local");
        Label name = new Label(displayName(entry));
        name.getStyleClass().add("storage-source-name");
        name.setMaxWidth(Double.MAX_VALUE);
        name.setTooltip(new Tooltip(displayName(entry)));
        Label type = new Label(typeName(entry.getType()));
        type.getStyleClass().add("storage-source-kind");
        VBox identity = new VBox(4, name, type);
        identity.setMinWidth(0);
        HBox.setHgrow(identity, Priority.ALWAYS);
        Button edit = iconButton("mdoal-edit", "修改快捷入口");
        edit.setOnAction(event -> showEditor(entry));
        Button remove = iconButton("mdoal-delete_outline", "删除快捷入口");
        remove.getStyleClass().add("storage-source-delete");
        remove.setOnAction(event -> showDelete(entry));
        HBox actions = new HBox(2, edit, remove);
        HBox heading = new HBox(10, iconBox, identity, actions);
        heading.setAlignment(Pos.TOP_LEFT);

        Label key = new Label(entry.getKey());
        key.setMaxWidth(Double.MAX_VALUE);
        key.setMinWidth(0);
        key.setTooltip(new Tooltip(entry.getKey()));
        key.getStyleClass().add("shortcut-card-key");

        Button up = iconButton("mdoal-arrow_upward", "上移快捷入口");
        up.setDisable(index == 0);
        up.setOnAction(event -> move(entry, -1));
        Button down = iconButton("mdoal-arrow_downward", "下移快捷入口");
        down.setDisable(index == viewModel.getPathList().size() - 1);
        down.setOnAction(event -> move(entry, 1));
        HBox footer = new HBox(4, key, up, down);
        HBox.setHgrow(key, Priority.ALWAYS);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("shortcut-card-footer");

        VBox card = new VBox(6, heading, footer);
        card.getStyleClass().addAll("storage-source-card", "shortcut-card");
        card.setMinWidth(0);
        card.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.min(280, Math.max(0, scroll.getViewportBounds().getWidth() - 8)),
                scroll.viewportBoundsProperty()));
        card.setMaxWidth(280);
        return card;
    }

    @FXML
    private void createFolder() {
        showEditor(null);
    }

    private void move(AnywhereDoor entry, int direction) {
        try {
            viewModel.move(entry, direction);
            message.setText("");
        } catch (RuntimeException error) {
            message.setText(errorMessage(error));
        }
    }

    private void showEditor(AnywhereDoor existing) {
        if (existing == null || existing.getType() == AsmrKeyType.FOLDER || existing.getType() == AsmrKeyType.ONLINE) {
            showDirectoryEditor(existing);
            return;
        }
        TextField key = new TextField(existing.getKey());
        key.setId("shortcut-key");
        key.setPromptText("入口目标");
        key.setMaxWidth(Double.MAX_VALUE);
        TextField alias = aliasField(existing);
        Label error = formMessage();
        VBox form = new VBox(14, field("类型", new Label(typeName(existing.getType()))),
                field("入口目标", key), field("别名", alias));
        showEditorDialog(existing, form, error, 220,
                () -> viewModel.update(existing, key.getText(), alias.getText()));
    }

    private void showDirectoryEditor(AnywhereDoor existing) {
        ComboBox<MediaLibrary> libraryBox = new ComboBox<>();
        libraryBox.setId("shortcut-library");
        libraryBox.setPromptText("选择媒体库");
        libraryBox.setMaxWidth(Double.MAX_VALUE);
        libraryBox.getStyleClass().add("media-library-form-combo");
        Label error = formMessage();
        try {
            libraryBox.getItems().setAll(viewModel.getLibraries());
            if (libraryBox.getItems().isEmpty()) error.setText("暂无媒体库");
        } catch (RuntimeException failure) {
            error.setText(errorMessage(failure));
        }
        TreeView<AsmrFile> tree = new TreeView<>();
        tree.setId("shortcut-directory-tree");
        tree.setPrefHeight(160);
        tree.setMaxHeight(160);
        tree.setMinHeight(140);
        tree.disableProperty().bind(libraryBox.valueProperty().isNull());
        TextField selectedPath = new TextField();
        selectedPath.setId("shortcut-key");
        selectedPath.setEditable(false);
        selectedPath.setPromptText("尚未选择目录");
        tree.getSelectionModel().selectedItemProperty().addListener((obs, before, item) -> {
            selectedPath.setText(item == null || item.getValue() == null ? "" : item.getValue().getFilepath());
            selectedPath.setTooltip(item == null || item.getValue() == null ? null : new Tooltip(item.getValue().getFilepath()));
        });
        libraryBox.valueProperty().addListener((obs, before, library) -> {
            selectedPath.clear();
            showLibraryRoot(library, tree, error);
            if (existing != null && library != null && library.getId().equals(existing.getMediaLibraryId())) {
                selectedPath.setText(existing.getKey());
            }
        });
        if (existing != null && existing.getMediaLibraryId() != null) {
            libraryBox.getItems().stream().filter(library -> library.getId().equals(existing.getMediaLibraryId()))
                    .findFirst().ifPresent(libraryBox::setValue);
        }
        Button refresh = iconButton("mdomz-refresh", "刷新目录");
        refresh.disableProperty().bind(libraryBox.valueProperty().isNull());
        refresh.setOnAction(event -> {
            selectedPath.clear();
            showLibraryRoot(libraryBox.getValue(), tree, error);
        });
        Label directoryLabel = new Label("目录");
        directoryLabel.getStyleClass().add("form-field-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox directoryHeading = new HBox(8, directoryLabel, spacer, refresh);
        directoryHeading.setAlignment(Pos.CENTER_LEFT);
        TextField alias = aliasField(existing);
        VBox form = new VBox(10, field("媒体库", libraryBox),
                new VBox(5, directoryHeading, tree, selectedPath), field("别名", alias));
        showEditorDialog(existing, form, error, 410, () -> {
            if (libraryBox.getValue() == null) throw new IllegalArgumentException("请先选择媒体库");
            if (selectedPath.getText() == null || selectedPath.getText().isBlank()) {
                throw new IllegalArgumentException("请选择目标目录");
            }
            if (existing == null) viewModel.createFolder(libraryBox.getValue(), selectedPath.getText(), alias.getText());
            else viewModel.updateFolder(existing, libraryBox.getValue(), selectedPath.getText(), alias.getText());
        });
    }

    private void showLibraryRoot(MediaLibrary library, TreeView<AsmrFile> tree, Label error) {
        tree.getSelectionModel().clearSelection();
        tree.setRoot(null);
        error.setText("");
        if (library == null) return;
        try {
            var source = viewModel.getDirectorySource(library);
            AsmrFile directory = new AsmrFile(library.toString(), library.getPath(), "dir", true, library.toString(), 0, null);
            AtomicReference<TreeItem<AsmrFile>> currentRoot = new AtomicReference<>();
            currentRoot.set(new StorageSourceTreeItem(directory, source, failure -> {
                if (tree.getRoot() == currentRoot.get()) error.setText("读取目录失败：" + errorMessage(failure));
            }));
            tree.setRoot(currentRoot.get());
            currentRoot.get().setExpanded(true);
        } catch (RuntimeException failure) {
            error.setText(errorMessage(failure));
        }
    }

    private TextField aliasField(AnywhereDoor existing) {
        TextField alias = new TextField(existing == null ? "" : existing.getName());
        alias.setId("shortcut-alias");
        alias.setPromptText("可选，留空使用默认名称");
        return alias;
    }

    private Label formMessage() {
        Label error = new Label();
        error.getStyleClass().add("form-message");
        error.setWrapText(true);
        bindMessage(error);
        return error;
    }

    private void showEditorDialog(AnywhereDoor existing, VBox form, Label error, double height, Runnable submit) {
        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);
        formScroll.getStyleClass().add("form-scroll");
        formScroll.setPrefViewportHeight(height);
        VBox body = new VBox(12, formScroll, error);
        if (root.getScene() != null) {
            body.prefWidthProperty().bind(Bindings.min(560, Bindings.max(180,
                    root.getScene().widthProperty().subtract(100))));
            formScroll.maxHeightProperty().bind(Bindings.max(100,
                    root.getScene().heightProperty().subtract(250)));
        } else {
            body.setPrefWidth(560);
        }
        // 走 Dialogs 体系，与其他弹窗共用外壳与按钮外观
        DialogSpec spec = DialogSpec.builder()
                .title(existing == null ? "创建文件夹快捷入口" : "修改快捷入口")
                .content(body)
                .stylesheet(CssLoader.getCss(CssLoader.mediaLibrary))
                .action(existing == null ? "创建" : "保存", "default-button", handle -> {
                    try {
                        submit.run();
                        message.setText("");
                        handle.close();
                    } catch (RuntimeException failure) {
                        error.setText(errorMessage(failure));
                    }
                })
                .action("取消", "cancel-button", DialogHandle::close)
                .build();
        Dialogs.show(spec);
    }

    private void showDelete(AnywhereDoor entry) {
        Label description = new Label("确定删除快捷入口「" + displayName(entry) + "」？文件与作品数据不会被删除。");
        description.setWrapText(true);
        Label error = new Label();
        error.setWrapText(true);
        error.getStyleClass().add("form-message");
        bindMessage(error);
        VBox body = new VBox(12, description, error);
        body.setPrefWidth(400);
        if (root.getScene() != null) body.maxWidthProperty().bind(
                Bindings.max(180, root.getScene().widthProperty().subtract(100)));
        Alerts.danger().title("删除快捷入口")
                .content(body)
                .stylesheet(CssLoader.getCss(CssLoader.mediaLibrary))
                .button("删除", "default-button", handle -> {
                    try {
                        viewModel.remove(entry);
                        message.setText("");
                        handle.close();
                    } catch (RuntimeException failure) {
                        error.setText(errorMessage(failure));
                    }
                })
                .cancel()
                .show();
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
        button.getStyleClass().addAll(Styles.FLAT, "button-icon", "storage-source-card-action");
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        return button;
    }

    private void bindMessage(Label label) {
        label.visibleProperty().bind(label.textProperty().isNotEmpty());
        label.managedProperty().bind(label.visibleProperty());
    }

    private String errorMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank() ? "操作失败" : error.getMessage();
    }

    static String displayName(AnywhereDoor entry) {
        return entry.getDisplayName();
    }

    private String typeName(AsmrKeyType type) {
        if (type == null) return "快捷入口";
        return switch (type) {
            case FOLDER -> "文件夹";
            case ONLINE -> "网络目录";
            case CATEGORY -> "分类";
            case VA -> "声优";
            case CIRCLE -> "社团";
            case TAG -> "标签";
            default -> "快捷入口";
        };
    }

    private String iconLiteral(AsmrKeyType type) {
        if (type == null) return "mdoal-label";
        return switch (type) {
            case FOLDER -> "mdoal-folder_open";
            case ONLINE -> "mdoal-cloud";
            case CATEGORY -> "mdomz-outlined_flag";
            case VA -> "mdomz-person";
            case CIRCLE -> "mdoal-local_fire_department";
            case TAG -> "mdoal-loyalty";
            default -> "mdoal-label";
        };
    }
}
