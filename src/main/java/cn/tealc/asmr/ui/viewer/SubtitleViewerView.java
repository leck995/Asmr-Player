package cn.tealc.asmr.ui.viewer;

import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.SubtitleCue;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.subtitle.SubtitleDisplayService;
import cn.tealc.asmr.subtitle.SubtitleScriptMode;
import cn.tealc.asmr.ui.component.EmptyState;
import cn.tealc.asmr.ui.dialog.legacy.NewStage;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.FileNameUtils;
import cn.tealc.asmr.subtitle.parser.SubtitleParser;
import cn.tealc.asmr.subtitle.parser.SubtitleParserFactory;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 字幕查看器窗口，用于在作品详情页中查看指定字幕文件的内容。
 * <p>
 * 继承 {@link NewStage}，以非模态独立窗口弹出，左右分栏布局：
 * <ul>
 *   <li>左侧：同目录字幕文件列表，点击切换</li>
 *   <li>右侧：选中文件的字幕条目列表（原文+翻译+时间标签分层显示）</li>
 * </ul>
 * 支持 LRC/VTT/SRT 三种格式。不依赖播放器，纯静态查看。
 * </p>
 *
 * @author Leck
 */
public class SubtitleViewerView extends NewStage {

    private static final Logger logger = LoggerFactory.getLogger(SubtitleViewerView.class);

    /** 字幕扩展名集合（小写） */
    private static final java.util.Set<String> SUBTITLE_EXTS =
            java.util.Set.of("lrc", "vtt", "srt");

    private final MediaSource source;
    private final String audioDir;

    /** 左侧：同目录字幕文件列表 */
    private final ObservableList<AsmrFile> subtitleFiles = FXCollections.observableArrayList();
    private final ListView<AsmrFile> fileListView = new ListView<>(subtitleFiles);
    private final Label fileCountLabel = new Label();
    private final VBox leftPane = new VBox();

    /** 右侧：字幕条目列表 */
    private final ObservableList<SubtitleCue> cues = FXCollections.observableArrayList();
    private final FilteredList<SubtitleCue> filteredCues = new FilteredList<>(cues);
    private final ListView<SubtitleCue> cueListView = new ListView<>(filteredCues);
    private final ProgressIndicator loadingIndicator = new ProgressIndicator();
    private final StackPane loadingOverlay = new StackPane();
    private final Label cueCountLabel = new Label();
    private final Label selectedFileLabel = new Label("选择左侧字幕文件以开始阅读");
    private final ComboBox<SubtitleScriptMode> scriptModeComboBox = new ComboBox<>();

    /**
     * 构造字幕查看器，加载指定字幕文件。
     *
     * @param subtitleFile 用户右键/双击选中的字幕文件
     * @param work         当前作品（用于获取 MediaSource）
     */
    public SubtitleViewerView(AsmrFile subtitleFile, AsmrWork work) {
        super();
        setTitle("字幕查看");
        this.source = MediaSourceFactory.forWork(work);
        this.audioDir = parentDir(subtitleFile.getFilepath());

        setWidth(1120);
        setHeight(680);
        getScene().getStylesheets().add(CssLoader.getCommonCss(CssLoader.subtitleViewer));
        getRoot().getStyleClass().add("subtitle-viewer-root");

        initFileList();
        initContent();
        loadFileList(subtitleFile);
    }

    // ==================== UI 初始化 ====================

