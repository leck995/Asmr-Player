package cn.tealc.asmr.ui.work.cells;

import cn.tealc.asmr.eventbus.EventBus;

import atlantafx.base.theme.Styles;
import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.factory.ViewFactory;
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
import cn.tealc.asmr.ui.component.OverlayLayer;
import cn.tealc.asmr.ui.work.*;
import cn.tealc.asmr.ui.tools.MoveAsmrPage;
import cn.tealc.asmr.ui.tools.AiTitleTranslationAction;
import cn.tealc.asmr.ui.tools.SubtitleRenameView;
import cn.tealc.asmr.ui.tools.SubtitleRenameViewModel;
import cn.tealc.asmr.ui.work.list.AdaptiveWidthCell;
import cn.tealc.asmr.ui.work.list.DefaultWorkCellContext;
import cn.tealc.asmr.ui.work.list.WorkCellContext;
import cn.tealc.asmr.ui.dialog.Alerts;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.utils.*;
import cn.tealc.asmr.mvvm.ViewTuple;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.controlsfx.control.Rating;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;


import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 小图模式作品卡片，在网格中以紧凑布局展示单个作品的缩略封面、编号、标题与标签。
 * <p>
 * 与 {@link BigWorkCell} 的区别：默认宽度更窄、封面取缩略图，分类与自定义标签合并进单个可换行的
 * {@link FlowPane}，评分以文本按钮呈现；此外支持 {@code removeFromAlbum} 回调，用于专辑内嵌场景。
 * 宽度由宿主列表通过 {@link AdaptiveWidthCell#setCardWidth(double)} 动态分配，变化时同步重算
 * 封面裁剪、标题换行宽度与截断长度。
 *
 * @author Leck
 */
public class SmallWorkCell extends VBox implements WorkCellActions, AdaptiveWidthCell {
    /** 卡片默认宽度，列表未分配实际宽度时使用 */
    private static final double DEFAULT_CARD_WIDTH = 240;
    /** 数据区（标题、标签栏）左右内边距，比封面稍收进以形成留白 */
    private static final double CONTENT_PADDING = 8;
    /** 行尾为 AI 翻译按钮预留的宽度（按钮 20px + 左内边距 5px） */
    private static final double TITLE_RESERVED_WIDTH = 25;
    private static final int TITLE_MAX_LINES = 3;
    /**
     * 封面解码宽度上限。
     * <p>
     * 缩略图源本身尺寸就小，宽卡片再往上请求只会放大解码缓冲；超出部分交给
     * {@link ImageView} 拉伸即可。
     */
    private static final int COVER_MAX_WIDTH = 480;
    private static final Image DEFAULT_IMAGE = new Image(FXResourcesLoader.image("cover-thumb.jpg"), 240, 180, true, true);
    private final WorkService workService = DI.get(WorkService.class);
    private final MetadataSyncService metadataSyncService = DI.get(MetadataSyncService.class);
    private final PlaylistService playlistService = DI.get(PlaylistService.class);
    private final FileOperationService fileOperationService = DI.get(FileOperationService.class);

    /**
     * 卡片实际宽度。
     * <p>
     * 由列表按容器宽度分配，默认取 {@link #DEFAULT_CARD_WIDTH}；
     * 宽度变化时同步封面、标题与裁剪区域，使卡片能铺满整行。
     */
    private final DoubleProperty cardWidth = new SimpleDoubleProperty(DEFAULT_CARD_WIDTH);
    /** 列表是否已下发真实宽度；构造阶段的默认宽度不参与取图。 */
    private boolean widthAssigned;
    /** 本页是否已按当前宽度取过封面；窗口缩放不再重新解码。 */
    private boolean coverLoaded;

    private final WorkCellContext context;
    private final Consumer<AsmrWork> removeFromAlbum;
    private AsmrWork work;
    private final ImageView itemAlbum = new ImageView();
    private final Label workIdLabel = new Label();
    private final Label dateLabel = new Label();
    private final ToggleButton starBtn = new ToggleButton();
    private final TextFlow itemTitle = new TextFlow();
    private final Text titleText = new Text();
    private final Button titleTranslateButton = new Button();
    private TranslationPreviewItem pendingTitleTranslation;
    private final FlowPane tagPane = new FlowPane();
    private final Button ratingButton = new Button();
    private final SVGPath coverClip = new SVGPath();
    /** 当前展示的完整标题，宽度变化时据此重新截断 */
    private String displayTitle = "";

    /**
     * 以默认上下文创建卡片，删除动作走 {@link WorkCellContext}（移除数据库记录）。
     *
     * @param work 要在卡片中展示的作品
     */
    public SmallWorkCell(AsmrWork work) {
        this(work, DefaultWorkCellContext.INSTANCE, null);
    }

    /**
     * 指定列表上下文创建卡片；上下文决定标签/分类点击跳转、收藏变更与删除如何回传到宿主列表。
     *
     * @param work    要展示的作品
     * @param context 卡片与宿主列表协作的上下文，为 {@code null} 时回退到 {@link DefaultWorkCellContext}
     */
    public SmallWorkCell(AsmrWork work, WorkCellContext context) {
        this(work, context, null);
    }

    /**
     * 专辑内嵌场景：传入移出回调后，右键"删除"变为"移出本集"，仅把作品从当前专辑移除而不删数据库记录。
     *
     * @param work            要展示的作品
     * @param removeFromAlbum 将作品移出专辑的回调，为 {@code null} 时执行常规删除
     */
    public SmallWorkCell(AsmrWork work, Consumer<AsmrWork> removeFromAlbum) {
        this(work, DefaultWorkCellContext.INSTANCE, removeFromAlbum);
    }

    /**
     * 实际完成卡片构建的重载：绑定自适应宽度、装配封面/标题/标签/评分各区域并注册交互事件，
     * 末尾调用 {@link #refreshData(AsmrWork)} 装载数据；上面所有公开构造函数都委托到这里。
     *
     * @param work            要展示的作品，为 {@code null} 时直接返回、不构建任何子节点
     * @param context         与宿主列表协作的上下文，为 {@code null} 时回退到 {@link DefaultWorkCellContext}
     * @param removeFromAlbum 专辑内嵌时的移出回调，{@code null} 表示常规删除
     */
    private SmallWorkCell(AsmrWork work, WorkCellContext context, Consumer<AsmrWork> removeFromAlbum) {
        this.context = context == null ? DefaultWorkCellContext.INSTANCE : context;
        this.removeFromAlbum = removeFromAlbum;
        if (work == null) return;
        this.work = work;

        // ---- 封面 ----
        itemAlbum.setAccessibleText(work.getFilepath());
        itemAlbum.fitWidthProperty().bind(cardWidth);
        itemAlbum.fitHeightProperty().bind(cardWidth.multiply(0.75));
        itemAlbum.setSmooth(true);
        CoverViewport.configure(itemAlbum);
        updateCoverClip(coverClip);
        itemAlbum.setClip(coverClip);

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

        // ---- 头部 ----
        StackPane headPane = new StackPane(itemAlbum, workIdLabel, dateLabel, playBtn, starBtn, ratingButton);
        headPane.getStyleClass().add("head");
        headPane.setMinWidth(Region.USE_PREF_SIZE);
        headPane.prefWidthProperty().bind(cardWidth);
        headPane.maxWidthProperty().bind(cardWidth);
        StackPane.setAlignment(workIdLabel, Pos.TOP_LEFT);
        StackPane.setAlignment(dateLabel, Pos.BOTTOM_RIGHT);
        StackPane.setAlignment(playBtn, Pos.CENTER);
        StackPane.setAlignment(starBtn, Pos.TOP_RIGHT);
        StackPane.setAlignment(ratingButton, Pos.BOTTOM_LEFT);

        // ---- 标题 ----
        itemTitle.setId("grid-item-title");
        itemTitle.getStyleClass().add("cell-title");
        titleText.setFont(Font.font(15));
        // 换行宽度扣除行尾 AI 按钮占位，与 fitsTitle 的测量宽度保持一致
        titleText.setWrappingWidth(Math.max(60, titleContentWidth() - TITLE_RESERVED_WIDTH));
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
                setDisplayTitle(item.translatedText());
                titleTranslateButton.setGraphic(AiTitleTranslationAction.createReadyIcon());
                titleTranslateButton.setTooltip(new Tooltip("应用中文标题"));
            });
        });
        titleTranslateButton.setOnMouseClicked(mouseEvent -> mouseEvent.consume());
        itemTitle.getChildren().addAll(titleText, titleTranslateButton);
        itemTitle.prefWidthProperty().bind(cardWidth.subtract(CONTENT_PADDING * 2));
        itemTitle.minWidthProperty().bind(cardWidth.subtract(CONTENT_PADDING * 2));
        itemTitle.maxWidthProperty().bind(cardWidth.subtract(CONTENT_PADDING * 2));

        // ---- 标签栏 ----
        tagPane.setAlignment(Pos.TOP_LEFT);
        tagPane.setId("list-item-tag-pane");
        tagPane.prefWidthProperty().bind(cardWidth.subtract(CONTENT_PADDING * 2));
        tagPane.maxWidthProperty().bind(cardWidth.subtract(CONTENT_PADDING * 2));
        tagPane.prefWrapLengthProperty().bind(cardWidth.subtract(CONTENT_PADDING * 2));
        tagPane.setHgap(5);
        tagPane.setVgap(5);
        tagPane.setPadding(Insets.EMPTY);

        // ---- 评分 ----
        ratingButton.getStyleClass().add("rating-text-button");
        ratingButton.setTooltip(new Tooltip("设置作品评分"));
        ratingButton.setOnAction(actionEvent -> showRatingDialog());
        ratingButton.setOnMouseClicked(mouseEvent -> mouseEvent.consume());

        // ---- 布局 ----
        setMinWidth(Region.USE_PREF_SIZE);
        prefWidthProperty().bind(cardWidth);
        maxWidthProperty().bind(cardWidth);
        getChildren().addAll(headPane, itemTitle, tagPane);
        // 封面需要贴卡片顶边与左右边，因此卡片本身不留内边距；
        // 数据区各自的边距在下方按 CONTENT_PADDING 单独设置
        setPadding(Insets.EMPTY);
        VBox.setMargin(itemTitle, new Insets(2, CONTENT_PADDING, 0, CONTENT_PADDING));
        VBox.setMargin(tagPane, new Insets(0, CONTENT_PADDING, 8, CONTENT_PADDING));
        setSpacing(6);
        setAlignment(Pos.TOP_CENTER);
        getStyleClass().addAll("work-cell", "small-work-cell");

        // 宽度变化会同时影响封面裁剪、文字换行宽度与标题可容纳字数；
        // 监听器放在布局完成后注册，避免构造中途误清标题
        cardWidth.addListener((observable, oldValue, newValue) -> {
            updateCoverClip(coverClip);
            titleText.setWrappingWidth(Math.max(60, titleContentWidth() - TITLE_RESERVED_WIDTH));
            setDisplayTitle(displayTitle);
        });

        // ---- 右键 / 点击 ----
        // 菜单按当前作品延迟构建：卡片刷新为新作品后，菜单项也指向新作品
        String removeText = removeFromAlbum == null ? "移除" : "移出本集";
        setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton().equals(MouseButton.SECONDARY)) {
                ContextMenu contextMenu = WorkContextMenuFactory.create(this.work, this, removeText);
                contextMenu.show(this.getScene().getWindow(), mouseEvent.getScreenX(), mouseEvent.getScreenY());
            }
            if (mouseEvent.getButton().equals(MouseButton.PRIMARY)) {
                AsmrWork current = this.work;
                if (current.getStorageType() == 0) {
                    if (!fileOperationService.exists(current)) {
                        EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(cn.tealc.asmr.model.message.MessageType.ERROR, "该音声不存在，请检查路径或更新"));
                        return;
                    }
                }
                ViewTuple<WorkDetailView, WorkDetailViewModel> viewTuple = ViewFactory.workDetailView(current);
                OverlayLayer.show((Pane) viewTuple.getView());
                mouseEvent.consume();
            }
        });

        // ---- 装载数据 ----
        refreshData(work);
    }

    /**
     * 重新装载所有数据：封面、作品编号、收藏、标题、标签、评分
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
        FontIcon storageTypeIcon = new FontIcon();
        workIdLabel.setGraphic(storageTypeIcon);
        storageTypeIcon.getStyleClass().add(work.getStorageType() == 1 ? "web" : "local");
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
            nsfwLabel.setId("grid-item-nsfw");
            nsfwLabel.getStyleClass().add("nsfw");
            tagPane.getChildren().add(nsfwLabel);
        }
        fillCustomTags(work.getTags());
        fillFlowPane(work.getCategories(), "category");
        refreshRatingText();
    }

    /**
     * 添加自定义标签。
     */
    private void fillCustomTags(String csv) {
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
            tagPane.getChildren().add(label);
        }
    }

    /**
     * 添加分类元数据标签。
     */
    private void fillFlowPane(String csv, String styleClass) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String item : csv.split(",")) {
            if (item.isBlank()) {
                continue;
            }
            Label label = new Label(item);
            label.getStyleClass().add(styleClass);
            label.setOnMouseClicked(mouseEvent -> {
                context.navigate(AsmrKeyType.CATEGORY, item);
                mouseEvent.consume();
            });
            tagPane.getChildren().add(label);
        }
    }

    /**
     * 显示作品评分弹窗。
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
     * 刷新评分按钮文本。
     */
    private void refreshRatingText() {
        Double rate = work.getRate();
        ratingButton.setText(rate == null || rate <= 0 ? "评分" : String.format(Locale.ROOT, "评分 %.1f", rate));
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
        if (removeFromAlbum != null) {
            removeFromAlbum.accept(work);
            return;
        }
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
     * 按卡片宽度加载缩略封面，每个作品只解码一次。
     * <p>
     * 宽度来自列表首次下发的真实值（构造阶段的默认宽度不算），窗口缩放只拉伸现有图片，
     * 翻页或重建卡片时才按新宽度重新取图。
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
                    work, "thumb", "cover-thumb",
                    requestWidth, requestHeight, false, DEFAULT_IMAGE);
            Platform.runLater(() -> {
                if (this.work != work) {
                    return;
                }
                itemAlbum.setImage(image);
            });
        });
    }

    /**
     * 更新封面裁剪区域，仅保留顶部圆角。
     * <p>
     * 圆角与大图卡片保持一致，避免卡片圆角小于封面圆角时露出直角。
     */
    private void updateCoverClip(SVGPath coverClip) {
        double width = cardWidth.get();
        double height = width * 0.75;
        double radius = 12;
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
        // 监听器会同步封面裁剪、文字换行宽度与标题截断
        cardWidth.set(width);
        // 首次下发宽度时取图；之后窗口缩放只拉伸，不再解码
        loadCoverIfNeeded();
    }

    /**
     * 标题可用宽度：卡片宽度减去左右内边距。
     */
    private double titleContentWidth() {
        return Math.max(60, cardWidth.get() - CONTENT_PADDING * 2);
    }

    private void refreshTitle(AsmrWork work) {
        pendingTitleTranslation = null;
        setDisplayTitle(work.getTitleCN() != null && !work.getTitleCN().isBlank() ? work.getTitleCN() : work.getTitle());
        titleTranslateButton.setGraphic(AiTitleTranslationAction.createIdleIcon());
        titleTranslateButton.setTooltip(new Tooltip("AI 翻译作品名称"));
    }

    /**
     * 将标题限制为三行，并为翻译按钮保留尾部空间。
     */
    private void setDisplayTitle(String title) {
        displayTitle = title == null ? "" : title;
        if (title == null || title.isBlank() || fitsTitle(title)) {
            titleText.setText(displayTitle);
            return;
        }

        int low = 0;
        int high = title.codePointCount(0, title.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            int endIndex = title.offsetByCodePoints(0, middle);
            String candidate = title.substring(0, endIndex).stripTrailing() + "...";
            if (fitsTitle(candidate)) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }

        int endIndex = title.offsetByCodePoints(0, low);
        titleText.setText(title.substring(0, endIndex).stripTrailing() + "...");
    }

    /**
     * 检查标题文本是否可在三行内显示。
     * <p>
     * 测量宽度扣除行尾 AI 翻译按钮的占位，避免文字被按钮挤下去后行数估算偏少。
     */
    private boolean fitsTitle(String title) {
        Text measureText = new Text(title);
        measureText.setFont(titleText.getFont());
        measureText.setWrappingWidth(Math.max(60, titleContentWidth() - TITLE_RESERVED_WIDTH));
        Text singleLineText = new Text("测");
        singleLineText.setFont(titleText.getFont());
        return measureText.getLayoutBounds().getHeight() <= singleLineText.getLayoutBounds().getHeight() * TITLE_MAX_LINES;
    }
}
