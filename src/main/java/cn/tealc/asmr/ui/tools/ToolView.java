package cn.tealc.asmr.ui.tools;

import atlantafx.base.controls.Tab;
import atlantafx.base.controls.TabLine;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.factory.ViewFactory;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.DuplicatedWork;
import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.service.MediaLibraryService;
import cn.tealc.asmr.service.BackgroundService;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.source.StorageType;
import cn.tealc.asmr.task.DataCleanupTask;
import cn.tealc.asmr.task.DuplicateWorkRemovalTask;
import cn.tealc.asmr.task.MissingWorkCleanupTask;
import cn.tealc.asmr.task.background.TaskManager;
import cn.tealc.asmr.task.RenameService;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.work.cells.DuplicateWorkCell;
import cn.tealc.asmr.ui.work.WorkDetailView;
import cn.tealc.asmr.ui.work.WorkDetailViewModel;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.ui.dialog.DialogSpec;
import cn.tealc.asmr.ui.dialog.Dialogs;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;
import cn.tealc.asmr.utils.FileUtils;
import cn.tealc.asmr.utils.CoverUtils;
import cn.tealc.asmr.utils.WorkCodeUtils;
import cn.tealc.asmr.mvvm.FXMLView;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.mvvm.ViewTuple;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.concurrent.WorkerStateEvent;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.ResourceBundle;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工具箱页面，用 {@link TabLine} 分隔重命名、重复作品检测与数据清理三块功能。
 * <p>
 * 通过 {@link #showTab(int)} 切换正文容器内容；重复检测表格的单元格复用 {@link DuplicateWorkCell}，
 * 选择状态由 {@link DuplicateWorkSelection} 统一管理，删除经确认对话框后交给后台任务执行。
 *
 * @author Leck
 */
public class ToolView implements FXMLView<ToolViewModel>, Initializable {
    private final WorkService workService = DI.get(WorkService.class);
    private final MediaLibraryService mediaLibraryService = DI.get(MediaLibraryService.class);
    private final TaskManager taskManager = DI.get(TaskManager.class);
    @InjectViewModel
    private ToolViewModel viewModel;

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private ComboBox<String> encryptionComboBox;

    @FXML
    private TextField formatSampleTextField;

    @FXML
    private ComboBox<MediaLibrary> mediaLibraryComboBoxForRename;

    @FXML
    private Button openMediaLibraryBtn;

    @FXML
    private CheckBox renameEncryptionModel;

    @FXML
    private Label renameSampleLabel;

    @FXML
    private CheckBox renameTitleCNModel;

    @FXML
    private PageHeader pageHeader;
    @FXML
    private TabLine tabPane;
    @FXML
    private StackPane contentPane;
    @FXML
    private ScrollPane renamePane;
    @FXML
    private VBox duplicatePane;
    @FXML
    private ScrollPane cleanupPane;
    @FXML
    private Button checkDuplicateBtn;
    @FXML
    private TableView<DuplicatedWork> duplicateTableView;
    @FXML
    private TableColumn<DuplicatedWork, List<AsmrWork>> duplicatedWorkCol;
    @FXML
    private TableColumn<DuplicatedWork, AsmrWork> workCol;

    @FXML
    private Label duplicateNumTip;
    @FXML
    private Label duplicateSelectedTip;
    @FXML
    private Button removeDuplicatesBtn;
    @FXML
    private Button selectAllDuplicatesBtn;
    @FXML
    private Button clearDuplicateSelectionBtn;
    private final DuplicateWorkSelection duplicateSelection = new DuplicateWorkSelection();
    private final BooleanProperty removingDuplicates = new SimpleBooleanProperty();



    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        pageHeader.setIcon(Material2OutlinedAL.BUILD);
        pageHeader.setTitle("工具箱");
        pageHeader.setSubtitle("集中处理媒体库命名、重复作品检测与数据清理。");
        pageHeader.setSeparatorVisible(false);

        tabPane.setTabClosingPolicy(Tab.ClosingPolicy.NO_TABS);
        tabPane.getSelectionModel().selectFirst();
        showTab(0);
        tabPane.getSelectionModel().selectedIndexProperty().addListener((observable, oldIndex, newIndex) ->
                showTab(newIndex.intValue()));

        /*==========================重命名相关=================================*/
        mediaLibraryComboBoxForRename.setItems(viewModel.getMediaLibrariesList());
        mediaLibraryComboBoxForRename.setConverter(new StringConverter<>() {
            @Override
            public String toString(MediaLibrary mediaLibrary) {
                if (mediaLibrary != null)
                    return mediaLibrary.getPath();
                return null;
            }

            @Override
            public MediaLibrary fromString(String s) {
                return null;
            }
        });
        openMediaLibraryBtn.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            MediaLibrary library = mediaLibraryComboBoxForRename.getValue();
            return library == null || library.getStorageType() != StorageType.LOCAL;
        }, mediaLibraryComboBoxForRename.valueProperty()));
        encryptionComboBox.getItems().addAll("{【workId】}{【subtitle】}","{workId} {【subtitle】}","{workId} {title}","{workId} {title} {subtitle}","{workId}-{title}-{cv}");
        encryptionComboBox.getSelectionModel().selectedItemProperty().addListener((observableValue, s, t1) -> {
            formatSampleTextField.setText(t1);
        });
        encryptionComboBox.getSelectionModel().select(0);


        /*==========================作品重复检测相关=================================*/
        duplicateNumTip.textProperty().bind(viewModel.duplicatedTipProperty());

        checkDuplicateBtn.setOnAction(actionEvent -> {
            duplicateSelection.clear();
            viewModel.checkDuplicate();
        });
        checkDuplicateBtn.disableProperty().bind(removingDuplicates);
        duplicateTableView.disableProperty().bind(removingDuplicates);
        removeDuplicatesBtn.disableProperty().bind(removingDuplicates.or(duplicateSelection.selectedCountProperty().isEqualTo(0)));
        selectAllDuplicatesBtn.disableProperty().bind(removingDuplicates.or(Bindings.isEmpty(viewModel.getDuplicatedWorks())));
        clearDuplicateSelectionBtn.disableProperty().bind(removeDuplicatesBtn.disableProperty());
        duplicateSelectedTip.textProperty().bind(duplicateSelection.selectedCountProperty().asString("已选 %d 部"));
        selectAllDuplicatesBtn.setOnAction(event -> duplicateSelection.selectAll());
        clearDuplicateSelectionBtn.setOnAction(event -> duplicateSelection.clear());
        removeDuplicatesBtn.setOnAction(event -> confirmDuplicateRemoval(duplicateSelection.getSelectedWorks()));

        duplicateTableView.setItems(viewModel.getDuplicatedWorks());
        duplicateSelection.setItems(duplicateTableView.getItems().stream()
                .flatMap(group -> group.getDuplicatedList().stream()).toList());
        duplicateTableView.getItems().addListener((ListChangeListener<DuplicatedWork>) change ->
                duplicateSelection.setItems(duplicateTableView.getItems().stream()
                        .flatMap(group -> group.getDuplicatedList().stream()).toList()));
        duplicateTableView.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.A && event.isShortcutDown()) {
                duplicateSelection.selectAll();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                duplicateSelection.clear();
                event.consume();
            }
        });
        workCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<DuplicatedWork, AsmrWork>, ObservableValue<AsmrWork>>() {
            @Override
            public ObservableValue<AsmrWork> call(TableColumn.CellDataFeatures<DuplicatedWork, AsmrWork> duplicatedWorkAsmrWorkCellDataFeatures) {
                return duplicatedWorkAsmrWorkCellDataFeatures.getValue().originProperty();
            }
        });
        workCol.setCellFactory(new Callback<TableColumn<DuplicatedWork, AsmrWork>, TableCell<DuplicatedWork, AsmrWork>>() {
            @Override
            public TableCell<DuplicatedWork, AsmrWork> call(TableColumn<DuplicatedWork, AsmrWork> duplicatedWorkAsmrWorkTableColumn) {
                return new TableCell<>(){
                    @Override
                    protected void updateItem(AsmrWork asmrWork, boolean b) {
                        super.updateItem(asmrWork, b);
                        if (!b){
                            setText(asmrWork.getOriginWorkId() !=null ?asmrWork.getOriginWorkId():asmrWork.getWorkId());
                        }else {
                            setText(null);
                        }
                    }
                };
            }
        });
        duplicatedWorkCol.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<DuplicatedWork, List<AsmrWork>>, ObservableValue<List<AsmrWork>>>() {
            @Override
            public ObservableValue<List<AsmrWork>> call(TableColumn.CellDataFeatures<DuplicatedWork, List<AsmrWork>> duplicatedWorkListCellDataFeatures) {
                return duplicatedWorkListCellDataFeatures.getValue().duplicatedListProperty();
            }
        });
        duplicatedWorkCol.setCellFactory(new Callback<TableColumn<DuplicatedWork, List<AsmrWork>>, TableCell<DuplicatedWork, List<AsmrWork>>>() {
            @Override
            public TableCell<DuplicatedWork, List<AsmrWork>> call(TableColumn<DuplicatedWork, List<AsmrWork>> duplicatedWorkListTableColumn) {
                return new TableCell<>(){
                    private final List<DuplicateWorkCell> cells = new ArrayList<>();
                    @Override
                    protected void updateItem(List<AsmrWork> asmrWorks, boolean b) {
                        cells.forEach(DuplicateWorkCell::dispose);
                        cells.clear();
                        super.updateItem(asmrWorks, b);
                        if (!b && asmrWorks != null){
                            HBox parent=new HBox();
                            parent.setSpacing(15.0);
                            for (AsmrWork asmrWork : asmrWorks) {
                                DuplicateWorkCell cell = new DuplicateWorkCell(asmrWork, duplicateSelection, work -> {
                                    if (!duplicateSelection.getSelectedWorks().contains(work)) {
                                        duplicateSelection.select(work, false, false);
                                    }
                                    confirmDuplicateRemoval(duplicateSelection.getSelectedWorks());
                                });
                                cells.add(cell);
                                parent.getChildren().add(cell);
                            }
                            setGraphic(parent);
                        }else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });

    }

    /**
     * 按索引切换正文：1 显示重复作品面板、2 显示数据清理面板，其余（含 0）一律显示重命名面板。
     *
     * @param index 选中的 {@link TabLine} 索引
     */
    private void showTab(int index) {
        switch (index) {
            case 1 -> contentPane.getChildren().setAll(duplicatePane);
            case 2 -> contentPane.getChildren().setAll(cleanupPane);
            default -> contentPane.getChildren().setAll(renamePane);
        }
    }

    /**
     * 弹出移除重复作品的危险确认框：默认只删数据库记录，勾选后才彻底删除存储文件。
     * <p>
     * 移除期间用 {@code removingDuplicates} 禁用相关按钮，后台任务成功/失败/取消后都会复位该标志。
     *
     * @param works 当前选中的重复作品，方法内部对其做快照后再处理
     */
    private void confirmDuplicateRemoval(List<AsmrWork> works) {
        if (works.isEmpty() || removingDuplicates.get()) return;
        List<AsmrWork> snapshot = List.copyOf(works);
        Set<Integer> ids = snapshot.stream().map(AsmrWork::getId).collect(java.util.stream.Collectors.toSet());
        long wholeGroups = viewModel.getDuplicatedWorks().stream()
                .filter(group -> group.getDuplicatedList().stream().allMatch(work -> ids.contains(work.getId()))).count();
        Label hint = new Label("默认仅移除数据库记录，作品文件将保留。"
                + (wholeGroups > 0 ? "\n注意：其中 " + wholeGroups + " 组将移除全部版本。" : ""));
        hint.setWrapText(true);
        TextArea targets = new TextArea(snapshot.stream()
                .map(work -> String.format("%s [存储源 %s] %s", work.getWorkId(), work.getStorageSourceId(), work.getFilepath()))
                .collect(java.util.stream.Collectors.joining("\n")));
        targets.setEditable(false);
        targets.setWrapText(true);
        targets.setPrefRowCount(Math.min(8, snapshot.size() + 1));
        CheckBox deleteStorage = new CheckBox("同时彻底删除存储中的作品（无法撤销）");
        Alerts.danger().title("移除重复作品")
                .message("确定移除选中的 " + snapshot.size() + " 部作品吗？")
                .content(new VBox(10, hint, targets, deleteStorage))
                .ok("移除", handle -> {
                    if (removingDuplicates.get()) return;
                    DuplicateWorkRemovalTask task = new DuplicateWorkRemovalTask(snapshot, deleteStorage.isSelected(), workService, removedIds -> {
                        viewModel.removeDuplicateWorks(removedIds);
                        EventBus.publish(NotificationKey.MAIN_GRID_REFRESH);
                    });
                    task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, ignored -> removingDuplicates.set(false));
                    task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, ignored -> removingDuplicates.set(false));
                    task.addEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED, ignored -> removingDuplicates.set(false));
                    if (taskManager.submit(task)) removingDuplicates.set(true);
                }).cancel().show();
    }



    private List<AsmrWork> renameList;
    /**
     * 对选定媒体库下的全部作品执行批量重命名：进度弹窗内启动 {@link RenameService}，
     * 成功后回写数据库路径、数据库回写失败的目录会尝试回滚并汇总结果。
     *
     * @param event 触发按钮的 FXML 事件，未在方法体中使用
     */
    @FXML
    void rename(ActionEvent event) {
        MediaLibrary mediaLibrary = mediaLibraryComboBoxForRename.getSelectionModel().getSelectedItem();
        String formatSampleText = formatSampleTextField.getText();
        if (mediaLibrary == null || mediaLibrary.getPath() == null || mediaLibrary.getPath().isBlank()) {
            Alerts.warning().title("提醒").message("请先选择一个媒体库").cancel("确定").show();
            return;
        }
        if (formatSampleText == null || formatSampleText.isBlank()) {
            Alerts.warning().title("提醒").message("请先填写命名格式").cancel("确定").show();
            return;
        }

        if (mediaLibrary.getId() == null) {
            Alerts.warning().title("提醒").message("媒体库记录无效，请刷新后重试").cancel("确定").show();
            return;
        }
        renameList = workService.getAllByMediaLibrary(mediaLibrary.getId());
        if (renameList.isEmpty()) {
            Alerts.warning().title("提醒").message("当前媒体库没有可重命名的作品").cancel("确定").show();
            return;
        }

        {
            Label label=new Label("确定重命名媒体库中的 " + renameList.size() + " 部作品吗？");

            ProgressBar progressBar=new ProgressBar();
            progressBar.setProgress(0);
            progressBar.setVisible(false);
            VBox progressPane=new VBox(label,progressBar);
            progressPane.setAlignment(Pos.CENTER);

            progressBar.progressProperty().addListener((observableValue, number, t1) -> {
                if (t1.doubleValue() == 1 ){
                    progressPane.getChildren().remove(progressPane);
                }
            });
            RenameService renameService = new RenameService(formatSampleText, renameTitleCNModel.isSelected());
            renameService.setList(renameList);
            renameService.setOnSucceeded(ignored -> {
                RenameService.RenameBatchResult result = renameService.getValue();
                int databaseFailures = 0;
                int rollbackErrors = 0;
                int updatedCount = 0;
                BackgroundService backgroundService = DI.get(BackgroundService.class);
                for (RenameService.RenamedWork renamedWork : result.renamedWorks()) {
                    AsmrWork work = renamedWork.work();
                    work.setFilepath(renamedWork.renamedPath());
                    if (workService.updateWorkFilePath(work)) {
                        updatedCount++;
                        backgroundService.invalidate(work);
                        continue;
                    }
                    databaseFailures++;
                    boolean rolledBack = false;
                    try {
                        rolledBack = MediaSourceFactory.forWork(work)
                                .rename(renamedWork.renamedPath(), fileName(renamedWork.originalPath()));
                    } catch (RuntimeException exception) {
                        rollbackErrors++;
                    }
                    work.setFilepath(rolledBack ? renamedWork.originalPath() : renamedWork.renamedPath());
                }
                int failed = result.failures().size() + databaseFailures;
                String summary = String.format("已结束：成功 %d 个，跳过 %d 个，失败 %d 个",
                        result.renamedWorks().size() - databaseFailures, result.skippedCount(), failed);
                if (!result.failures().isEmpty()) {
                    summary += "。例如：" + result.failures().getFirst();
                }
                if (databaseFailures > 0) {
                    summary += "。有 " + databaseFailures + " 个数据库回写失败，已尝试回滚目录名称";
                }
                if (rollbackErrors > 0) {
                    summary += "。有 " + rollbackErrors + " 个回滚请求失败，请手动检查目录名称";
                }
                label.setText(summary);
                if (updatedCount > 0) {
                    EventBus.publish(NotificationKey.MAIN_GRID_REFRESH);
                }
            });
            renameService.setOnCancelled(ignored -> label.setText("已取消，未处理的作品保持原名称"));
            renameService.setOnFailed(ignored -> label.setText("批量重命名失败：" + renameService.getException().getMessage()));


            Button cancelBtn=new Button("取消");
            cancelBtn.getStyleClass().add("lc-label-btn");
            cancelBtn.setCancelButton(true);
            cancelBtn.setOnAction(actionEvent -> {
                if (renameService.isRunning()){
                    renameService.cancel();
                }
            });

            // 标准「标题 + 内容 + 按钮」弹窗，统一走 Dialogs 体系
            PageHeader header = new PageHeader();
            header.setIcon(Material2OutlinedMZ.WARNING);
            header.setTitle("提醒");
            Dialogs.show(DialogSpec.builder()
                    .heading(header)
                    .content(progressPane)
                    .styleClass("warning")
                    .action("重命名", "default-button", handle -> {
                        label.setText("重命名中，请不要操作，耐心等待重命名完成");
                        progressBar.progressProperty().bind(renameService.progressProperty());
                        renameService.start();
                        progressBar.setVisible(true);
                    })
                    .action("取消", "cancel-button", handle -> {
                        if (renameService.isRunning()) {
                            renameService.cancel();
                        }
                        handle.close();
                    })
                    .build());




        }
    }

    /**
     * 从存在可执行重命名计划的作品中随机取一个，把"原名 → 新名"的预览写入示例标签。
     *
     * @param event 触发按钮的 FXML 事件，未在方法体中使用
     */
    @FXML
    void previewRename(ActionEvent event) {
        MediaLibrary mediaLibrary = mediaLibraryComboBoxForRename.getSelectionModel().getSelectedItem();
        if (mediaLibrary == null || mediaLibrary.getPath() == null || mediaLibrary.getPath().isBlank()) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "请先选择一个媒体库"));
            return;
        }
        String format = formatSampleTextField.getText();
        if (format == null || format.isBlank()) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "请先填写命名格式"));
            return;
        }
        if (mediaLibrary.getId() == null) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "媒体库记录无效，请刷新后重试"));
            return;
        }
        List<AsmrWork> candidates = workService.getAllByMediaLibrary(mediaLibrary.getId()).stream()
                .filter(work -> RenameService.buildRenamePlan(work, format, renameTitleCNModel.isSelected()).executable())
                .toList();
        if (candidates.isEmpty()) {
            renameSampleLabel.setText("当前媒体库没有可生成预览的作品");
            return;
        }
        AsmrWork work = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        RenameService.RenamePlan plan = RenameService.buildRenamePlan(work, format, renameTitleCNModel.isSelected());
        renameSampleLabel.setText(fileName(work.getFilepath()) + "  →  " + plan.name());
    }

    private static String fileName(String path) {
        if (path == null) {
            return "未知作品";
        }
        int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return separator >= 0 ? path.substring(separator + 1) : path;
    }

    /**
     * 在系统资源管理器中打开当前选中的媒体库目录。
     *
     * @param event 触发按钮的 FXML 事件，未在方法体中使用
     */
    @FXML
    void openMediaLibrary(ActionEvent event){
        MediaLibrary item = mediaLibraryComboBoxForRename.getSelectionModel().getSelectedItem();
        if (item != null){
            if (item.getStorageType() != StorageType.LOCAL) {
                EventBus.publish(NotificationKey.MESSAGE,
                        new MessageInfo(MessageType.WARNING, "OpenList 媒体库不支持在系统资源管理器中打开"));
                return;
            }
            String mediaPath=mediaLibraryComboBoxForRename.getSelectionModel().getSelectedItem().getPath();
            if (mediaPath != null){
                try {
                    FileUtils.openInExplore(new File(mediaPath));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }else{
                EventBus.publish(NotificationKey.MESSAGE,new MessageInfo(MessageType.WARNING,"媒体库路径有误"));
            }
        }else {
            EventBus.publish(NotificationKey.MESSAGE,new MessageInfo(MessageType.WARNING,"请先选择一个媒体库"));
        }

    }

    /**
     * 清理数据库里本地/远程路径已失效的作品记录；只删记录，不删除实际文件。
     *
     * @param event 触发按钮的 FXML 事件，未在方法体中使用
     */
    @FXML
    void cleanupMissingWorks(ActionEvent event) {
        List<AsmrWork> works = workService.getAllWorks();
        confirmCleanup("清理失效作品", works.size(),
                "将检查所有作品的本地或远程路径，并删除无法找到的数据库记录。实际文件不会被删除。",
                () -> taskManager.submit(new MissingWorkCleanupTask(works, workService)));
    }

    /**
     * 清理未绑定任何媒体库的作品数据库记录；不删除实际文件，但标签、声优、社团、评分等信息会丢失。
     *
     * @param event 触发按钮的 FXML 事件，未在方法体中使用
     */
    @FXML
    void cleanupUnboundWorks(ActionEvent event) {
        List<AsmrWork> works = workService.getAllWorks().stream()
                .filter(work -> work.getMediaLibraryId() == null)
                .toList();
        confirmCleanup("清理未绑定作品", works.size(),
                "将删除没有媒体库绑定的作品数据库记录。实际文件不会被删除，但标签、声优、社团、评分等信息会丢失。",
                () -> taskManager.submit(new DataCleanupTask(DataCleanupTask.Operation.UNBOUND_WORKS,
                        works, List.of(), List.of(), workService, mediaLibraryService)));
    }

    /**
     * 清理应用缓存目录中没有任何作品引用的封面文件；不删除作品目录里的封面。
     *
     * @param event 触发按钮的 FXML 事件，未在方法体中使用
     */
    @FXML
    void cleanupUnusedCovers(ActionEvent event) {
        List<AsmrWork> works = workService.getAllWorks();
        List<File> files = findUnusedCoverFiles(works);
        confirmCleanup("清理多余封面", files.size(),
                "将删除应用缓存目录中没有任何作品引用的封面文件，不会删除作品目录中的封面。",
                () -> taskManager.submit(new DataCleanupTask(DataCleanupTask.Operation.UNUSED_COVERS,
                        List.of(), files, List.of(), workService, mediaLibraryService)));
    }

    /**
     * 清理没有任何作品使用的媒体库配置；不删除实际文件，也不删除未绑定作品。
     *
     * @param event 触发按钮的 FXML 事件，未在方法体中使用
     */
    @FXML
    void cleanupEmptyLibraries(ActionEvent event) {
        List<AsmrWork> works = workService.getAllWorks();
        List<MediaLibrary> libraries = findEmptyLibraries(works, mediaLibraryService.getAll());
        confirmCleanup("清理空媒体库", libraries.size(),
                "将删除没有作品使用的媒体库配置，不会删除实际文件，也不会删除未绑定作品。",
                () -> taskManager.submit(new DataCleanupTask(DataCleanupTask.Operation.EMPTY_LIBRARIES,
                        List.of(), List.of(), libraries, workService, mediaLibraryService)));
    }

    /**
     * 统一的数据清理确认：无可清理项时仅提示，否则弹危险确认框并在用户确认后执行 {@code action}。
     *
     * @param title   弹窗标题
     * @param count   预计处理数量
     * @param message 影响范围说明
     * @param action  用户确认后执行的清理动作
     */
    private void confirmCleanup(String title, int count, String message, Runnable action) {
        if (count == 0) {
            Alerts.info().title(title).message("当前没有可清理的数据。")
                    .cancel("确定").show();
            return;
        }
        Alerts.danger().title(title)
                .message(message + "\n预计处理数量：" + count + "。确定继续吗？")
                .ok("确定", handle -> action.run()).cancel().show();
    }

    /**
     * 找出缓存 {@code main}/{@code thumb} 目录中，文件名对应的作品编号已不在库中的封面文件。
     *
     * @param works 当前全部作品，用于收集仍在使用的作品编号
     * @return 可安全删除的封面文件列表
     */
    private static List<File> findUnusedCoverFiles(List<AsmrWork> works) {
        Set<String> usedCodes = new HashSet<>();
        for (AsmrWork work : works) {
            String code = WorkCodeUtils.normalize(work.getWorkId());
            if (code != null) {
                usedCodes.add(code.toUpperCase(Locale.ROOT));
            }
        }
        List<File> result = new ArrayList<>();
        for (String subDir : List.of("main", "thumb")) {
            File root = CoverUtils.getCacheDirectory(subDir);
            File[] files = root.listFiles(file -> file.isFile() && isCoverExtension(file.getName()));
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String name = file.getName();
                String stem = name.substring(0, name.lastIndexOf('.')).toUpperCase(Locale.ROOT);
                String code = WorkCodeUtils.normalize(stem);
                if (code != null && !usedCodes.contains(code)) {
                    result.add(file);
                }
            }
        }
        return result;
    }

    private static boolean isCoverExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    /**
     * 找出既没有被作品直接绑定、也没有作品路径落在其目录下的空媒体库。
     *
     * @param works     当前全部作品
     * @param libraries 待检查的媒体库列表
     * @return 未被任何作品使用的媒体库
     */
    private static List<MediaLibrary> findEmptyLibraries(List<AsmrWork> works, List<MediaLibrary> libraries) {
        List<MediaLibrary> result = new ArrayList<>();
        for (MediaLibrary library : libraries) {
            boolean used = works.stream().anyMatch(work -> library.getId() != null
                    && library.getId().equals(work.getMediaLibraryId()));
            if (!used && library.getPath() != null) {
                String root = normalizePath(library.getPath());
                used = works.stream().anyMatch(work -> work.getStorageType() != null
                        && library.getStorageType() != null
                        && work.getStorageType().equals(library.getStorageType().getCode())
                        && (isUnderPath(work.getFilepath(), root)
                        || isUnderPath(work.getParentPath(), root)));
            }
            if (!used) {
                result.add(library);
            }
        }
        return result;
    }

    private static boolean isUnderPath(String path, String root) {
        if (path == null) {
            return false;
        }
        String normalized = normalizePath(path);
        if ("/".equals(root)) {
            return normalized.startsWith("/");
        }
        return normalized.equals(root) || normalized.startsWith(root + "/");
    }

    private static String normalizePath(String path) {
        String normalized = path.replace('\\', '/').replaceAll("/+", "/")
                .toLowerCase(Locale.ROOT);
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}

