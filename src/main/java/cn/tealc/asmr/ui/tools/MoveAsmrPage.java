package cn.tealc.asmr.ui.tools;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.FileOperationService;
import cn.tealc.asmr.service.MediaLibraryService;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.source.StorageType;
import cn.tealc.asmr.source.local.LocalMediaSource;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogPage;
import cn.tealc.asmr.ui.library.StorageSourceTreeItem;
import cn.tealc.asmr.utils.CssLoader;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.List;
import java.util.Objects;

/**
 * 作品移动弹窗，以媒体库目录树选择目标位置，确认后把整个作品文件夹移动过去。
 * <p>
 * 目录树通过 {@link MediaSource} 懒加载，本地与 OpenList 来源共用同一套交互：
 * 本地作品可移动到任意本地媒体库，OpenList 作品只能移动到同一存储源下的媒体库。
 * 实现 {@link DialogPage}，标题、正文与"移动到此处/取消"按钮由 {@code Dialogs} 分槽承载；
 * 未选中目录或选中的是根节点时禁用移动按钮，移动成功后广播 {@code MAIN_GRID_WORK_REMOVE}。
 *
 * @author Leck
 */
public class MoveAsmrPage implements DialogPage {
    private final FileOperationService fileOperationService = DI.get(FileOperationService.class);
    private final PageHeader header;
    private final VBox content;
    private final List<DialogAction> actions;
    private final AsmrWork work;

    @Override
    public PageHeader header() {
        return header;
    }

    @Override
    public Node content() {
        return content;
    }

    @Override
    public List<DialogAction> actions() {
        return actions;
    }

    @Override
    public double width() {
        return 760;
    }

    @Override
    public List<String> stylesheets() {
        return List.of(CssLoader.getCss(CssLoader.moveAsmr));
    }


    /**
     * 构建移动界面：用与作品同来源的媒体库生成目录树、目标位置标签与操作按钮。
     *
     * @param work 待移动的作品
     */
    public MoveAsmrPage(AsmrWork work) {
        this.work = work;

        header = new PageHeader();
        header.setIcon(Material2OutlinedMZ.MOVE_TO_INBOX);
        header.setTitle("移动作品");
        header.setSubtitle("选择目标目录后，将整个作品文件夹移动到该位置。");

        List<MediaLibrary> libraries = availableLibraries(work);

        TreeView<AsmrFile> treeView = new TreeView<>();
        TreeItem<AsmrFile> rootItem = new TreeItem<>(
                new AsmrFile("媒体库", "/", "dir", true, "媒体库", 0, null));
        rootItem.setExpanded(true);
        Label message = new Label();
        message.getStyleClass().add("move-tree-hint");
        message.setWrapText(true);

        for (MediaLibrary library : libraries) {
            try {
                MediaSource source = sourceFor(library);
                AsmrFile directory = new AsmrFile(library.getName(), library.getPath(),
                        "dir", true, library.getName(), 0, null);
                rootItem.getChildren().add(new StorageSourceTreeItem(directory, source,
                        error -> message.setText("读取目录失败：" + errorMessage(error))));
            } catch (RuntimeException error) {
                // 单个媒体库不可用时不阻断其他目标
                message.setText("媒体库「" + library.getName() + "」不可用：" + errorMessage(error));
            }
        }

        treeView.setRoot(rootItem);
        treeView.getStyleClass().add("move-tree-view");
        treeView.setCellFactory(view -> new TreeCell<>() {
            @Override
            protected void updateItem(AsmrFile file, boolean empty) {
                super.updateItem(file, empty);
                if (!empty && file != null) {
                    setText(file.getFilename());
                    FontIcon folderIcon = new FontIcon(getTreeItem() != null && getTreeItem().getParent() == null
                            ? Material2OutlinedAL.FOLDER_SPECIAL
                            : Material2OutlinedAL.FOLDER);
                    folderIcon.getStyleClass().add("move-tree-icon");
                    setGraphic(folderIcon);
                } else {
                    setText(null);
                    setGraphic(null);
                }
            }
        });
        if (libraries.isEmpty()) {
            message.setText(work.getStorageType() != null && work.getStorageType() == StorageType.OPENLIST.getCode()
                    ? "当前 OpenList 存储源下没有可用的媒体库"
                    : "没有可用的媒体库");
        }

        Label targetMediaLibrary = new Label();
        targetMediaLibrary.getStyleClass().add("move-target");
        targetMediaLibrary.setWrapText(true);
        targetMediaLibrary.textProperty().bind(Bindings.createStringBinding(() -> {
            TreeItem<AsmrFile> selectedItem = treeView.getSelectionModel().getSelectedItem();
            return selectedItem == null || selectedItem.getParent() == null
                    ? "尚未选择目标目录" : selectedItem.getValue().getFilepath();
        }, treeView.getSelectionModel().selectedItemProperty()));
        Label targetLabel = new Label("目标位置");
        targetLabel.getStyleClass().add("move-target-label");
        VBox targetCard = new VBox(5, targetLabel, targetMediaLibrary);
        targetCard.getStyleClass().add("move-target-card");

        Label treeHint = new Label("可展开文件夹以选择更深层目录");
        treeHint.getStyleClass().add("move-tree-hint");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox treeHeader = new HBox(8, new Label("选择目标目录"), spacer, treeHint);
        treeHeader.getStyleClass().add("move-tree-header");
        ((Label) treeHeader.getChildren().getFirst()).getStyleClass().add("move-tree-title");

        content = new VBox(14, targetCard, treeHeader, treeView, message);
        content.setPrefSize(720, 420);
        VBox.setVgrow(treeView, Priority.ALWAYS);

        DialogAction move = DialogAction.of("移动到此处", "default-button", handle -> {
            TreeItem<AsmrFile> fileTreeItem = treeView.getSelectionModel().getSelectedItem();
            if (fileTreeItem == null || fileTreeItem.getParent() == null) {
                Alerts.warning().title("提醒").message("请选择移动的目标位置").cancel("确定").show();
                return;
            }
            AsmrFile targetDirectory = fileTreeItem.getValue();
            MediaLibrary targetLibrary = libraryFor(libraries, targetDirectory.getFilepath());
            if (targetLibrary == null) {
                Alerts.warning().title("提醒").message("请选择媒体库中的目录").cancel("确定").show();
                return;
            }
            handle.setBusy(true);
            message.setText("正在移动，请稍候…");
            Thread.startVirtualThread(() -> {
                FileOperationService.MoveWorkResult result = fileOperationService.moveWork(
                        work, targetDirectory.getFilepath(), targetLibrary.getId(),
                        targetLibrary.getStorageSourceId());
                Platform.runLater(() -> {
                    if (!result.success()) {
                        handle.setBusy(false);
                        message.setText(result.message());
                        Alerts.warning().title("提醒").message(result.message()).cancel("确定").show();
                        return;
                    }
                    EventBus.publish(NotificationKey.MAIN_GRID_WORK_REMOVE, work, false);
                    handle.close();
                });
            });
        }).defaultButton()
                .graphic(new FontIcon(Material2OutlinedMZ.MOVE_TO_INBOX))
                .disabledWhen(Bindings.createBooleanBinding(() -> {
                    TreeItem<AsmrFile> selectedItem = treeView.getSelectionModel().getSelectedItem();
                    return selectedItem == null || selectedItem.getParent() == null;
                }, treeView.getSelectionModel().selectedItemProperty()));

        actions = List.of(move, DialogAction.cancel("取消"));
    }

