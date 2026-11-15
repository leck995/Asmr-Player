package cn.tealc.asmr.ui.work.cells;

import cn.tealc.asmr.eventbus.EventBus;

import atlantafx.base.theme.Styles;
import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.ui.component.OverlayLayer;
import cn.tealc.asmr.factory.ViewFactory;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.AudioTrack;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.model.translation.TranslationPreviewItem;
import cn.tealc.asmr.player.VlcPlayer;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.service.MetadataSyncService;
import cn.tealc.asmr.service.PlaylistService;
import cn.tealc.asmr.service.FileOperationService;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.ui.work.*;
import cn.tealc.asmr.ui.tools.MoveAsmrPage;
import cn.tealc.asmr.ui.tools.AiTitleTranslationAction;
import cn.tealc.asmr.ui.tools.SubtitleRenameView;
import cn.tealc.asmr.ui.tools.SubtitleRenameViewModel;
import cn.tealc.asmr.ui.work.list.AdaptiveWidthCell;
import cn.tealc.asmr.ui.work.list.DefaultWorkCellContext;
import cn.tealc.asmr.ui.work.list.WorkCellContext;
import cn.tealc.asmr.utils.FXResourcesLoader;
import cn.tealc.asmr.utils.CoverUtils;
import cn.tealc.asmr.utils.CoverViewport;
import cn.tealc.asmr.utils.FileUtils;
import cn.tealc.asmr.utils.WebUtils;
import cn.tealc.asmr.mvvm.ViewTuple;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.controlsfx.control.Rating;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

/**
 * 大图模式作品卡片。
 * <p>
 * 视觉层次自上而下：封面（编号 / 收藏 / 日期浮层）→ 标题 → 社团与标记 → 评分 →
 * 「标签」分组 → 「声优」分组。分组标签只在存在对应内容时出现，避免卡片出现空档。
 * <p>
 * 相比 {@link SmallWorkCell}：默认更宽、封面取主图，单独展示社团与"声优"分组，评分带星形图标，
 * 标题是否截断受设置 {@code gridItemDetailModelMaxTitle} 控制并配悬浮提示；删除只走上下文，
 * 不支持专辑内嵌的"移出本集"。
 *
 * @author Leck
 */
public class BigWorkCell extends VBox implements WorkCellActions, AdaptiveWidthCell {
    /** 未被列表分配宽度时使用的默认卡片宽度。 */
    private static final double DEFAULT_CARD_WIDTH = 320;
    /** 内容区左右内边距。 */
    private static final double CONTENT_PADDING = 12;
    /** 封面顶部圆角，与卡片圆角保持一致。 */
    private static final double COVER_RADIUS = 12;
    /** 标题受高度限制时保留的最大行数。 */
    private static final int TITLE_MAX_LINES = 3;
    /** 标题行尾为 AI 翻译按钮与省略号预留的宽度。 */
    private static final double TITLE_RESERVED_WIDTH = 26;
    /**
     * 封面解码宽度上限。
     * <p>
     * 封面源图普遍不到 720p，4:3 下宽约 960；请求更宽只会让 JavaFX 放大解码缓冲，
     * 既不会更清晰，也让每张卡多占数 MB 内存。
     */
    private static final int COVER_MAX_WIDTH = 960;
    /** 已评分状态，供 CSS 区分实心/空心星。 */
    private static final PseudoClass RATED = PseudoClass.getPseudoClass("rated");

    private static final Image DEFAULT_IMAGE = new Image(FXResourcesLoader.image("cover-main.jpg"), 400, 300, true, true);
    private final WorkService workService = DI.get(WorkService.class);
    private final MetadataSyncService metadataSyncService = DI.get(MetadataSyncService.class);
    private final PlaylistService playlistService = DI.get(PlaylistService.class);
    private final FileOperationService fileOperationService = DI.get(FileOperationService.class);

    /**
     * 卡片实际宽度。
     * <p>
     * 由列表按容器宽度分配，默认取设置值；宽度变化时同步封面、标题与裁剪区域，
     * 使卡片能铺满整行、不留下空白。
     */
    private final DoubleProperty cardWidth =
            new SimpleDoubleProperty(DEFAULT_CARD_WIDTH);
    /** 列表是否已下发真实宽度；构造阶段的默认宽度不参与取图。 */
    private boolean widthAssigned;
    /** 本页是否已按当前宽度取过封面；窗口缩放不再重新解码。 */
    private boolean coverLoaded;

