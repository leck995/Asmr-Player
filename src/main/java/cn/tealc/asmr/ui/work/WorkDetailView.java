package cn.tealc.asmr.ui.work;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.teafx.utils.AnchorPaneUtil;
import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.factory.ViewFactory;
import cn.tealc.asmr.model.SupportedAudioFormat;
import cn.tealc.asmr.model.SupportedImageFormat;
import cn.tealc.asmr.model.SupportedVideoFormat;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.AudioTrack;
import cn.tealc.asmr.model.collection.ItemDataEx;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.model.translation.TranslationPreviewItem;
import cn.tealc.asmr.player.VlcPlayer;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.ExternalPlayerService;
import cn.tealc.asmr.service.FileOperationService;
import cn.tealc.asmr.service.PlaylistService;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.source.StorageType;
import cn.tealc.asmr.ui.component.EmptyState;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.ui.TeaSkin;
import cn.tealc.asmr.ui.collection.AddToSheetView;
import cn.tealc.asmr.ui.collection.AddToSheetViewModel;
import cn.tealc.asmr.ui.work.PathPane;
import cn.tealc.asmr.ui.tools.SubtitleRenameView;
import cn.tealc.asmr.ui.tools.SubtitleRenameViewModel;
import cn.tealc.asmr.ui.album.WorkAlbumDialogs;
import cn.tealc.asmr.ui.tools.AiTitleTranslationAction;
import cn.tealc.asmr.ui.tools.AiFileNameTranslationAction;
import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.CoverViewport;
import cn.tealc.asmr.utils.URLUtils;
import cn.tealc.asmr.utils.VideoFileResolver;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.utils.comparator.AsmrFileComparator;
import cn.tealc.asmr.mvvm.FluentViewLoader;
import cn.tealc.asmr.mvvm.InjectViewModel;
import cn.tealc.asmr.mvvm.JavaView;
import cn.tealc.asmr.mvvm.ViewTuple;
import com.jfoenix.controls.JFXDialog;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.css.PseudoClass;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.MenuItem;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.controlsfx.control.Rating;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;
import org.kordamp.ikonli.materialdesign2.MaterialDesignB;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * 作品详情界面。
 * <p>
 * 整合了原 {@code FolderTableView} 的文件浏览功能（目录树、文件列表、右键菜单、过滤等），
 * 与作品级信息（封面、标题、收藏、分类/声优/标签）统一在一个 View 中展示。
 *
 * @author Leck
 */
public class WorkDetailView extends BorderPane implements JavaView<WorkDetailViewModel>, Initializable, TeaSkin {

    private static final int ALL = 0;
    private static final int PICTURE = 1;
    private static final int VIDEO = 2;
    private static final int AUDIO = 3;

    /** 已评分状态，供 CSS 区分实心/空心星，与 BigWorkCell 同名 */
    private static final PseudoClass RATED = PseudoClass.getPseudoClass("rated");

    /** 左侧栏内容宽度：与左栏 350 的宽度一致，供 FlowPane 计算折行 */
    private static final double LEFT_CONTENT_WIDTH = 350;

    @InjectViewModel
    private WorkDetailViewModel viewModel;

    // 文件浏览相关
    private TableView<AsmrFile> tableView;
    private FilteredList<AsmrFile> filteredList;
    private ObservableList<AsmrFile> items;
    private StringProperty rootPath;
    private StringProperty currentPath;
    private final BooleanProperty filesLoading = new SimpleBooleanProperty(false);
    /** 本次详情页内的目录列表缓存：目录往返不再重复请求，写操作后按路径失效 */
    private final Map<String, List<AsmrFile>> listingCache = new HashMap<>();
    /** 写操作后需要强制回源刷新一次的目录，避免首次列举命中服务端的旧缓存 */
    private final Set<String> dirtyDirectories = new HashSet<>();
    /** 列表版本：写操作递增，用于丢弃写操作前发出、写操作后才返回的过期响应 */
    private int listingVersion;
    private PathPane pathPane;
    private Button translateFileNamesBtn;
    private boolean fileNameTranslationReady;
    private MediaSource source;
    private Image audioImage, picImage, otherImage, videoImage, folderImage;
    private final PlaylistService playlistService = DI.get(PlaylistService.class);
    private final FileOperationService fileOperationService = DI.get(FileOperationService.class);
    private final ExternalPlayerService externalPlayerService = DI.get(ExternalPlayerService.class);

    @Override
    public void changeSkin() {
        getStylesheets().add(CssLoader.getCss(CssLoader.workDetail));
        EventBus.subscribe("CHANGE_SKIN", (s, objects) -> {
            getStylesheets().clear();
            getStylesheets().add(CssLoader.getCss(CssLoader.workDetail));
        });
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        changeSkin();
        showLocalDetail();
    }

