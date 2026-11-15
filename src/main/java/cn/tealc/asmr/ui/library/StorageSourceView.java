package cn.tealc.asmr.ui.library;

import atlantafx.base.theme.Styles;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.StorageSource;
import cn.tealc.asmr.model.StorageSourceStatus;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.mvvm.JavaView;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.source.StorageType;
import cn.tealc.asmr.source.openlist.OpenListMediaSource;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.ui.dialog.DialogHandle;
import cn.tealc.asmr.ui.dialog.DialogSpec;
import cn.tealc.asmr.ui.component.EmptyState;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.utils.CssLoader;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
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
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

/** 存储源管理界面。 */
public class StorageSourceView extends BorderPane implements JavaView<StorageSourceViewModel> {
    @InjectViewModel private StorageSourceViewModel viewModel;
    private final FlowPane cards = new FlowPane(12, 12);
    private final EmptyState empty = new EmptyState();
    private final Label count = new Label();
    private ScrollPane scroll;

    public void initialize() {
        setPadding(new Insets(12));
        Label title = new Label("存储源");
        title.getStyleClass().add("storage-source-heading");
        count.getStyleClass().add("storage-source-secondary");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button refresh = iconButton("mdomz-refresh", "刷新");
        refresh.setOnAction(event -> viewModel.refresh());
        Button create = new Button("创建存储源", new FontIcon("mdoal-add"));
        create.getStyleClass().add(Styles.ACCENT);
        create.setOnAction(event -> showEditor(null));
        HBox toolbar = new HBox(10, title, count, spacer, refresh, create);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 14, 0));
        setTop(toolbar);

        cards.getStyleClass().add("storage-source-cards");
        empty.setIcon(Material2OutlinedMZ.STORAGE);
        empty.setTitle("暂无存储源");
        empty.setDescription("创建本机存储或 OpenList 存储源后，会显示在这里。");
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
        viewModel.getSources().addListener((ListChangeListener<StorageSource>) change -> renderCards());
        renderCards();
    }

    private void renderCards() {
        cards.getChildren().setAll(viewModel.getSources().stream().map(this::createCard).toList());
        boolean noSources = viewModel.getSources().isEmpty();
        empty.setManaged(noSources);
        empty.setVisible(noSources);
        count.setText(viewModel.getSources().size() + " 个");
    }

    private Node createCard(StorageSource source) {
        FontIcon kindIcon = new FontIcon(source.getType() == StorageType.LOCAL ? "mdomz-storage" : "mdoal-cloud");
        kindIcon.getStyleClass().add("storage-source-kind-icon");
        StackPane iconBox = new StackPane(kindIcon);
        iconBox.getStyleClass().add(source.getType() == StorageType.LOCAL
                ? "storage-source-icon-local" : "storage-source-icon-remote");
        iconBox.getStyleClass().add("storage-source-icon-box");
        Label name = new Label(source.getName());
        name.getStyleClass().add("storage-source-name");
        name.setMaxWidth(Double.MAX_VALUE);
        name.setTooltip(new Tooltip(source.getName()));
        Label kind = new Label(source.getType() == StorageType.LOCAL ? "本机存储" : "OpenList");
        kind.getStyleClass().add("storage-source-kind");
        VBox identity = new VBox(4, name, kind);
        identity.setMinWidth(0);
        HBox.setHgrow(identity, Priority.ALWAYS);
        Button edit = iconButton("mdoal-edit", "修改存储源");
        edit.setOnAction(event -> showEditor(source));
        Button remove = iconButton("mdoal-delete_outline", "删除存储源");
        edit.getStyleClass().add("storage-source-card-action");
        remove.getStyleClass().addAll("storage-source-card-action", "storage-source-delete");
        remove.setOnAction(event -> removeSource(source));
        HBox actions = new HBox(2, edit, remove);
        HBox heading = new HBox(10, iconBox, identity, actions);
        heading.setAlignment(Pos.TOP_LEFT);

        Label status = new Label(statusText(source.getStatus()));
        status.getStyleClass().add(source.getStatus() == StorageSourceStatus.ACTIVE
                ? "storage-source-status-active" : "storage-source-status-warning");
        Label pathTitle = new Label("目标路径");
        pathTitle.getStyleClass().add("storage-source-caption");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox pathHeading = new HBox(8, pathTitle, spacer, status);
        pathHeading.setAlignment(Pos.CENTER_LEFT);
        Label path = new Label(source.getTargetPath());
        path.setMaxWidth(Double.MAX_VALUE);
        path.setMinWidth(0);
        path.setTooltip(new Tooltip(source.getTargetPath()));
        path.getStyleClass().add("storage-source-path");
        VBox details = new VBox(5, pathHeading, path);
        details.getStyleClass().add("storage-source-details");
        VBox card = new VBox(14, heading, details);
        card.getStyleClass().add("storage-source-card");
        card.setMinWidth(0);
        card.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.min(320, Math.max(0, scroll.getViewportBounds().getWidth() - 8)),
                scroll.viewportBoundsProperty()));
        card.setMaxWidth(320);
        return card;
    }

    private String statusText(StorageSourceStatus status) {
        return switch (status) {
            case ACTIVE -> "已启用";
            case AUTH_REQUIRED -> "需要令牌";
            case AUTH_EXPIRED -> "令牌已失效";
            case UNAVAILABLE -> "不可用";
            case DISABLED -> "已停用";
        };
    }

    private Button iconButton(String icon, String description) {
        Button button = new Button(null, new FontIcon(icon));
        button.getStyleClass().addAll(Styles.FLAT, "button-icon");
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        return button;
    }

    private void removeSource(StorageSource source) {
        Label description = new Label("确定删除存储源「" + source.getName() + "」？存储中的文件不会被删除。");
        description.setWrapText(true);
        Label message = messageLabel();
        VBox body = new VBox(12, description, message);
        body.setPrefWidth(400);
        Alerts.danger().title("删除存储源")
                .content(body)
                .stylesheet(CssLoader.getCss(CssLoader.mediaLibrary))
                .button("删除", "default-button", handle -> {
                    try {
                        viewModel.remove(source);
                        handle.close();
                    } catch (RuntimeException e) {
                        message.setText(errorMessage(e));
                    }
                })
                .cancel()
                .show();
    }

    private void showEditor(StorageSource existing) {
        ComboBox<StorageType> type = new ComboBox<>();
        type.getItems().setAll(StorageType.LOCAL, StorageType.OPENLIST);
        type.setConverter(new StringConverter<>() {
            @Override public String toString(StorageType value) {
                return value == null ? "" : value == StorageType.LOCAL ? "本机存储" : "OpenList";
            }
            @Override public StorageType fromString(String value) { return null; }
        });
        type.setMaxWidth(Double.MAX_VALUE);
        type.setValue(existing == null ? StorageType.LOCAL : existing.getType());
        type.setDisable(existing != null);

        TextField name = new TextField(existing == null ? "" : existing.getName());
        name.setPromptText("显示名称");
        TextField localPath = new TextField(existing != null && existing.getType() == StorageType.LOCAL
                ? existing.getTargetPath() : "");
        localPath.setPromptText("选择本机目录");
        Button choose = iconButton("mdoal-folder_open", "选择本机目录");
        choose.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择存储源目录");
            java.io.File selected = chooser.showDialog(getScene() == null ? null : getScene().getWindow());
            if (selected != null) localPath.setText(selected.getAbsolutePath());
        });
        HBox localInput = new HBox(8, localPath, choose);
        HBox.setHgrow(localPath, Priority.ALWAYS);
        VBox localFields = field("目标路径", localInput);

        TextField baseUrl = new TextField(existing != null && existing.getType() == StorageType.OPENLIST
                ? viewModel.getOpenListBaseUrl(existing) : "");
        baseUrl.setPromptText("https://openlist.example.com:5244");
        PasswordField token = new PasswordField();
        token.setPromptText(existing == null ? "OpenList 令牌" : "留空以保留现有令牌");
        TextField targetPath = new TextField(existing != null && existing.getType() == StorageType.OPENLIST
                ? existing.getTargetPath() : "/");
        targetPath.setPromptText("远程目标路径");
        TreeView<AsmrFile> tree = new TreeView<>();
        tree.setPrefHeight(160);
        tree.setMaxHeight(160);
        tree.setVisible(false);
        tree.setManaged(false);
        Runnable clearBrowser = () -> {
            tree.setRoot(null);
            tree.setVisible(false);
            tree.setManaged(false);
        };
        baseUrl.textProperty().addListener((obs, before, after) -> clearBrowser.run());
        token.textProperty().addListener((obs, before, after) -> clearBrowser.run());
        tree.getSelectionModel().selectedItemProperty().addListener((obs, before, selected) -> {
            if (selected != null && selected.getValue() != null) targetPath.setText(selected.getValue().getFilepath());
        });
        Button browse = new Button("测试并浏览", new FontIcon("mdoal-cloud"));
        Label message = messageLabel();
        VBox remoteFields = new VBox(12, field("OpenList 地址", baseUrl), field("令牌", token),
                field("目标路径", new HBox(8, targetPath, browse)), tree);
        HBox.setHgrow(targetPath, Priority.ALWAYS);
        VBox form = new VBox(12, field("类型", type), field("名称", name), localFields, remoteFields);
        form.setPrefWidth(480);
        form.setPadding(new Insets(4, 4, 4, 2));
        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);
        formScroll.getStyleClass().add("form-scroll");
        if (getScene() != null) {
            formScroll.maxHeightProperty().bind(Bindings.max(120, getScene().heightProperty().subtract(210)));
        } else {
            formScroll.setMaxHeight(420);
        }
        Runnable updateVisibility = () -> {
            boolean local = type.getValue() == StorageType.LOCAL;
            localFields.setVisible(local);
            localFields.setManaged(local);
            remoteFields.setVisible(!local);
            remoteFields.setManaged(!local);
            message.setText("");
            formScroll.setPrefViewportHeight(local ? 220 : tree.isVisible() ? 460 : 390);
        };
        type.valueProperty().addListener((obs, before, after) -> updateVisibility.run());
        updateVisibility.run();

        Button save = new Button(existing == null ? "创建" : "保存");
        save.getStyleClass().add("default-button");
        VBox body = new VBox(8, formScroll, message);

        // 改用 Dialogs 体系，外观与其他弹窗统一；句柄用于判断弹窗是否仍在显示
        DialogHandle[] dialogRef = new DialogHandle[1];
        DialogSpec spec = DialogSpec.builder()
                .title(existing == null ? "创建存储源" : "修改存储源")
                .content(body)
                .action(existing == null ? "创建" : "保存", "default-button", handle -> {
                    try {
                        if (type.getValue() == StorageType.LOCAL) {
                            if (existing == null) viewModel.createLocal(name.getText(), localPath.getText());
                            else viewModel.updateLocal(existing, name.getText(), localPath.getText());
                        } else {
                            if (existing == null) viewModel.createOpenList(name.getText(), baseUrl.getText(), token.getText(), targetPath.getText());
                            else viewModel.updateOpenList(existing, name.getText(), baseUrl.getText(), token.getText(), targetPath.getText());
                        }
                        handle.close();
                    } catch (RuntimeException e) {
                        message.setText(errorMessage(e));
                    }
                })
                .action("取消", "cancel-button", DialogHandle::close)
                .build();
        browse.setOnAction(event -> {
            String url = baseUrl.getText();
            String secret = token.getText();
            browse.setDisable(true);
            save.setDisable(true);
            baseUrl.setDisable(true);
            token.setDisable(true);
            type.setDisable(true);
            message.setText("正在连接...");
            Task<OpenListMediaSource> task = new Task<>() {
                @Override protected OpenListMediaSource call() {
                    return viewModel.testOpenList(url, secret, existing);
                }
            };
            task.setOnSucceeded(done -> {
                if (dialogRef[0] == null || !dialogRef[0].isShowing()) return;
                browse.setDisable(false);
                save.setDisable(false);
                baseUrl.setDisable(false);
                token.setDisable(false);
                type.setDisable(existing != null);
                AsmrFile root = new AsmrFile("/", "/", "dir", true, "/", 0, null);
                TreeItem<AsmrFile> rootItem = new StorageSourceTreeItem(root, task.getValue());
                tree.setRoot(rootItem);
                rootItem.setExpanded(true);
                tree.setManaged(true);
                tree.setVisible(true);
                message.setText("");
                formScroll.setPrefViewportHeight(460);
            });
            task.setOnFailed(done -> {
                if (dialogRef[0] == null || !dialogRef[0].isShowing()) return;
                browse.setDisable(false);
                save.setDisable(false);
                baseUrl.setDisable(false);
                token.setDisable(false);
                type.setDisable(existing != null);
                message.setText(errorMessage(task.getException()));
            });
            Thread.startVirtualThread(task);
        });
        dialogRef[0] = Dialogs.show(spec);
    }

    private VBox field(String title, Node input) {
        Label label = new Label(title);
        label.getStyleClass().add("form-field-label");
        VBox box = new VBox(6, label, input);
        box.getStyleClass().add("form-field");
        return box;
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
        return error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? "操作失败，请检查输入或连接。" : error.getMessage();
    }
}