    private final WorkCellContext context;
    private AsmrWork work;
    private final ImageView itemAlbum = new ImageView();
    private final Label workIdLabel = new Label();
    private final Label dateLabel = new Label();
    private final ToggleButton starBtn = new ToggleButton();
    private final TextFlow itemTitle = new TextFlow();
    private final Text titleText = new Text();
    private final Button titleTranslateButton = new Button();
    private TranslationPreviewItem pendingTitleTranslation;
    private final Label circleLabel = new Label();
    private final HBox tagPane = new HBox();
    private final Button ratingButton = new Button();
    private final FontIcon ratingIcon = new FontIcon(Material2OutlinedMZ.STAR_BORDER);
    private final Label tagsSectionLabel = new Label("标签");
    private final Label vasSectionLabel = new Label("声优");
    private final FlowPane categoriesPane = new FlowPane();
    private final FlowPane vaPane = new FlowPane();
    private final Tooltip titleTooltip = new Tooltip();
    private String displayTitle = "";
    private boolean titleTooltipInstalled;

    /**
     * 以默认上下文创建大图卡片。
     *
     * @param work 要展示的作品
     */
    public BigWorkCell(AsmrWork work) {
        this(work, DefaultWorkCellContext.INSTANCE);
    }

    /**
     * 构建卡片：绑定自适应宽度，装配封面、标题、社团、评分与「标签/声优」分组并注册交互事件，
     * 末尾调用 {@link #refreshData(AsmrWork)} 装载数据。
     *
     * @param work    要展示的作品；为 {@code null} 时直接返回、不构建子节点
     * @param context 与宿主列表协作的上下文（跳转、收藏、删除回传），为 {@code null} 时回退到 {@link DefaultWorkCellContext}
     */
    public BigWorkCell(AsmrWork work, WorkCellContext context) {
        this.context = context == null ? DefaultWorkCellContext.INSTANCE : context;
        if (work == null) return;
        this.work = work;

        // ---- 封面 ----
        itemAlbum.setAccessibleText(work.getFilepath());
        itemAlbum.fitWidthProperty().bind(cardWidth);
        itemAlbum.fitHeightProperty().bind(cardWidth.multiply(0.75));
        itemAlbum.setSmooth(true);
        CoverViewport.configure(itemAlbum);
        itemAlbum.setId("grid-item-cover");

        SVGPath imageClip = new SVGPath();
        updateCoverClip(imageClip);
        itemAlbum.setClip(imageClip);

        // ---- 作品编号标签 ----
        // 事件处理器统一读取字段 this.work：卡片被 refreshData 复用到新作品后仍能取到当前作品
        workIdLabel.setId("grid-item-work-id");
        workIdLabel.getStyleClass().add("cell-work-id");
        workIdLabel.setOnMouseClicked(mouseEvent -> {
            String workId = this.work.getWorkId();
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(workId);
            clipboard.setContent(content);
            EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(cn.tealc.asmr.model.message.MessageType.SUCCESS, String.format("%s:已经复制到剪切板", workId)));
            mouseEvent.consume();
        });

        // ---- 日期 ----
        dateLabel.setId("grid-item-date");
        dateLabel.getStyleClass().add("cell-date");