    /**
     * 初始化左侧文件列表。
     */
    private void initFileList() {
        fileListView.getStyleClass().add("subtitle-viewer-file-list");
        fileListView.setCellFactory(lv -> new SubtitleFileCell());
        fileListView.setPlaceholder(createEmptyState("未找到字幕文件", "当前目录中没有可用的 LRC、VTT 或 SRT 文件。"));

        Label fileHeader = new Label("字幕文件");
        fileHeader.getStyleClass().add("subtitle-viewer-pane-title");
        Label fileDescription = new Label("同目录可用字幕");
        fileDescription.getStyleClass().add("subtitle-viewer-pane-description");
        fileCountLabel.textProperty().bind(Bindings.createStringBinding(
                () -> subtitleFiles.size() + " 个文件", subtitleFiles));
        fileCountLabel.getStyleClass().add("subtitle-viewer-count");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox headerTop = new HBox(10, fileHeader, headerSpacer, fileCountLabel);
        headerTop.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(3, headerTop, fileDescription);
        header.getStyleClass().add("subtitle-viewer-pane-header");

        leftPane.getChildren().setAll(header, fileListView);
        VBox.setVgrow(fileListView, Priority.ALWAYS);
        leftPane.getStyleClass().add("subtitle-viewer-left-pane");

        // 文件切换事件
        fileListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        loadSubtitle(newVal);
                    }
                });
    }

    /**
     * 初始化右侧内容区：字幕条目 ListView + 加载指示器叠加。
     */
    private void initContent() {
        cueListView.getStyleClass().add("subtitle-viewer-list");
        cueListView.setCellFactory(lv -> new SubtitleCueCell());
        cueListView.setPlaceholder(createEmptyState("请选择字幕文件", "从左侧选择一个字幕文件，即可在这里阅读原文、翻译与时间信息。"));

        TextField searchField = new TextField();
        searchField.setPromptText("筛选原文或翻译内容");
        searchField.getStyleClass().add("subtitle-viewer-search");
        searchField.textProperty().addListener((observable, oldValue, keyword) -> updateCueFilter(keyword));

        Label visibleCountLabel = new Label();
        visibleCountLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            int matched = filteredCues.size();
            int total = cues.size();
            return matched == total ? total + " 条" : matched + " / " + total + " 条";
        }, filteredCues, cues));
        visibleCountLabel.getStyleClass().add("subtitle-viewer-filter-count");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        scriptModeComboBox.setItems(FXCollections.observableArrayList(SubtitleScriptMode.values()));
        scriptModeComboBox.setValue(SubtitleDisplayService.getCurrentMode());
        scriptModeComboBox.getStyleClass().add("subtitle-viewer-script-mode");
        scriptModeComboBox.valueProperty().addListener((observable, oldValue, mode) -> {
            if (mode != null && mode != SubtitleDisplayService.getCurrentMode()) {
                SubtitleDisplayService.modeProperty().set(mode.name());
            }
        });
        ChangeListener<String> modeListener = (observable, oldValue, modeValue) -> {
            SubtitleScriptMode mode = SubtitleScriptMode.fromConfig(modeValue);
            if (scriptModeComboBox.getValue() != mode) {
                scriptModeComboBox.setValue(mode);
            }
            updateCueFilter(searchField.getText());
            cueListView.refresh();
        };
        SubtitleDisplayService.modeProperty().addListener(modeListener);
        setOnHidden(event -> SubtitleDisplayService.modeProperty().removeListener(modeListener));
        HBox filterBar = new HBox(10, searchField, scriptModeComboBox, visibleCountLabel);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.getStyleClass().add("subtitle-viewer-filter-bar");

        Label numberColumnLabel = new Label("序号");
        numberColumnLabel.getStyleClass().addAll("subtitle-viewer-column-title", "subtitle-viewer-column-number");
        Label timeColumnLabel = new Label("时间");
        timeColumnLabel.getStyleClass().addAll("subtitle-viewer-column-title", "subtitle-viewer-column-time");
        Label textColumnLabel = new Label("字幕内容");
        textColumnLabel.getStyleClass().addAll("subtitle-viewer-column-title", "subtitle-viewer-column-text");
        HBox columnHeader = new HBox(12, numberColumnLabel, timeColumnLabel, textColumnLabel);
        columnHeader.setAlignment(Pos.CENTER_LEFT);
        columnHeader.getStyleClass().add("subtitle-viewer-column-header");

        loadingIndicator.setPrefSize(34, 34);
        Label loadingText = new Label("正在解析字幕…");
        loadingText.getStyleClass().add("subtitle-viewer-loading-text");
        VBox loadingBox = new VBox(10, loadingIndicator, loadingText);
        loadingBox.setAlignment(Pos.CENTER);
        loadingOverlay.getChildren().add(loadingBox);
        loadingOverlay.getStyleClass().add("subtitle-viewer-loading-overlay");
        loadingOverlay.setVisible(false);
        loadingOverlay.setManaged(false);

        VBox cueContent = new VBox(filterBar, columnHeader, cueListView);
        VBox.setVgrow(cueListView, Priority.ALWAYS);
        StackPane rightStack = new StackPane(cueContent, loadingOverlay);
        HBox.setHgrow(rightStack, Priority.ALWAYS);

        Label contentHeader = new Label("字幕内容");
        contentHeader.getStyleClass().add("subtitle-viewer-pane-title");
        cueCountLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            if (cues.isEmpty()) {
                return "暂无条目";
            }
            int translatedCount = (int) cues.stream().filter(SubtitleCue::hasTranslation).count();
            return cues.size() + " 条 · " + formatDuration(cues.getLast().getStartTime())
                    + " · 译文 " + translatedCount;
        }, cues));
        cueCountLabel.getStyleClass().add("subtitle-viewer-count");
        selectedFileLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            AsmrFile selectedFile = fileListView.getSelectionModel().getSelectedItem();
            return selectedFile == null ? "选择左侧字幕文件以开始阅读" : selectedFile.getFilename();
        }, fileListView.getSelectionModel().selectedItemProperty()));
        selectedFileLabel.getStyleClass().add("subtitle-viewer-pane-description");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox headerTop = new HBox(10, contentHeader, headerSpacer, cueCountLabel);
        headerTop.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(3, headerTop, selectedFileLabel);
        header.getStyleClass().add("subtitle-viewer-pane-header");

        VBox rightPane = new VBox(header, rightStack);
        VBox.setVgrow(rightStack, Priority.ALWAYS);
        rightPane.getStyleClass().add("subtitle-viewer-right-pane");

        // SplitPane 左右分栏
        SplitPane splitPane = new SplitPane(leftPane, rightPane);
        splitPane.setDividerPositions(0.26);
        splitPane.getStyleClass().add("subtitle-viewer-split");
        setContent(splitPane);
    }

    // ==================== 数据加载 ====================

    /**
     * 异步加载同目录字幕文件列表，并选中当前文件。
     *
     * @param currentFile 用户选中的字幕文件
     */
    private void loadFileList(AsmrFile currentFile) {
        Thread.startVirtualThread(() -> {
            try {
                List<AsmrFile> allFiles = source.listFiles(audioDir);
                List<AsmrFile> files = allFiles.stream()
                        .filter(f -> !f.isDir())
                        .filter(f -> SUBTITLE_EXTS.contains(f.getType().toLowerCase()))
                        .collect(Collectors.toList());
                logger.info("同目录找到 {} 个字幕文件", files.size());
                Platform.runLater(() -> {
                    subtitleFiles.setAll(files);
                    fileListView.getSelectionModel().select(currentFile);
                });
            } catch (Exception e) {
                logger.error("加载同目录字幕文件列表失败", e);
            }
        });
    }

    /**
     * 异步读取并解析字幕文件，更新右侧 ListView。
     *
     * @param subtitleFile 字幕文件
     */
    private void loadSubtitle(AsmrFile subtitleFile) {
        setLoading(true);
        Thread.startVirtualThread(() -> {
            try {
                String ext = FileNameUtils.getSuffix(subtitleFile.getFilename());
                SubtitleParser parser = SubtitleParserFactory.forExtension(ext);
                if (parser == null) {
                    logger.warn("不支持的字幕格式: {}", ext);
                    Platform.runLater(() -> {
                        setLoading(false);
                        cues.clear();
                    });
                    return;
                }
                String text = source.readTextFile(subtitleFile.getFilepath());
                List<SubtitleCue> result = parser.parse(text);
                logger.info("字幕文件 {} 解析到 {} 条", subtitleFile.getFilename(), result != null ? result.size() : 0);
                Platform.runLater(() -> {
                    cues.setAll(result != null ? result : List.of());
                    if (!cues.isEmpty()) {
                        cueListView.scrollTo(0);
                    }
                    setLoading(false);
                });
            } catch (Exception e) {
                logger.error("读取字幕文件失败: {}", subtitleFile.getFilepath(), e);
                Platform.runLater(() -> setLoading(false));
            }
        });
    }

    /**
     * 切换字幕解析时的加载遮罩。
     *
     * @param loading 是否正在加载
     */
    private void setLoading(boolean loading) {
        loadingOverlay.setVisible(loading);
        loadingOverlay.setManaged(loading);
    }

    /**
     * 按原文和译文过滤字幕条目。
     *
     * @param keyword 用户输入的筛选词
     */
    private void updateCueFilter(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        filteredCues.setPredicate(cue -> normalizedKeyword.isEmpty()
                || containsKeyword(cue.getText(), normalizedKeyword)
                || containsKeyword(cue.getTranslation(), normalizedKeyword)
                || containsKeyword(SubtitleDisplayService.convert(cue.getText()), normalizedKeyword)
                || containsKeyword(SubtitleDisplayService.convert(cue.getTranslation()), normalizedKeyword));
    }

    /**
     * 判断字幕文本是否包含筛选词。
     *
     * @param text    待检查文本
     * @param keyword 已规范化的筛选词
     * @return 包含筛选词时返回 true
     */
    private static boolean containsKeyword(String text, String keyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(keyword);
    }

    /**
     * 将字幕开始时间格式化为紧凑时长。
     *
     * @param milliseconds 毫秒时间
     * @return 时长文本
     */
    private static String formatDuration(long milliseconds) {
        long totalSeconds = Math.max(0, milliseconds / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    /**
     * 创建列表为空时的提示内容。
     *
     * @param title       提示标题
     * @param description 提示说明
     * @return 空状态节点
     */
    private static EmptyState createEmptyState(String title, String description) {
        EmptyState emptyState = EmptyState.of(Material2OutlinedMZ.SUBTITLES, title, description);
        emptyState.setCompact(true);
        return emptyState;
    }

    // ==================== Cell 实现 ====================

    /**
     * 右侧字幕条目单元格：左侧原文+翻译，右侧时间标签。
     */
    private static class SubtitleCueCell extends ListCell<SubtitleCue> {

        private final Label indexLabel = new Label();
        private final Label textLabel = new Label();
        private final Label translationLabel = new Label();
        private final Label timeLabel = new Label();
        private final HBox cellBox;

        private SubtitleCueCell() {
            indexLabel.getStyleClass().add("subtitle-viewer-cell-index");
            textLabel.setWrapText(true);
            textLabel.setMaxWidth(Double.MAX_VALUE);
            textLabel.getStyleClass().add("subtitle-viewer-cell-text");

            translationLabel.setWrapText(true);
            translationLabel.setMaxWidth(Double.MAX_VALUE);
            translationLabel.getStyleClass().add("subtitle-viewer-cell-translation");

            timeLabel.getStyleClass().add("subtitle-viewer-cell-time");

            VBox textBox = new VBox(5, textLabel, translationLabel);
            textBox.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            cellBox = new HBox(12, indexLabel, timeLabel, textBox);
            cellBox.setAlignment(Pos.CENTER_LEFT);
            cellBox.setMaxWidth(Double.MAX_VALUE);
            cellBox.getStyleClass().add("subtitle-viewer-cell");
            cellBox.prefWidthProperty().bind(widthProperty().subtract(42));
            setGraphic(cellBox);
            setText(null);
        }

        @Override
        protected void updateItem(SubtitleCue cue, boolean empty) {
            super.updateItem(cue, empty);
            if (empty || cue == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            textLabel.setText(SubtitleDisplayService.convert(cue.getText()));
            boolean hasTranslation = cue.hasTranslation();
            translationLabel.setText(hasTranslation ? SubtitleDisplayService.convert(cue.getTranslation()) : "");
            translationLabel.setVisible(hasTranslation);
            translationLabel.setManaged(hasTranslation);
            timeLabel.setText(cue.getTimeTag());
            indexLabel.setText(String.format(Locale.ROOT, "%03d", getIndex() + 1));
            setGraphic(cellBox);
        }
    }

    /**
     * 左侧文件列表单元格：显示字幕文件名（不含路径）。
     */
    private static class SubtitleFileCell extends ListCell<AsmrFile> {

        private final Label fileNameLabel = new Label();
        private final Label fileTypeLabel = new Label();
        private final VBox fileBox;

        private SubtitleFileCell() {
            fileNameLabel.setWrapText(true);
            fileNameLabel.setMaxWidth(Double.MAX_VALUE);
            fileNameLabel.getStyleClass().add("subtitle-viewer-file-name");
            fileTypeLabel.getStyleClass().add("subtitle-viewer-file-type");
            Region spacer = new Region();
            HBox.setHgrow(fileNameLabel, Priority.ALWAYS);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox fileRow = new HBox(8, fileNameLabel, spacer, fileTypeLabel);
            fileRow.setAlignment(Pos.CENTER_LEFT);
            fileBox = new VBox(fileRow);
            fileBox.setMaxWidth(Double.MAX_VALUE);
            fileBox.prefWidthProperty().bind(widthProperty().subtract(28));
            setGraphic(fileBox);
            setText(null);
        }

        @Override
        protected void updateItem(AsmrFile file, boolean empty) {
            super.updateItem(file, empty);
            if (empty || file == null) {
                setGraphic(null);
                setText(null);
            } else {
                String suffix = FileNameUtils.getSuffix(file.getFilename());
                fileNameLabel.setText(removeSuffix(file.getFilename(), suffix));
                fileTypeLabel.setText(suffix == null || suffix.isBlank()
                        ? "字幕"
                        : suffix.toUpperCase(Locale.ROOT));
                setGraphic(fileBox);
                setText(null);
            }
        }

        /**
         * 移除用于格式徽标的文件扩展名。
         *
         * @param filename 文件名
         * @param suffix   文件扩展名
         * @return 不含扩展名的显示名称
         */
        private static String removeSuffix(String filename, String suffix) {
            if (suffix == null || suffix.isBlank()) {
                return filename;
            }
            int suffixLength = suffix.length() + 1;
            return filename.length() > suffixLength
                    ? filename.substring(0, filename.length() - suffixLength)
                    : filename;
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 获取文件的父目录路径，兼容 / 和 \。
     */
    private static String parentDir(String filePath) {
        int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return slash == -1 ? filePath : filePath.substring(0, slash);
    }
}