    public void showLocalDetail() {
        if (viewModel.getWork() == null) {
            Alerts.warning().message("该音声不存在，可能被删除或移动，请更新").cancel("确定").show();
            return;
        }

        /* ===================== 左侧面板 ===================== */
        ImageView imageView = new ImageView();
        imageView.imageProperty().bind(viewModel.coverProperty());
        imageView.setFitHeight(255);
        imageView.setFitWidth(340);
        CoverViewport.configure(imageView);
        Rectangle rectangle = new Rectangle(340, 255);
        rectangle.setArcHeight(15);
        rectangle.setArcWidth(15);
        imageView.setClip(rectangle);

        // ---- 作品编号与日期：压在封面底边，编号靠左、日期靠右，与卡片浮层一致 ----
        Label workIdLabel = new Label();
        workIdLabel.textProperty().bind(viewModel.workIdProperty());
        workIdLabel.getStyleClass().addAll("detail-meta-chip", "detail-work-id");
        workIdLabel.setGraphicTextGap(5);
        // 来源图标：云端作品与本地作品使用不同图标，与卡片一致
        FontIcon sourceIcon = new FontIcon();
        sourceIcon.getStyleClass().add(viewModel.getWork() != null && Integer.valueOf(1).equals(viewModel.getWork().getStorageType()) ? "web" : "local");
        workIdLabel.setGraphic(sourceIcon);
        workIdLabel.setTooltip(new Tooltip("点击复制作品编号"));
        workIdLabel.setOnMouseClicked(mouseEvent -> {
            viewModel.copyWorkId();
            mouseEvent.consume();
        });

        Label releaseLabel = new Label();
        releaseLabel.textProperty().bind(viewModel.releaseProperty());
        releaseLabel.getStyleClass().add("detail-meta-chip");

        // 编号/日期各自独立显隐：数据为空时不占位
        workIdLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> !isBlank(viewModel.getWorkId()), viewModel.workIdProperty()));
        workIdLabel.managedProperty().bind(workIdLabel.visibleProperty());
        releaseLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> !isBlank(viewModel.getRelease()), viewModel.releaseProperty()));
        releaseLabel.managedProperty().bind(releaseLabel.visibleProperty());

        // ---- 字幕 / 全年龄标记：压在封面右上角 ----
        // 按数据绑定显示，作品信息刷新后无需重建节点即可同步
        Label subtitleTag = new Label("字幕");
        subtitleTag.getStyleClass().addAll("detail-tag-label", "detail-subtitle");
        subtitleTag.visibleProperty().bind(viewModel.subtitleProperty());
        subtitleTag.managedProperty().bind(subtitleTag.visibleProperty());
        Label allAgesTag = new Label("全年龄");
        allAgesTag.getStyleClass().addAll("detail-tag-label", "detail-all-ages");
        allAgesTag.visibleProperty().bind(viewModel.allAgesProperty());
        allAgesTag.managedProperty().bind(allAgesTag.visibleProperty());
        HBox tagPane = new HBox(6, subtitleTag, allAgesTag);
        tagPane.getStyleClass().add("detail-tag-pane");
        tagPane.setAlignment(Pos.CENTER_RIGHT);
        // StackPane 默认会把可拉伸节点撑满整个封面，会让 TOP_RIGHT 失效、标记跑到垂直居中；
        // 限定为「刚好包住内容」，右上角定位才会生效
        tagPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        // 两个标记都不显示时整条标记栏不占位，封面不留空浮层
        BooleanBinding hasTags = viewModel.subtitleProperty().or(viewModel.allAgesProperty());
        tagPane.visibleProperty().bind(hasTags);
        tagPane.managedProperty().bind(hasTags);

        // 封面叠层：图片在下；标记在右上，编号在左下，日期在右下
        StackPane coverPane = new StackPane(imageView, workIdLabel, releaseLabel, tagPane);
        coverPane.getStyleClass().add("detail-cover-pane");
        coverPane.setPrefSize(340, 255);
        coverPane.setMinSize(340, 255);
        coverPane.setMaxSize(340, 255);
        StackPane.setAlignment(workIdLabel, Pos.BOTTOM_LEFT);
        StackPane.setAlignment(releaseLabel, Pos.BOTTOM_RIGHT);
        StackPane.setAlignment(tagPane, Pos.TOP_RIGHT);
        StackPane.setMargin(workIdLabel, new Insets(0, 0, 8, 8));
        StackPane.setMargin(releaseLabel, new Insets(0, 8, 8, 0));
        StackPane.setMargin(tagPane, new Insets(8, 8, 0, 0));

        FontIcon playAllIcon = new FontIcon();
        Label playAllText = new Label("播放全部");
        HBox playAllContent = new HBox(8, playAllIcon, playAllText);
        playAllContent.setAlignment(Pos.CENTER);
        Button playAllBtn = new Button();
        playAllBtn.setGraphic(playAllContent);
        playAllBtn.setAlignment(Pos.CENTER);
        playAllBtn.getStyleClass().addAll("lc-svg-label-round-btn", "play-all-btn");
        Button openFolderBtn = new Button();
        openFolderBtn.setTooltip(new Tooltip("打开文件夹"));
        openFolderBtn.setGraphic(new FontIcon());
        openFolderBtn.getStyleClass().addAll("open-folder-btn");
        openFolderBtn.setOnAction(actionEvent -> viewModel.openWorkDir());

        ToggleButton starBtn = new ToggleButton();
        starBtn.selectedProperty().bindBidirectional(viewModel.starProperty());
        starBtn.setTooltip(new Tooltip("收藏"));
        starBtn.setGraphic(new FontIcon());
        starBtn.getStyleClass().addAll("star-btn");
        starBtn.setOnAction(actionEvent -> viewModel.star(starBtn.isSelected()));

        // 加入作品集：改用线性书签图标，与收藏按钮同属描边风格，视觉重量一致
        Button albumBtn = new Button(null, new FontIcon(MaterialDesignB.BOOKMARK_PLUS_OUTLINE));
        albumBtn.setTooltip(new Tooltip("加入作品集"));
        albumBtn.setAccessibleText("加入作品集");
        // 与打开文件夹、收藏共用同一套圆形图标按钮样式
        albumBtn.getStyleClass().addAll("album-btn");
        albumBtn.setOnAction(event -> WorkAlbumDialogs.chooseAlbums(viewModel.getWork()));

        // ---- 评分：紧靠在「播放全部」左侧 ----
        FontIcon ratingIcon = new FontIcon(Material2OutlinedMZ.STAR_BORDER);
        Button ratingButton = new Button();
        ratingButton.setGraphic(ratingIcon);
        ratingButton.getStyleClass().add("detail-rating");
        ratingButton.setTooltip(new Tooltip("设置作品评分"));
        ratingButton.setMaxWidth(Region.USE_PREF_SIZE);
        ratingButton.setOnAction(event -> showRatingDialog());
        refreshRating(ratingButton, ratingIcon, viewModel.getRate());
        viewModel.rateProperty().addListener((observable, oldValue, newValue) ->
                refreshRating(ratingButton, ratingIcon, newValue));

        HBox playPane = new HBox(ratingButton, playAllBtn, openFolderBtn, starBtn, albumBtn);
        playPane.setSpacing(5);
        playPane.setAlignment(Pos.CENTER);

        FlowPane categoryPane = new FlowPane();
        categoryPane.setHgap(8.0);
        categoryPane.setVgap(5.0);
        FlowPane actorsPane = new FlowPane();
        actorsPane.setHgap(8.0);
        actorsPane.setVgap(5.0);
        rebuildMetadataLabels(categoryPane, actorsPane);
        // 标签/分类/声优被编辑或同步后由 ViewModel 更新列表，这里重建胶囊保持同步
        viewModel.getTags().addListener((ListChangeListener<String>) change -> rebuildMetadataLabels(categoryPane, actorsPane));
        viewModel.getCategories().addListener((ListChangeListener<String>) change -> rebuildMetadataLabels(categoryPane, actorsPane));
        viewModel.getVas().addListener((ListChangeListener<String>) change -> rebuildMetadataLabels(categoryPane, actorsPane));

        Label circleLabel = new Label();
        circleLabel.textProperty().bind(viewModel.circleProperty());
        circleLabel.visibleProperty().bind(viewModel.circleVisibleProperty());
        circleLabel.getStyleClass().add("circle-label");
        circleLabel.setGraphic(new FontIcon());
        circleLabel.setOnMouseClicked(mouseEvent -> {
            navigateToWorkList(viewModel.getCircle(), AsmrKeyType.CIRCLE);
            mouseEvent.consume();
        });
        circleLabel.setPadding(new Insets(0, 0, 0, 10));

        // 社团区块：标记已移到封面浮层，这里只承载社团本身
        VBox circleSection = createMetadataSection("社团", circleLabel, "detail-section-circle");
        circleSection.visibleProperty().bind(viewModel.circleVisibleProperty());
        circleSection.managedProperty().bind(circleSection.visibleProperty());
        VBox metadataSection = createMetadataSection("标签与分类", categoryPane, "detail-section-tags");
        VBox actorsSection = createMetadataSection("声优", actorsPane, "detail-section-vas");
        // 分组无内容时整块隐藏，避免左侧出现空档
        bindSectionVisible(metadataSection, categoryPane);
        bindSectionVisible(actorsSection, actorsPane);

        Text titleText = new Text();
        titleText.setText(viewModel.getTitle());
        viewModel.titleProperty().addListener((observable, oldValue, newValue) -> titleText.setText(newValue));
        titleText.getStyleClass().add("detail-dialog-title-text");
        Button translateTitleButton = new Button();
        translateTitleButton.setGraphic(AiTitleTranslationAction.createIdleIcon());
        translateTitleButton.setTooltip(new Tooltip("AI 翻译作品名称"));
        translateTitleButton.getStyleClass().add("detail-ai-title-translate-button");
        final TranslationPreviewItem[] pendingTitleTranslation = new TranslationPreviewItem[1];
        translateTitleButton.setOnAction(event -> {
            if (pendingTitleTranslation[0] != null) {
                if (AiTitleTranslationAction.apply(pendingTitleTranslation[0], viewModel.getWork())) {
                    pendingTitleTranslation[0] = null;
                    viewModel.update();
                    translateTitleButton.setGraphic(AiTitleTranslationAction.createIdleIcon());
                    translateTitleButton.setTooltip(new Tooltip("AI 翻译作品名称"));
                }
                return;
            }
            AiTitleTranslationAction.preview(viewModel.getWork(), translateTitleButton, item -> {
                pendingTitleTranslation[0] = item;
                titleText.setText(item.translatedText());
                translateTitleButton.setGraphic(AiTitleTranslationAction.createReadyIcon());
                translateTitleButton.setTooltip(new Tooltip("应用中文标题"));
            });
        });
        TextFlow titleFlow = new TextFlow(titleText, translateTitleButton);
        titleFlow.getStyleClass().add("detail-dialog-title");
        titleFlow.setPrefWidth(1020);

        VBox left = new VBox(coverPane, playPane,
                circleSection, metadataSection, actorsSection);
        left.setAlignment(Pos.TOP_LEFT);
        left.setSpacing(15);
        left.setPrefWidth(LEFT_CONTENT_WIDTH);
        ScrollPane leftScrollPane = new ScrollPane(left);
        leftScrollPane.setPrefWidth(LEFT_CONTENT_WIDTH + 10);

        /* ===================== 右侧文件浏览区 ===================== */
        initFileBrowser(viewModel.getWork());

        playAllBtn.setOnAction(action -> viewModel.playAll(items));

        setTop(titleFlow);
        setLeft(leftScrollPane);
        setCenter(buildFileBrowserPane());
        getStyleClass().add("detail-dialog");
    }

    /* ===================== 文件浏览（原 FolderTableView） ===================== */

    private BorderPane fileBrowserPane;

    /**
     * 初始化文件浏览器。
     */
    private void initFileBrowser(AsmrWork work) {
        this.source = MediaSourceFactory.forWork(work);
        rootPath = new SimpleStringProperty();
        currentPath = new SimpleStringProperty();

        initTableView(work.getFilepath());
        initPathView();

        HBox filterPane = initFilterPane();
        AnchorPane tableArea = new AnchorPane(tableView, filterPane);
        AnchorPaneUtil.setPosition(tableView, 0, 0, 0, 0);
        AnchorPaneUtil.setPosition(filterPane, 0.0, 0.0, null, null);

        // 加载动画叠加在表格区上，空目录显示表格占位提示
        ProgressIndicator loadingIndicator = new ProgressIndicator();
        loadingIndicator.getStyleClass().add("folder-table-loading");
        StackPane tableStack = new StackPane(tableArea, loadingIndicator);
        StackPane.setAlignment(loadingIndicator, Pos.CENTER);
        loadingIndicator.visibleProperty().bind(filesLoading);

        Label fileListTitle = new Label("文件列表");
        fileListTitle.getStyleClass().add("detail-file-list-title");
        Label fileCountLabel = new Label();
        fileCountLabel.textProperty().bind(Bindings.createStringBinding(
                () -> items.size() + " 个项目", items));
        fileCountLabel.getStyleClass().add("detail-file-count");
        Button refreshBtn = new Button("", new FontIcon(Material2OutlinedMZ.REFRESH));
        refreshBtn.setTooltip(new Tooltip("刷新当前目录"));
        refreshBtn.getStyleClass().add("detail-file-refresh");
        refreshBtn.setOnAction(event -> update(currentPath.get(), true));
        HBox.setMargin(refreshBtn, new Insets(0, 0, 0, 6));
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox fileHeader = new HBox(fileListTitle, headerSpacer, fileCountLabel, refreshBtn);
        fileHeader.setAlignment(Pos.CENTER_LEFT);
        fileHeader.getStyleClass().add("detail-file-header");

        // 面包屑始终可见，表格区在加载时叠加转圈
        fileBrowserPane = new BorderPane();
        fileBrowserPane.setTop(new VBox(fileHeader, pathPane));
        fileBrowserPane.setCenter(tableStack);
    }

    /**
     * 构建文件浏览器面板。
     */
    private BorderPane buildFileBrowserPane() {
        return fileBrowserPane;
    }

    /**
     * 构建一个「分组标题 + 内容」的元数据区块。
     *
     * @param title       分组标题
     * @param content     分组内容
     * @param titleSuffix 追加到标题上的样式类，用于沿用各分组的专属配色；可为 null
     */
    private VBox createMetadataSection(String title, Node content, String titleSuffix) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("detail-section-title");
        if (titleSuffix != null) {
            titleLabel.getStyleClass().add(titleSuffix);
        }
        VBox section = new VBox(8, titleLabel, content);
        section.getStyleClass().add("detail-metadata-section");
        return section;
    }

    /**
     * 按 ViewModel 中的标签、分类、声优重建胶囊。
     * <p>
     * 自定义标签与系统分类同处一个 FlowPane，自定义标签在前，与卡片顺序一致。
     *
     * @param categoryPane 标签与分类容器
     * @param actorsPane   声优容器
     */
    private void rebuildMetadataLabels(FlowPane categoryPane, FlowPane actorsPane) {
        categoryPane.getChildren().clear();
        for (String tag : viewModel.getTags()) {
            categoryPane.getChildren().add(createMetadataLabel(tag, "custom-tag", AsmrKeyType.TAG));
        }
        for (String category : viewModel.getCategories()) {
            categoryPane.getChildren().add(createMetadataLabel(category, "category-label", AsmrKeyType.CATEGORY));
        }
        actorsPane.getChildren().clear();
        for (String va : viewModel.getVas()) {
            actorsPane.getChildren().add(createMetadataLabel(va, "actor-label", AsmrKeyType.VA));
        }
    }

    /**
     * 创建一个可点击跳转的元数据胶囊。
     *
     * @param text       文本
     * @param styleClass 样式类
     * @param type       点击后跳转的检索类型
     */
    private Label createMetadataLabel(String text, String styleClass, AsmrKeyType type) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setOnMouseClicked(mouseEvent -> {
            navigateToWorkList(text, type);
            mouseEvent.consume();
        });
        return label;
    }

    /**
     * 分组内容为空时隐藏整个区块，隐藏后不占据布局空间。
     */
    private void bindSectionVisible(VBox section, Pane content) {
        BooleanBinding hasContent = Bindings.createBooleanBinding(
                () -> !content.getChildren().isEmpty(), content.getChildren());
        section.visibleProperty().bind(hasContent);
        section.managedProperty().bind(hasContent);
    }

    /**
     * 提交评分前弹出星级选择框，保存后由 ViewModel 回写并刷新按钮。
     */
    private void showRatingDialog() {
        Double current = viewModel.getRate();
        Rating ratingControl = new Rating(5);
        ratingControl.setPartialRating(true);
        ratingControl.setRating(current == null ? 0 : current);
        ratingControl.getStyleClass().add("rating-dialog-rating");
        Label ratingValueLabel = new Label(formatRating(ratingControl.getRating()));
        ratingValueLabel.getStyleClass().add("rating-dialog-value");
        ratingControl.ratingProperty().addListener((observable, oldValue, newValue) -> {
            double normalized = roundToHalfStar(newValue.doubleValue());
            if (Double.compare(normalized, newValue.doubleValue()) != 0) {
                ratingControl.setRating(normalized);
                return;
            }
            ratingValueLabel.setText(formatRating(normalized));
        });

        Label hint = new Label("点击星级设置 0 至 5 分，支持半星。");
        hint.getStyleClass().add("rating-dialog-hint");
        HBox ratingInput = new HBox(12, ratingControl, ratingValueLabel);
        ratingInput.setAlignment(Pos.CENTER_LEFT);
        ratingInput.getStyleClass().add("rating-dialog-input");
        VBox body = new VBox(10, hint, ratingInput);
        body.getStyleClass().add("rating-dialog-body");
        Alerts.basic()
                .title("作品评分")
                .content(body)
                // 评分控件的配色定义在 main-grid.css 中，弹窗需要显式引入
                .stylesheet(CssLoader.getCss(CssLoader.workGrid))
                .ok("保存评分", handle -> viewModel.updateRate(ratingControl.getRating()))
                .cancel()
                .show();
    }

    /**
     * 刷新评分按钮：未评分显示空心星与「评分」，已评分显示实心星与数值。
     */
    private void refreshRating(Button ratingButton, FontIcon ratingIcon, Double rate) {
        boolean rated = rate != null && rate > 0;
        ratingIcon.setIconCode(rated ? Material2OutlinedMZ.STAR : Material2OutlinedMZ.STAR_BORDER);
        ratingButton.setText(rated ? String.format(Locale.ROOT, "%.1f", rate) : "评分");
        ratingButton.pseudoClassStateChanged(RATED, rated);
    }

    /**
     * 将评分限制为半星步长。
     */
    private double roundToHalfStar(double rating) {
        return Math.round(rating * 2) / 2.0;
    }

    /**
     * 格式化评分文本。
     */
    private String formatRating(double rating) {
        return String.format(Locale.ROOT, "%.1f 分", rating);
    }

    /**
     * 判断字符串是否为空白，用于绑定元数据行的可见性。
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void navigateToWorkList(String key, AsmrKeyType type) {
        closeDetailDialog();
        viewModel.gotoWorkList(key, type);
    }

    private void closeDetailDialog() {
        Node current = this;
        while (current != null) {
            if (current instanceof JFXDialog dialog) {
                dialog.close();
                return;
            }
            current = current.getParent();
        }
    }

    private void initPathView() {
        pathPane = new PathPane();
        pathPane.getStyleClass().add("path-view-pane");
        pathPane.setMaxWidth(800.0);
        pathPane.setPrefWidth(40.0);
        pathPane.setPath(URLUtils.getFolderRelativePath(URLUtils.getParent(rootPath.get(), null), rootPath.get()));
        currentPath.addListener(((observable, oldValue, newValue) -> {
            if (newValue != null) {
                pathPane.setPath(URLUtils.getFolderRelativePath(URLUtils.getParent(rootPath.get(), null), newValue));
            }
        }));
        pathPane.onActionProperty().addListener(((observableValue, s, t1) -> {
            if (t1 != null) {
                String parent = URLUtils.getParent(rootPath.get(), null);
                String sep = parent.contains("/") ? "/" : File.separator;
                String target = parent + sep + t1;
                // 点击当前所在目录视为手动刷新，强制绕过两层目录缓存
                update(target, target.equals(currentPath.get()));
            }
        }));
    }

    private HBox initFilterPane() {
        ToggleGroup filterBtnToggleGroup = new ToggleGroup();

        ToggleButton allBtn = new ToggleButton("", new FontIcon(Material2OutlinedAL.FORMAT_LIST_BULLETED));
        allBtn.setToggleGroup(filterBtnToggleGroup);
        allBtn.getStyleClass().addAll("table-filter-btn");
        allBtn.setSelected(true);

        ToggleButton audioBtn = new ToggleButton("", new FontIcon(Material2OutlinedAL.AUDIOTRACK));
        audioBtn.setToggleGroup(filterBtnToggleGroup);
        audioBtn.getStyleClass().addAll("table-filter-btn");

        ToggleButton videoBtn = new ToggleButton("", new FontIcon(Material2OutlinedMZ.ONDEMAND_VIDEO));
        videoBtn.setToggleGroup(filterBtnToggleGroup);
        videoBtn.getStyleClass().addAll("table-filter-btn");

        ToggleButton picBtn = new ToggleButton("", new FontIcon(Material2OutlinedAL.IMAGE));
        picBtn.setToggleGroup(filterBtnToggleGroup);
        picBtn.getStyleClass().addAll("table-filter-btn");

        allBtn.setOnAction(actionEvent -> filter(ALL));
        audioBtn.setOnAction(actionEvent -> filter(AUDIO));
        videoBtn.setOnAction(actionEvent -> filter(VIDEO));
        picBtn.setOnAction(actionEvent -> filter(PICTURE));

        translateFileNamesBtn = new Button("", AiFileNameTranslationAction.createIdleIcon());
        translateFileNamesBtn.setTooltip(new Tooltip("AI 翻译文件和目录名称（仅预览，不重命名）"));
        translateFileNamesBtn.getStyleClass().add("table-file-translate-btn");
        translateFileNamesBtn.setOnAction(actionEvent -> {
            if (fileNameTranslationReady) {
                confirmFileNameRenames();
                return;
            }
            AiFileNameTranslationAction.translate(items, translateFileNamesBtn, () -> {
                tableView.refresh();
                fileNameTranslationReady = true;
                translateFileNamesBtn.setGraphic(AiFileNameTranslationAction.createReadyIcon());
                translateFileNamesBtn.setTooltip(new Tooltip("应用当前层级的文件和目录重命名"));
            });
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox filterPane = new HBox(translateFileNamesBtn, spacer, allBtn, audioBtn, videoBtn, picBtn);
        filterPane.setSpacing(8.0);
        filterPane.setAlignment(Pos.CENTER_RIGHT);
        return filterPane;
    }

    private void initTableView(String path) {
        rootPath.set(path);
        currentPath.set(path);

        items = FXCollections.observableArrayList();
        tableView = new TableView<>();
        tableView.getStyleClass().add("folder-table-view");
        filteredList = new FilteredList<>(items, e -> true);
        tableView.setItems(filteredList);
        // 仅在加载完成且没有文件时显示空状态
        EmptyState emptyState = EmptyState.of(Material2OutlinedAL.FOLDER_OPEN,
                "没有文件", "当前目录中没有可显示的文件。");
        emptyState.setCompact(true);
        tableView.setPlaceholder(emptyState);
        emptyState.visibleProperty().bind(Bindings.isEmpty(items).and(filesLoading.not()));
        TableColumn<AsmrFile, String> nameCol = new TableColumn<>("名称");
        nameCol.getStyleClass().add("detail-table-name");
        nameCol.setPrefWidth(550);
        TableColumn<AsmrFile, String> typeCol = new TableColumn<>("类型");
        typeCol.getStyleClass().add("detail-table-type");
        typeCol.setPrefWidth(60);
        tableView.getColumns().addAll(typeCol, nameCol);
        nameCol.setCellValueFactory(new PropertyValueFactory<AsmrFile, String>("displayName"));
        typeCol.setCellValueFactory(new PropertyValueFactory<AsmrFile, String>("type"));
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String s, boolean b) {
                if (b) {
                    setGraphic(null);
                } else {
                    setGraphic(buildFileIcon(s));
                }
            }
        });

        tableView.setSortPolicy(table -> {
            FXCollections.sort(items, new AsmrFileComparator());
            return true;
        });

        tableView.setRowFactory(view -> {
            TableRow<AsmrFile> row = new TableRow<>() {
                @Override
                protected void updateItem(AsmrFile asmrFile, boolean empty) {
                    super.updateItem(asmrFile, empty);
                    if (empty || asmrFile == null) {
                        setDisable(true);
                        setContextMenu(null);
                        setOnMouseClicked(null);
                    } else {
                        setDisable(false);
                        setContextMenu(buildContextMenu(asmrFile));
                        setOnMouseClicked(e -> handleRowClick(e, asmrFile));
                    }
                }
            };
            return row;
        });

        loadFilesAsync(path, false);
    }

    private void loadFilesAsync(String path, boolean forceRefresh) {
        boolean force = forceRefresh || dirtyDirectories.contains(path);
        List<AsmrFile> cached = force ? null : listingCache.get(path);
        if (cached != null) {
            showFiles(cached);
            filesLoading.set(false);
            return;
        }
        filesLoading.set(true);
        int version = listingVersion;
        Thread.startVirtualThread(() -> {
            try {
                List<AsmrFile> files = source.listFiles(path, force);
                List<AsmrFile> finalFiles = files == null ? List.of() : List.copyOf(files);
                Platform.runLater(() -> {
                    if (version != listingVersion) {
                        // 请求期间发生过写操作，响应已过期，不能写回缓存
                        if (path.equals(currentPath.get())) {
                            filesLoading.set(false);
                        }
                        return;
                    }
                    dirtyDirectories.remove(path);
                    listingCache.put(path, finalFiles);
                    // 快速连续跳转时，旧目录的响应不能覆盖当前目录
                    if (path.equals(currentPath.get())) {
                        showFiles(finalFiles);
                        filesLoading.set(false);
                    }
                });
            } catch (RuntimeException exception) {
                Platform.runLater(() -> {
                    if (!path.equals(currentPath.get()) || version != listingVersion) {
                        return;
                    }
                    items.clear();
                    filesLoading.set(false);
                    EventBus.publish(NotificationKey.MESSAGE,
                            new MessageInfo(MessageType.WARNING, "读取文件列表失败：" + exception.getMessage()));
                });
            }
        });
    }

    /**
     * 展示目录内容。缓存条目可能带有上一轮 AI 名称预览，展示前复制，避免目录往返后残留。
     */
    private void showFiles(List<AsmrFile> files) {
        items.setAll(files.stream().map(WorkDetailView::copyForDisplay).toList());
        tableView.sort();
    }

    private static AsmrFile copyForDisplay(AsmrFile file) {
        return new AsmrFile(file.getFilename(), file.getFilepath(), file.getType(), file.isDir(),
                file.getHideName(), file.getSize(), file.getModified());
    }

    private void handleRowClick(MouseEvent mouseEvent, AsmrFile asmrFile) {
        if (mouseEvent.getButton() != MouseButton.PRIMARY || mouseEvent.getClickCount() != 2) {
            return;
        }
        if (tryPlayVideo(asmrFile)) {
            return;
        }
        String type = asmrFile.getType();
        if ("dir".equals(type)) {
            update(asmrFile.getFilepath());
        } else if (SupportedAudioFormat.contains(type)) {
            ObservableList<AudioTrack> list = playlistService.buildPlaylistFromFiles(viewModel.getWork(), tableView.getItems());
            int index = -1;
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).getUrl().equals(asmrFile.getFilepath())) {
                        index = i;
                        break;
                    }
                }
            }
            if (list != null && !list.isEmpty()) {
                VlcPlayer.getInstance().initAndPlay(list, Math.max(0, index));
            }
        } else if (isSubtitleFile(type)) {
            ViewFactory.subtitleViewerView(asmrFile, viewModel.getWork()).show();
        } else if (SupportedImageFormat.contains(type)) {
            ViewFactory.imageViewerView(asmrFile, viewModel.getWork()).show();
        } else {
            openWithDefaultHandler(asmrFile);
        }
    }

    /**
     * 若该条目应交给外部播放器，则在后台判定并播放。
     * <p>
     * 判定可能涉及 I/O（{@code .strm} 需读出内部地址，OpenList 还需请求直链），
     * 因此整个判定与播放都在虚拟线程完成，UI 线程不阻塞。
     * <p>
     * 普通视频与 {@code .strm} 之外的条目可在 UI 线程同步判定，直接返回 true；
     * 其余情况交给后台线程，由它决定是否真的播放。后台判定为非视频时，
     * 回到 UI 线程按原有分支继续处理。
     *
     * @param asmrFile 文件条目
     * @return 已确定由外部播放器处理（或已交给后台处理）时返回 true
     */
    private boolean tryPlayVideo(AsmrFile asmrFile) {
        if (!Config.setting.externalPlayerEnabled.get() || asmrFile.isDir()) {
            return false;
        }
        String type = asmrFile.getType();
        // 非 strm 的普通文件只看扩展名，无 I/O，可当场判定
        if (!"strm".equalsIgnoreCase(type)) {
            if (!SupportedVideoFormat.contains(type)) {
                return false;
            }
            playWithExternalPlayer(asmrFile, source);
            return true;
        }
        Thread.startVirtualThread(() -> {
            if (!VideoFileResolver.isVideoFile(asmrFile, source)) {
                // strm 目标是音频，交回 UI 线程走原有的音频播放分支
                Platform.runLater(() -> handleNonVideoClick(asmrFile));
                return;
            }
            launchResolved(asmrFile, source);
        });
        return true;
    }

    /**
     * 解析地址并调用外部播放器播放视频。
     * <p>
     * 地址解析可能涉及网络请求（OpenList 直链），放到虚拟线程执行。
     *
     * @param asmrFile 视频文件
     * @param source   文件所属来源
     */
    private void playWithExternalPlayer(AsmrFile asmrFile, MediaSource source) {
        Thread.startVirtualThread(() -> launchResolved(asmrFile, source));
    }

    /**
     * 在后台线程解析地址并启动播放器。
     *
     * @param asmrFile 视频文件
     * @param source   文件所属来源
     */
    private void launchResolved(AsmrFile asmrFile, MediaSource source) {
        String url = VideoFileResolver.resolveUrl(asmrFile, source);
        if (url == null || url.isBlank()) {
            Platform.runLater(() -> EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "获取播放地址失败")));
            return;
        }
        ExternalPlayerService.LaunchResult result = externalPlayerService.launch(url);
        if (!result.success()) {
            Platform.runLater(() -> EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, result.message())));
        }
    }

    /**
     * 双击后判定为「不是视频」的 strm 条目，回到 UI 线程按音频处理。
     *
     * @param asmrFile 文件条目
     */
    private void handleNonVideoClick(AsmrFile asmrFile) {
        ObservableList<AudioTrack> list = playlistService.buildPlaylistFromFiles(viewModel.getWork(), tableView.getItems());
        int index = -1;
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getUrl().equals(asmrFile.getFilepath())) {
                    index = i;
                    break;
                }
            }
        }
        if (list != null && !list.isEmpty()) {
            VlcPlayer.getInstance().initAndPlay(list, Math.max(0, index));
        }
    }

    /**
     * 交给系统默认程序打开，失败时提示。
     *
     * @param asmrFile 文件条目
     */
    private void openWithDefaultHandler(AsmrFile asmrFile) {
        if (!fileOperationService.openInSystem(viewModel.getWork(), asmrFile.getFilepath())) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "无法打开该文件"));
        }
    }

    /**
     * 判断是否在右键菜单中展示视频播放项。
     * <p>
     * 右键菜单在 UI 线程同步构建，不能读文件或发网络请求，因此这里只看扩展名：
     * 普通视频文件直接成立，{@code .strm} 一律展示（其目标类型在点击时才判定）。
     *
     * @param asmrFile 文件条目
     * @return 需要展示播放项时返回 true
     */
    private boolean isVideoEntryForMenu(AsmrFile asmrFile) {
        if (!Config.setting.externalPlayerEnabled.get()) {
            return false;
        }
        if (asmrFile.isDir()) {
            return false;
        }
        String type = asmrFile.getType();
        return SupportedVideoFormat.contains(type) || "strm".equalsIgnoreCase(type);
    }

    private ContextMenu buildContextMenu(AsmrFile asmrFile) {
        ContextMenu contextMenu = new ContextMenu();
        AsmrWork work = viewModel.getWork();

        MenuItem openInExploreMI = new MenuItem("打开", new FontIcon(Material2OutlinedAL.FOLDER_OPEN));
        openInExploreMI.setOnAction(e -> {
            if (!fileOperationService.openInSystem(work, asmrFile.getFilepath())) {
                EventBus.publish(NotificationKey.MESSAGE,
                        new MessageInfo(MessageType.WARNING, "无法打开该文件"));
            }
        });
        if (source.getType() == StorageType.LOCAL) {
            contextMenu.getItems().add(openInExploreMI);
        }

        if (SupportedImageFormat.contains(asmrFile.getType())) {
            MenuItem viewImgMI = new MenuItem("查看图片", new FontIcon(Material2OutlinedAL.IMAGE));
            viewImgMI.setOnAction(e -> ViewFactory.imageViewerView(asmrFile, work).show());
            MenuItem setMainCoverMI = new MenuItem("大封面");
            setMainCoverMI.setOnAction(e -> setAsCover(asmrFile, "cover-main"));
            MenuItem setThumbCoverMI = new MenuItem("小封面");
            setThumbCoverMI.setOnAction(e -> setAsCover(asmrFile, "cover-thumb"));
            javafx.scene.control.Menu setCoverMenu = new javafx.scene.control.Menu("设为封面",
                    new FontIcon(Material2OutlinedMZ.PHOTO), setMainCoverMI, setThumbCoverMI);
            MenuItem setImageMI = new MenuItem("设为背景", new FontIcon(Material2OutlinedMZ.PHOTO));
            setImageMI.setOnAction(e -> setAsBackground(asmrFile));
            contextMenu.getItems().add(0, viewImgMI);
            contextMenu.getItems().add(setCoverMenu);
            contextMenu.getItems().add(setImageMI);
        }

        if (SupportedAudioFormat.contains(asmrFile.getType())) {
            MenuItem playMI = new MenuItem("播放", new FontIcon(Material2OutlinedMZ.PLAY_CIRCLE_OUTLINE));
            MenuItem addToPlaylistMI = new MenuItem("添加到播放列表", new FontIcon(Material2OutlinedMZ.PLAYLIST_PLAY));
            MenuItem addToCollectionMI = new MenuItem("添加至歌单", new FontIcon(Material2OutlinedMZ.PLAYLIST_ADD));
            playMI.setOnAction(e -> VlcPlayer.getInstance().addAndPlay(new AudioTrack(work, asmrFile)));
            addToPlaylistMI.setOnAction(e -> VlcPlayer.getInstance().add(new AudioTrack(work, asmrFile)));
            addToCollectionMI.setOnAction(e -> {
                ViewTuple<AddToSheetView, AddToSheetViewModel> viewTuple = FluentViewLoader.fxmlView(AddToSheetView.class)
                        .viewModel(new AddToSheetViewModel(work, asmrFile)).load();
                Dialogs.show(viewTuple.getCodeBehind());
            });
            contextMenu.getItems().addAll(0, List.of(playMI, addToPlaylistMI, addToCollectionMI));
        }

        if (isVideoEntryForMenu(asmrFile)) {
            MenuItem playVideoMI = new MenuItem("用第三方播放器播放",
                    new FontIcon(Material2OutlinedMZ.PLAY_CIRCLE_OUTLINE));
            playVideoMI.setOnAction(e -> tryPlayVideo(asmrFile));
            contextMenu.getItems().add(0, playVideoMI);
        }

        if (asmrFile.isDir()) {
            MenuItem renameMi = new MenuItem("字幕重命名", new FontIcon(Material2OutlinedMZ.PALETTE));
            renameMi.setOnAction(e -> {
                // 该工具会直接改名目录内文件，无论是否执行都丢弃该目录的旧列表
                invalidateListing(asmrFile.getFilepath());
                ViewTuple<SubtitleRenameView, SubtitleRenameViewModel> viewTuple = ViewFactory.subtitleRenameView(work, asmrFile.getFilepath());
                Dialogs.show(viewTuple.getCodeBehind());
            });
            // 目录菜单可能只有「打开」一项（非本地来源时一项都没有），只能追加，不能按下标插入
            contextMenu.getItems().add(renameMi);
        }

        if (isSubtitleFile(asmrFile.getType())) {
            MenuItem viewSubMI = new MenuItem("查看字幕", new FontIcon(Material2OutlinedAL.FORMAT_LIST_BULLETED));
            viewSubMI.setOnAction(e -> ViewFactory.subtitleViewerView(asmrFile, work).show());
            contextMenu.getItems().add(0, viewSubMI);
        }

        MenuItem deleteMI = new MenuItem("删除", new FontIcon(Material2OutlinedAL.DELETE_OUTLINE));
        deleteMI.getStyleClass().add("work-delete-menu-item");
        deleteMI.setOnAction(e -> deleteFile(asmrFile));
        contextMenu.getItems().addAll(new SeparatorMenuItem(), deleteMI);

        return contextMenu;
    }

    private void setAsBackground(AsmrFile asmrFile) {
        if (!SupportedImageFormat.contains(asmrFile.getType())) {
            return;
        }
        AsmrWork work = viewModel.getWork();
        String path = currentPath.get();
        Thread.startVirtualThread(() -> {
            String newName = fileOperationService.setAsBackground(work, asmrFile);
            Platform.runLater(() -> {
                if (newName == null) {
                    Alerts.warning().message("操作失败，可能该文件正在使用").cancel("确定").show();
                    return;
                }
                asmrFile.setFilename(newName);
                asmrFile.setFilepath(replaceFileName(asmrFile.getFilepath(), newName));
                tableView.refresh();
                invalidateListing(path);
            });
        });
    }

    private void setAsCover(AsmrFile asmrFile, String coverName) {
        if (!SupportedImageFormat.contains(asmrFile.getType())) {
            return;
        }
        AsmrWork work = viewModel.getWork();
        String path = currentPath.get();
        Thread.startVirtualThread(() -> {
            String newName = fileOperationService.setAsCover(work, asmrFile, coverName);
            Platform.runLater(() -> {
                if (newName == null) {
                    Alerts.warning().message("操作失败，可能该文件正在使用").cancel("确定").show();
                    return;
                }
                asmrFile.setFilename(newName);
                asmrFile.setFilepath(replaceFileName(asmrFile.getFilepath(), newName));
                tableView.refresh();
                invalidateListing(path);
                EventBus.publish(NotificationKey.MAIN_GRID_WORK_UPDATE, work);
            });
        });
    }

    private static String replaceFileName(String path, String newName) {
        int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return separator >= 0 ? path.substring(0, separator + 1) + newName : newName;
    }

    private void deleteFile(AsmrFile asmrFile) {
        String targetType = asmrFile.isDir() ? "文件夹" : "文件";
        Label targetLabel = new Label(asmrFile.getFilename());
        targetLabel.setWrapText(true);
        targetLabel.getStyleClass().add("delete-file-target");
        VBox details = new VBox(6, new Label("删除目标"), targetLabel);
        details.getStyleClass().add("delete-file-details");

        Alerts.danger()
                .title("删除" + targetType)
                .message("将永久删除该" + targetType + "，且无法撤销。")
                .content(details)
                .ok("删除", handle -> {
                    AsmrWork work = viewModel.getWork();
                    Thread.startVirtualThread(() -> {
                        FileOperationService.OperationResult result =
                                fileOperationService.deleteFile(work, asmrFile);
                        Platform.runLater(() -> {
                            if (!result.success()) {
                                EventBus.publish(NotificationKey.MESSAGE,
                                        new MessageInfo(MessageType.WARNING, result.message()));
                                return;
                            }
                            invalidateListing(currentPath.get());
                            items.remove(asmrFile);
                        });
                    });
                })
                .cancel()
                .show();
    }

    /**
     * 判断文件类型是否为字幕文件（lrc/vtt/srt，不区分大小写）。
     *
     * @param type 文件类型（扩展名，不含点）
     * @return 是字幕文件返回 true
     */
    private boolean isSubtitleFile(String type) {
        return "lrc".equalsIgnoreCase(type) || "vtt".equalsIgnoreCase(type) || "srt".equalsIgnoreCase(type);
    }

    private ImageView buildFileIcon(String type) {
        ImageView imageView = new ImageView();
        imageView.setFitHeight(30);
        imageView.setPreserveRatio(true);
        Image image;
        if ("dir".equals(type)) {
            image = getFolderImage();
        } else if (SupportedAudioFormat.contains(type)) {
            image = getAudioImage();
        } else if (SupportedVideoFormat.contains(type)) {
            image = getVideoImage();
        } else if (SupportedImageFormat.contains(type)) {
            image = getPicImage();
        } else {
            image = getOtherImage();
        }
        imageView.setImage(image);
        return imageView;
    }

    /**
     * 更新表格（子目录导航），异步加载。
     */
    private void update(String path) {
        update(path, false);
    }

    /**
     * 切换到指定目录。
     *
     * @param path         目标目录
     * @param forceRefresh 为 true 时跳过视图缓存并强制来源刷新
     */
    private void update(String path, boolean forceRefresh) {
        resetFileNameTranslationState();
        if (!path.equals(currentPath.get())) {
            currentPath.set(path);
            items.clear();
        }
        loadFilesAsync(path, forceRefresh);
    }

    /**
     * 失效指定目录及其子目录的列表缓存，写操作后调用。
     * <p>
     * 同时把该目录标记为待强制刷新：下次进入时要求来源重新列举，
     * 而不是再次命中来源侧的旧缓存。
     */
    private void invalidateListing(String dirPath) {
        if (dirPath == null) {
            return;
        }
        listingCache.keySet().removeIf(key -> key.equals(dirPath)
                || key.startsWith(dirPath + "/")
                || key.startsWith(dirPath + File.separator));
        dirtyDirectories.add(dirPath);
        listingVersion++;
    }

    private void confirmFileNameRenames() {
        if (!fileNameTranslationReady) {
            return;
        }
        long renameCount = items.stream()
                .filter(file -> file.getPreviewName() != null && !file.getPreviewName().isBlank()
                        && !file.getFilename().equals(file.getPreviewName()))
                .count();
        if (renameCount == 0) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "当前没有可应用的名称预览"));
            return;
        }
        Alerts.warning()
                .title("确认重命名")
                .message("将重命名当前层级的 " + renameCount + " 个文件或目录。此操作会直接修改存储中的真实名称，是否继续？")
                .ok("重命名", handle -> applyFileNameRenames())
                .cancel("取消")
                .show();
    }

    private void applyFileNameRenames() {
        translateFileNamesBtn.setDisable(true);
        Thread.startVirtualThread(() -> {
            FileOperationService.RenameBatchResult result = fileOperationService.renamePreviewNames(viewModel.getWork(), List.copyOf(items));
            Platform.runLater(() -> {
                resetFileNameTranslationState();
                if (result.renamedCount() > 0) {
                    EventBus.publish(NotificationKey.MESSAGE,
                            new MessageInfo(MessageType.SUCCESS, "已重命名 " + result.renamedCount() + " 个文件或目录"));
                }
                if (!result.errors().isEmpty()) {
                    EventBus.publish(NotificationKey.MESSAGE,
                            new MessageInfo(MessageType.WARNING, "部分重命名未完成：" + String.join("；", result.errors())));
                }
                invalidateListing(currentPath.get());
                update(currentPath.get(), true);
            });
        });
    }

    private void resetFileNameTranslationState() {
        fileNameTranslationReady = false;
        if (translateFileNamesBtn == null) {
            return;
        }
        translateFileNamesBtn.setDisable(false);
        translateFileNamesBtn.setGraphic(AiFileNameTranslationAction.createIdleIcon());
        translateFileNamesBtn.setTooltip(new Tooltip("AI 翻译文件和目录名称（仅预览，不重命名）"));
    }

    private void filter(int filterType) {
        filteredList.setPredicate(asmrFile -> {
            if (ALL == filterType) {
                return true;
            } else if (PICTURE == filterType) {
                return SupportedImageFormat.contains(asmrFile.getType());
            } else if (AUDIO == filterType) {
                return SupportedAudioFormat.contains(asmrFile.getType());
            } else if (VIDEO == filterType) {
                return SupportedVideoFormat.contains(asmrFile.getType());
            }
            return false;
        });
    }

    private Image getAudioImage() {
        if (audioImage == null) {
            audioImage = new Image(this.getClass().getResource("/cn/tealc/asmr/image/suffix/file-audio.png").toExternalForm());
        }
        return audioImage;
    }

    private Image getPicImage() {
        if (picImage == null) {
            picImage = new Image(this.getClass().getResource("/cn/tealc/asmr/image/suffix/file-image.png").toExternalForm());
        }
        return picImage;
    }

    private Image getOtherImage() {
        if (otherImage == null) {
            otherImage = new Image(this.getClass().getResource("/cn/tealc/asmr/image/suffix/file-unknown.png").toExternalForm());
        }
        return otherImage;
    }

    private Image getVideoImage() {
        if (videoImage == null) {
            videoImage = new Image(this.getClass().getResource("/cn/tealc/asmr/image/suffix/file-video.png").toExternalForm());
        }
        return videoImage;
    }

    private Image getFolderImage() {
        if (folderImage == null) {
            folderImage = new Image(this.getClass().getResource("/cn/tealc/asmr/image/suffix/file-folder.png").toExternalForm());
        }
        return folderImage;
    }
}