        // ---- 播放按钮 ----
        Button playBtn = new Button();
        playBtn.setGraphic(new FontIcon(MaterialDesignP.PLAY_CIRCLE));
        playBtn.getStyleClass().addAll("lc-svg-btn", "play-btn");
        playBtn.setTooltip(new Tooltip("播放"));
        playBtn.setOnAction(actionEvent -> {
            ObservableList<AudioTrack> musics = playlistService.buildPlaylist(this.work);
            if (musics != null) {
                VlcPlayer.getInstance().initAndPlay(musics, 0);
            } else {
                EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(MessageType.WARNING, "当前文件夹无可识别的音频文件"));
            }
        });

        // ---- 收藏按钮 ----
        starBtn.setGraphic(new FontIcon());
        starBtn.setId("grid-item-star");
        starBtn.getStyleClass().addAll("lc-svg-btn", "star-btn");
        starBtn.setOnAction(actionEvent -> {
            workService.toggleFavorite(this.work);
            context.favoriteChanged(this.work);
        });

        // ---- 头部容器 ----
        // 编号与日期统一压封面底边，形成一条元数据带；封面顶部只留收藏与播放
        StackPane headPane = new StackPane(itemAlbum, workIdLabel, dateLabel, playBtn, starBtn);
        headPane.getStyleClass().add("head");
        StackPane.setAlignment(workIdLabel, Pos.BOTTOM_LEFT);
        StackPane.setAlignment(dateLabel, Pos.BOTTOM_RIGHT);
        StackPane.setAlignment(playBtn, Pos.CENTER);
        StackPane.setAlignment(starBtn, Pos.TOP_RIGHT);

        // ---- 标题 ----
        itemTitle.prefWidthProperty().bind(cardWidth.subtract(CONTENT_PADDING * 2));
        titleText.getStyleClass().add("cell-title-text");
        titleTranslateButton.setGraphic(AiTitleTranslationAction.createIdleIcon());
        titleTranslateButton.setTooltip(new Tooltip("AI 翻译作品名称"));
        titleTranslateButton.getStyleClass().add("ai-title-translate-button");
        titleTranslateButton.setOnAction(event -> {
            if (pendingTitleTranslation != null) {
                if (AiTitleTranslationAction.apply(pendingTitleTranslation, this.work)) {
                    pendingTitleTranslation = null;
                    refreshTitle(this.work);
                    titleTranslateButton.setGraphic(AiTitleTranslationAction.createIdleIcon());
                    titleTranslateButton.setTooltip(new Tooltip("AI 翻译作品名称"));
                }
                return;
            }
            AiTitleTranslationAction.preview(this.work, titleTranslateButton, item -> {
                pendingTitleTranslation = item;
                displayTitle = item.translatedText();
                applyTitleClamp();
                titleTranslateButton.setGraphic(AiTitleTranslationAction.createReadyIcon());
                titleTranslateButton.setTooltip(new Tooltip("应用中文标题"));
            });
        });
        titleTranslateButton.setOnMouseClicked(mouseEvent -> mouseEvent.consume());
        itemTitle.getChildren().addAll(titleText, titleTranslateButton);
        itemTitle.setId("grid-item-title");
        itemTitle.getStyleClass().add("cell-title");
        // 首次套用样式表后字体才最终确定，此时按真实字体重算截断长度
        titleText.fontProperty().addListener((observable, oldFont, newFont) -> applyTitleClamp());

        // ---- 社团 ----
        circleLabel.setGraphic(new FontIcon(MaterialDesignL.LABEL));
        circleLabel.setId("grid-item-circle");
        circleLabel.getStyleClass().add("cell-circle");
        circleLabel.setMinWidth(0);
        circleLabel.maxWidthProperty().bind(cardWidth.divide(2));
        circleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        circleLabel.setOnMouseClicked(mouseEvent -> {
            context.navigate(AsmrKeyType.CIRCLE, this.work.getCircle());
            mouseEvent.consume();
        });

        // ---- 标记栏（字幕 / 全年龄）：靠行尾右对齐 ----
        tagPane.setAlignment(Pos.CENTER_RIGHT);
        tagPane.setId("list-item-tag-pane");
        tagPane.getStyleClass().add("tag");
        tagPane.setSpacing(6);

        // ---- 评分 ----
        ratingButton.setGraphic(ratingIcon);
        ratingButton.getStyleClass().addAll("rating-text-button", "cell-rating");
        ratingButton.setTooltip(new Tooltip("设置作品评分"));
        ratingButton.setMaxWidth(Region.USE_PREF_SIZE);
        ratingButton.setOnAction(actionEvent -> showRatingDialog());
        ratingButton.setOnMouseClicked(mouseEvent -> mouseEvent.consume());

        // ---- 评分 + 标记同一行：评分居左，字幕/全年龄被弹性空隙推到右端 ----
        Region ratingSpacer = new Region();
        HBox.setHgrow(ratingSpacer, Priority.ALWAYS);
        HBox ratingRow = new HBox(8, ratingButton, ratingSpacer, tagPane);
        ratingRow.setAlignment(Pos.CENTER_LEFT);
        ratingRow.getStyleClass().add("cell-rating-row");

        // ---- 分组标题 ----
        tagsSectionLabel.getStyleClass().addAll("cell-section-label", "cell-section-tags");
        vasSectionLabel.getStyleClass().addAll("cell-section-label", "cell-section-vas");
        tagsSectionLabel.setMaxWidth(Region.USE_PREF_SIZE);
        vasSectionLabel.setMaxWidth(Region.USE_PREF_SIZE);

        // ---- 分类 / 声优 FlowPane ----
        categoriesPane.setId("grid-item-categories");
        categoriesPane.setVgap(6);
        categoriesPane.setHgap(6);
        vaPane.setId("grid-item-vas");
        vaPane.setMaxHeight(90);
        vaPane.setVgap(6);
        vaPane.setHgap(6);

        // ---- 文本内容区：标题 → 社团 → 评分与标记 → 标签 → 声优 ----
        VBox contentPane = new VBox(8, itemTitle, circleLabel, ratingRow,
                tagsSectionLabel, categoriesPane, vasSectionLabel, vaPane);
        contentPane.getStyleClass().add("cell-content");
        contentPane.setPadding(new Insets(10, CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING));

        // ---- 布局 ----
        setMinWidth(Region.USE_PREF_SIZE);
        maxWidthProperty().bind(cardWidth);
        prefWidthProperty().bind(cardWidth);
        getChildren().addAll(headPane, contentPane);
        setPadding(Insets.EMPTY);
        setSpacing(0);
        getStyleClass().addAll("work-cell", "big-work-cell");

        // 宽度变化（含设置调整与列表自适应分配）会同时影响封面裁剪与标题可容纳字数；
        // 监听器放在布局完成后注册，避免构造中途误清标题
        cardWidth.addListener((observable, oldValue, newValue) -> {
            updateCoverClip(imageClip);
            applyTitleClamp();
        });

        // ---- 右键 / 点击 ----
        // 菜单按当前作品延迟构建：卡片刷新为新作品后，菜单项也指向新作品
        setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton().equals(MouseButton.SECONDARY)) {
                ContextMenu contextMenu = WorkContextMenuFactory.create(this.work, this);
                contextMenu.show(this.getScene().getWindow(), mouseEvent.getScreenX(), mouseEvent.getScreenY());
            }
            if (mouseEvent.getButton().equals(MouseButton.PRIMARY)) {
                AsmrWork current = this.work;
                if (current.getStorageType() == 0) {
                    if (!fileOperationService.exists(current)) {
                        EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(MessageType.WARNING, "该音声不存在，请检查路径或更新"));
                        return;
                    }
                }
                ViewTuple<WorkDetailView, WorkDetailViewModel> viewTuple = ViewFactory.workDetailView(current);
                OverlayLayer.show((javafx.scene.layout.Pane) viewTuple.getView());
                mouseEvent.consume();
            }
        });

        // ---- 装载数据 ----
        refreshData(work);
    }

    /**
     * 重新装载所有数据：封面、作品编号、日期、收藏、标题、社团、标签、评分、分类、声优
     *
     * @param work 最新的作品数据，更新内部引用后刷新全部展示
     */
    public void refreshData(AsmrWork work) {
        this.work = work;
        // 封面：等列表下发真实宽度后再取图
        coverLoaded = false;
        loadCoverIfNeeded();
        // 作品编号
        if (work.getWorkId() != null) {
            workIdLabel.setText(work.getWorkId());
            workIdLabel.setVisible(true);
        } else {
            workIdLabel.setVisible(false);
        }
        FontIcon mediaSourceIcon = new FontIcon();
        mediaSourceIcon.getStyleClass().add(work.getStorageType() == 1 ? "web" : "local");
        workIdLabel.setGraphic(mediaSourceIcon);
        workIdLabel.setGraphicTextGap(5);
        // 日期
        if (work.getRelease() != null) {
            dateLabel.setText(work.getRelease());
            dateLabel.setVisible(true);
        } else {
            dateLabel.setVisible(false);
        }
        // 收藏
        starBtn.setSelected(work.getFavorites());
        // 标题
        refreshTitle(work);
        // 社团
        boolean hasCircle = work.getCircle() != null && !work.getCircle().isBlank();
        if (hasCircle) {
            circleLabel.setText(work.getCircle());
        }
        circleLabel.setVisible(hasCircle);
        circleLabel.setManaged(hasCircle);
        // 标签
        tagPane.getChildren().clear();
        if (work.getSubtitle()) {
            Label label = new Label("字幕");
            label.setId("grid-item-subtext");
            label.getStyleClass().add("subtitle");
            tagPane.getChildren().add(label);
        }
        if (!work.getNsfw()) {
            Label nsfwLabel = new Label("全年龄");
            nsfwLabel.getStyleClass().add("nsfw");
            nsfwLabel.setId("grid-item-nsfw");
            tagPane.getChildren().add(nsfwLabel);
        }
        // 没有字幕/全年龄标记时整条标记栏不占位，避免评分行出现多余留白
        boolean hasTags = !tagPane.getChildren().isEmpty();
        tagPane.setVisible(hasTags);
        tagPane.setManaged(hasTags);
        refreshRatingText();
        // 自定义标签置于首个元数据标签区域的最前方
        fillCustomTags(categoriesPane, work.getTags());
        // 分类
        fillFlowPane(categoriesPane, work.getCategories(), "category", "category");
        setSectionVisible(tagsSectionLabel, !categoriesPane.getChildren().isEmpty());
        // 声优
        vaPane.getChildren().clear();
        fillFlowPane(vaPane, work.getVas(), "va", "actor");
        setSectionVisible(vasSectionLabel, !vaPane.getChildren().isEmpty());
    }

    /**
     * 分组标题随内容一起显示或隐藏，隐藏时不占据布局空间。
     */
    private void setSectionVisible(Label label, boolean visible) {
        label.setVisible(visible);
        label.setManaged(visible);
    }

    /**
     * 将逗号分隔的字符串拆成 Label 填入 FlowPane
     *
     * @param pane         目标 FlowPane
     * @param csv          逗号分隔的字符串
     * @param accessibleText Label 的 accessibleText，用于点击事件区分类型
     * @param styleClass   Label 的样式类
     */
    private void fillFlowPane(FlowPane pane, String csv, String accessibleText, String styleClass) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String item : csv.split(",")) {
            if (item.isBlank()) {
                continue;
            }
            Label label = new Label(item);
            label.setAccessibleText(accessibleText);
            label.setOnMouseClicked(mouseEvent -> {
                Label source = (Label) mouseEvent.getSource();
                if ("category".equals(accessibleText)) {
                    context.navigate(AsmrKeyType.CATEGORY, source.getText());
                } else if ("va".equals(accessibleText)) {
                    context.navigate(AsmrKeyType.VA, source.getText());
                }
                mouseEvent.consume();
            });
            label.getStyleClass().add(styleClass);
            pane.getChildren().add(label);
        }
    }

    /**
     * 将自定义标签置于分类 FlowPane 的前方。
     */
    private void fillCustomTags(FlowPane pane, String csv) {
        pane.getChildren().clear();
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String item : csv.split(",")) {
            if (item.isBlank()) {
                continue;
            }
            Label label = new Label(item);
            label.getStyleClass().add("custom-tag");
            label.setOnMouseClicked(mouseEvent -> {
                context.navigate(AsmrKeyType.TAG, item);
                mouseEvent.consume();
            });
            pane.getChildren().add(label);
        }
    }

    /**
     * 显示作品评分弹窗，并在确认后保存评分。
     */
    private void showRatingDialog() {
        double currentRate = work.getRate() == null ? 0 : work.getRate();
        Rating ratingControl = new Rating(5);
        ratingControl.setPartialRating(true);
        ratingControl.setRating(currentRate);
        ratingControl.getStyleClass().add("rating-dialog-rating");
        Label ratingValueLabel = new Label(formatRating(ratingControl.getRating()));
        ratingValueLabel.getStyleClass().add("rating-dialog-value");
        ratingControl.ratingProperty().addListener((observable, oldValue, newValue) -> {
            double normalizedRating = roundToHalfStar(newValue.doubleValue());
            if (Double.compare(normalizedRating, newValue.doubleValue()) != 0) {
                ratingControl.setRating(normalizedRating);
                return;
            }
            ratingValueLabel.setText(formatRating(normalizedRating));
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
                .ok("保存评分", handle -> {
                    double rating = ratingControl.getRating();
                    workService.updateRate(work, rating);
                    work.setRate(rating);
                    refreshRatingText();
                })
                .cancel()
                .show();
    }

    /**
     * 刷新评分区域：未评分显示空心星 + 「评分」，已评分显示实心星 + 数值。
     */
    private void refreshRatingText() {
        Double rate = work.getRate();
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

    // ===================== WorkCellActions 实现 =====================

    @Override
    public void onSync(AsmrWork work) {
        if (Config.setting.sourcesUrl.get() == null || !Config.setting.sourcesUrl.get().startsWith("https://")) {
            EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(MessageType.WARNING, "请先正确设置同步网址"));
            return;
        }
        if (work.getWorkId() != null && !work.getWorkId().isBlank()) {
            metadataSyncService.syncSingle(work);
        } else {
            EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(MessageType.WARNING, "没有识别到作品编号"));
        }
    }

    @Override
    public void onEditInfo(AsmrWork work) {
        ViewTuple<UpdateInfoView, UpdateInfoViewModel> view = ViewFactory.updateInfoView(work);
        Dialogs.show(view.getCodeBehind());
    }

    @Override
    public void onEditTags(AsmrWork work) {
        ViewTuple<UpdateTagView, UpdateTagViewModel> viewTuple = ViewFactory.updateTagView(work);
        Dialogs.show(viewTuple.getCodeBehind());
    }

    @Override
    public void onDelete(AsmrWork work) {
        context.delete(work);
    }

    @Override
    public void onCopyWorkId(AsmrWork work) {
        if (work.getWorkId() != null && !work.getWorkId().isBlank()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(work.getWorkId()));
            clipboard.setContent(content);
            EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(MessageType.SUCCESS, String.format("%s:已经复制到剪切板", work.getWorkId())));
        } else {
            EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(MessageType.WARNING, "没有识别到作品编号"));
        }
    }

    @Override
    public void onOpenWeb(AsmrWork work) {
        String sourcesUrl = Config.setting.getSourcesUrl();
        if (sourcesUrl != null && !sourcesUrl.isEmpty()) {
            if (sourcesUrl.endsWith("/")) {
                sourcesUrl = sourcesUrl.substring(0, sourcesUrl.length() - 1);
            }
            String url = String.format("%s/pro/work/=/product_id/%s.html", sourcesUrl, work.getWorkId());
            WebUtils.browser(url);
        } else {
            EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(MessageType.WARNING, "请先设置同步网址"));
        }
    }

    @Override
    public void onToggleFavorite(AsmrWork work) {
        workService.toggleFavorite(work);
        context.favoriteChanged(work);
    }

    @Override
    public void onMove(AsmrWork work) {
        // MoveAsmrPage 实现 DialogPage，标题、正文、按钮由弹窗分槽承载
        Dialogs.show(new MoveAsmrPage(work));
    }

    @Override
    public void onSubtitleRename(AsmrWork work) {
        ViewTuple<SubtitleRenameView, SubtitleRenameViewModel> viewTuple = ViewFactory.subtitleRenameView(work, work.getFilepath());
        Dialogs.show(viewTuple.getCodeBehind());
    }

    @Override
    public void onOpenFolder(AsmrWork work) {
        if (!fileOperationService.openInSystem(work, work.getFilepath())) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "无法打开文件夹，可能仅本地来源支持"));
        }
    }


    /**
     * 按卡片宽度加载封面，每个作品只解码一次。
     * <p>
     * 宽度来自列表首次下发的真实值（构造阶段的默认宽度不算），窗口缩放只拉伸现有图片，
     * 翻页或重建卡片时才按新宽度重新取图。请求宽度以 {@link #COVER_MAX_WIDTH} 封顶，
     * 避免超宽卡片把不足 720p 的源图放大成更大的解码缓冲。
     */
    private void loadCoverIfNeeded() {
        AsmrWork work = this.work;
        if (work == null || !widthAssigned || coverLoaded) {
            return;
        }
        coverLoaded = true;
        int requestWidth = (int) Math.ceil(Math.min(COVER_MAX_WIDTH, cardWidth.get()));
        int requestHeight = Math.max(1, (int) Math.round(requestWidth * 0.75));
        Thread.startVirtualThread(() -> {
            Image image = CoverUtils.getCoverForWork(
                    work, "main", "cover-main",
                    requestWidth, requestHeight, false, DEFAULT_IMAGE);
            Platform.runLater(() -> {
                if (this.work != work) {
                    return;
                }
                itemAlbum.setImage(image);
            });
        });
    }

    private void updateCoverClip(SVGPath coverClip) {
        double width = cardWidth.get();
        double height = width * 0.75;
        double radius = COVER_RADIUS;
        coverClip.setContent(String.format(Locale.ROOT,
                "M 0 %.2f L 0 %.2f Q 0 0 %.2f 0 L %.2f 0 Q %.2f 0 %.2f %.2f L %.2f %.2f Z",
                height, radius, radius, width - radius, width, width, radius, width, height));
    }

    @Override
    public void setCardWidth(double width) {
        if (width <= 0) {
            return;
        }
        widthAssigned = true;
        cardWidth.set(width);
        // 首次下发宽度时取图；之后窗口缩放只拉伸，不再解码
        loadCoverIfNeeded();
    }

    private void refreshTitle(AsmrWork work) {
        pendingTitleTranslation = null;
        displayTitle = work.getTitleCN() != null && !work.getTitleCN().isBlank() ? work.getTitleCN() : work.getTitle();
        applyTitleClamp();
        titleTranslateButton.setGraphic(AiTitleTranslationAction.createIdleIcon());
        titleTranslateButton.setTooltip(new Tooltip("AI 翻译作品名称"));
    }

    /**
     * 按设置决定标题是否截断：开启限制时保留 {@link #TITLE_MAX_LINES} 行并以省略号收尾，
     * 关闭限制时完整显示。
     */
    private void applyTitleClamp() {
        String title = displayTitle == null ? "" : displayTitle;
        if (title.isBlank() || !Config.setting.gridItemDetailModelMaxTitle.get()) {
            titleText.setText(title);
            updateTitleTooltip(false);
            return;
        }
        double wrapWidth = Math.max(60, contentWidth() - TITLE_RESERVED_WIDTH);
        if (fitsLines(title, wrapWidth, TITLE_MAX_LINES)) {
            titleText.setText(title);
            updateTitleTooltip(false);
            return;
        }
        titleText.setText(clampText(title, wrapWidth));
        updateTitleTooltip(true);
    }

    /**
     * 内容区可用宽度：卡片宽度减去左右内边距。
     */
    private double contentWidth() {
        return cardWidth.get() - CONTENT_PADDING * 2;
    }

    /**
     * 二分查找最长可放入指定行数的标题前缀，并以省略号收尾。
     */
    private String clampText(String title, double wrapWidth) {
        int low = 0;
        int high = title.codePointCount(0, title.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            String candidate = title.substring(0, title.offsetByCodePoints(0, middle)).stripTrailing() + "…";
            if (fitsLines(candidate, wrapWidth, TITLE_MAX_LINES)) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return title.substring(0, title.offsetByCodePoints(0, low)).stripTrailing() + "…";
    }

    /**
     * 判断文本在给定宽度下能否放入指定行数。
     * <p>
     * 测量字体取自渲染后的标题节点，与界面实际使用字体保持一致。
     */
    private boolean fitsLines(String title, double wrapWidth, int maxLines) {
        Font font = titleText.getFont();
        Text measureText = new Text(title);
        measureText.setFont(font);
        measureText.setWrappingWidth(wrapWidth);
        Text singleLineText = new Text("测");
        singleLineText.setFont(font);
        return measureText.getLayoutBounds().getHeight() <= singleLineText.getLayoutBounds().getHeight() * maxLines;
    }

    /**
     * 标题被截断时用悬浮提示补全完整名称。
     */
    private void updateTitleTooltip(boolean clamped) {
        if (clamped) {
            titleTooltip.setText(displayTitle);
            if (!titleTooltipInstalled) {
                Tooltip.install(itemTitle, titleTooltip);
                titleTooltipInstalled = true;
            }
        } else if (titleTooltipInstalled) {
            Tooltip.uninstall(itemTitle, titleTooltip);
            titleTooltipInstalled = false;
        }
    }

}