    /**
     * 筛选可作为移动目标的媒体库。
     * <p>
     * 本地作品可使用全部本地媒体库；OpenList 作品只能使用同一存储源下的媒体库，
     * 因为一次移动只能在一个 OpenList 客户端内完成。
     */
    private static List<MediaLibrary> availableLibraries(AsmrWork work) {
        if (work == null) {
            return List.of();
        }
        return DI.get(MediaLibraryService.class).getAll().stream()
                .filter(library -> isSameSource(work, library))
                .toList();
    }

    private static boolean isSameSource(AsmrWork work, MediaLibrary library) {
        if (work.getStorageType() == null || library.getStorageType() == null) {
            return false;
        }
        if (work.getStorageType() == StorageType.LOCAL.getCode()) {
            return library.getStorageType() == StorageType.LOCAL;
        }
        return Objects.equals(work.getStorageSourceId(), library.getStorageSourceId());
    }

    private static MediaSource sourceFor(MediaLibrary library) {
        if (library.getStorageSourceId() != null) {
            return MediaSourceFactory.forStorageSourceId(library.getStorageSourceId());
        }
        if (library.getStorageType() == StorageType.LOCAL) {
            return LocalMediaSource.INSTANCE;
        }
        throw new IllegalStateException("媒体库未关联存储源");
    }

    /**
     * 判断目标路径属于哪个媒体库；媒体库路径互不重叠，取最长匹配即可。
     */
    private static MediaLibrary libraryFor(List<MediaLibrary> libraries, String path) {
        MediaLibrary match = null;
        for (MediaLibrary library : libraries) {
            if (!isSameOrChild(path, library.getPath())) {
                continue;
            }
            if (match == null || library.getPath().length() > match.getPath().length()) {
                match = library;
            }
        }
        return match;
    }

    private static boolean isSameOrChild(String path, String parentPath) {
        String normalizedPath = normalizePath(path);
        String normalizedParent = normalizePath(parentPath);
        return normalizedPath.equals(normalizedParent)
                || normalizedPath.startsWith(normalizedParent.endsWith("/")
                ? normalizedParent : normalizedParent + "/");
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.replace('\\', '/').replaceAll("/+$", "");
    }

    private static String errorMessage(Throwable error) {
        return error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? "操作失败" : error.getMessage();
    }
}
