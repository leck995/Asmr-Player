package cn.tealc.asmr.ui.work.cells;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.asmr.factory.ViewFactory;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.FileOperationService;
import cn.tealc.asmr.ui.component.OverlayLayer;
import cn.tealc.asmr.ui.tools.DuplicateWorkSelection;
import cn.tealc.asmr.ui.work.WorkDetailView;
import cn.tealc.asmr.ui.work.WorkDetailViewModel;
import cn.tealc.asmr.utils.AnchorPaneUtils;
import cn.tealc.asmr.utils.CoverUtils;
import cn.tealc.asmr.utils.CoverViewport;
import cn.tealc.asmr.utils.FXResourcesLoader;
import cn.tealc.asmr.mvvm.ViewTuple;
import javafx.geometry.Pos;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.beans.property.BooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.control.Tooltip;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.javafx.FontIcon;
import java.util.function.Consumer;


/**
 * 工具箱"重复作品检测"结果中的作品单元格，以固定小尺寸缩略图展示单个候选作品。
 * <p>
 * 供 {@code ToolView} 的重复列表使用：叠加复选框并配合 {@link DuplicateWorkSelection} 做多选，
 * 选中状态以伪类 {@code selected} 呈现。与网格卡片不同，它不参与自适应宽度布局，右键菜单为精简版，
 * 多数编辑类操作不支持。
 *
 * @author Leck
 */
public class DuplicateWorkCell extends AnchorPane implements WorkCellActions {
    private static final int COVER_WIDTH = 105;
    private static final int COVER_HEIGHT = 79;
    private static final Image DEFAULT_IMAGE = new Image(FXResourcesLoader.image("cover-thumb.jpg"), COVER_WIDTH, COVER_HEIGHT, true, true);
    private final FileOperationService fileOperationService = DI.get(FileOperationService.class);
    private final DuplicateWorkSelection selection;
    private final AsmrWork work;
    private final Consumer<AsmrWork> removeAction;
    private final ChangeListener<Boolean> selectionListener;
    private final WeakChangeListener<Boolean> weakSelectionListener;
    private final BooleanProperty selectedState;

    /**
     * 构建单元格：绘制缩略封面、作品编号、字幕/语言标记与选择复选框，并绑定多选与双击打开详情的交互。
     *
     * @param work         候选作品
     * @param selection    跨单元格共享的重复作品选择状态
     * @param removeAction 右键删除时对该作品执行的移除回调
     */
    public DuplicateWorkCell(AsmrWork work, DuplicateWorkSelection selection, Consumer<AsmrWork> removeAction) {
        this.work = work;
        this.selection = selection;
        this.removeAction = removeAction;
        setMinSize(COVER_WIDTH, COVER_HEIGHT);
        setPrefSize(COVER_WIDTH, COVER_HEIGHT);
        setMaxSize(COVER_WIDTH, COVER_HEIGHT);
        setFocusTraversable(true);
        ImageView itemAlbum = new ImageView();
        itemAlbum.setAccessibleText(work.getFilepath());
        CoverViewport.configure(itemAlbum);
        itemAlbum.setFitWidth(COVER_WIDTH);
        itemAlbum.setFitHeight(COVER_HEIGHT);
        itemAlbum.setImage(getThumbCoverImage(work));
        Rectangle rectangle=new Rectangle(COVER_WIDTH, COVER_HEIGHT);
        rectangle.setArcHeight(15);
        rectangle.setArcWidth(15);
        itemAlbum.setClip(rectangle);

        Label workIdLabel = new Label();
        if ( work.getWorkId() != null){
            workIdLabel.setText(work.getWorkId());
        }else {
            workIdLabel.setVisible(false);
        }
        workIdLabel.getStyleClass().add("cell-work-id");
        FontIcon sourceIcon = new FontIcon();
        sourceIcon.getStyleClass().add(work.getStorageType() == 1 ? "web" : "local");
        workIdLabel.setGraphic(sourceIcon);
        workIdLabel.setGraphicTextGap(4);

        HBox tagPane = new HBox();
        tagPane.setAlignment(Pos.CENTER_LEFT);
        tagPane.getStyleClass().add("tag");
        tagPane.setSpacing(4);

        if (Boolean.TRUE.equals(work.getSubtitle())) {
            Label label = new Label("字幕");
            label.getStyleClass().add("subtitle");
            tagPane.getChildren().add(label);
        }

        if (work.getRelatedWorkIds() != null){
            String[] languages = work.getRelatedWorkIds().split("\\*");
            for (String language : languages) {
                String[] parts = language.split("#", 2);
                if (parts.length == 2 && parts[0].equalsIgnoreCase(work.getWorkId())){
                    Label label = new Label(parts[1]);
                    label.getStyleClass().add("language");
                    tagPane.getChildren().add(label);
                }
            }
        }

        getChildren().addAll(itemAlbum,workIdLabel,tagPane);
        AnchorPaneUtils.setPosition(itemAlbum, 0.0, 0.0, 0.0, 0.0);
        AnchorPaneUtils.setPosition(workIdLabel, 6.0, null, null, 6.0);
        AnchorPaneUtils.setPosition(tagPane, null, null, 4.0, 4.0);
        getStyleClass().add("work-cell");
        getStyleClass().add("duplicate-work-cell");
        Tooltip.install(this, new Tooltip(String.valueOf(work.getTitle()) + "\n" + work.getFilepath()));

        CheckBox selected = new CheckBox();
        selected.setAccessibleText("选择作品 " + work.getWorkId());
        selected.getStyleClass().add("duplicate-work-check");
        selectionListener = (observable, oldValue, newValue) -> {
            selected.setSelected(newValue);
            pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), newValue);
        };
        selectedState = selection.selectedProperty(work);
        weakSelectionListener = new WeakChangeListener<>(selectionListener);
        selectedState.addListener(weakSelectionListener);
        selectionListener.changed(selectedState, false, selectedState.get());
        selected.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                selection.select(work, true, event.isShiftDown());
                requestFocus();
                event.consume();
            }
        });
        selected.addEventFilter(MouseEvent.MOUSE_RELEASED, MouseEvent::consume);
        selected.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        selected.setOnAction(event -> selection.select(work, true, false));
        getChildren().add(selected);
        AnchorPaneUtils.setPosition(selected, null, 6.0, 6.0, null);
        Region selectionBorder = new Region();
        selectionBorder.getStyleClass().add("duplicate-selection-border");
        selectionBorder.setMouseTransparent(true);
        getChildren().add(selectionBorder);
        AnchorPaneUtils.setPosition(selectionBorder, 0.0, 0.0, 0.0, 0.0);

        ContextMenu contextMenu = WorkContextMenuFactory.createLite(work, this);
        setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton().equals(MouseButton.SECONDARY)) {
                if (!selection.selectedProperty(work).get()) selection.select(work, false, false);
                contextMenu.show(this.getScene().getWindow(), mouseEvent.getScreenX(), mouseEvent.getScreenY());
                mouseEvent.consume();
            }
            if (mouseEvent.getButton().equals(MouseButton.PRIMARY)) {
                requestFocus();
                if (mouseEvent.getClickCount() == 1) {
                    selection.select(work, mouseEvent.isShortcutDown(), mouseEvent.isShiftDown());
                    mouseEvent.consume();
                    return;
                }
                if (mouseEvent.isShortcutDown() || mouseEvent.isShiftDown()) {
                    mouseEvent.consume();
                    return;
                }
                if (!fileOperationService.exists(work)){
                    EventBus.publish(NotificationKey.MESSAGE,new MessageInfo(MessageType.ERROR,"该音声不存在，请检查路径或更新"));
                    return;
                }
                ViewTuple<WorkDetailView, WorkDetailViewModel> viewTuple = ViewFactory.workDetailView(work);
                OverlayLayer.show((Pane) viewTuple.getView());
                mouseEvent.consume();
            }
        });
    }

    /**
     * 解除对选中状态属性的弱监听，供单元格回收时调用以避免泄漏。
     */
    public void dispose() {
        selectedState.removeListener(weakSelectionListener);
    }

    // ===================== WorkCellActions 实现 =====================

    @Override
    public void onSync(AsmrWork work) {
        // 精简版不支持
    }

    @Override
    public void onEditInfo(AsmrWork work) {
        // 精简版不支持
    }

    @Override
    public void onEditTags(AsmrWork work) {
        // 精简版不支持
    }

    @Override
    public void onDelete(AsmrWork work) {
        removeAction.accept(work);
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
        // 精简版不支持
    }

    @Override
    public void onToggleFavorite(AsmrWork work) {
        // 精简版不支持
    }

    @Override
    public void onMove(AsmrWork work) {
        // 精简版不支持
    }

    @Override
    public void onSubtitleRename(AsmrWork work) {
        // 精简版不支持
    }

    @Override
    public void onOpenFolder(AsmrWork work) {
        if (!fileOperationService.openInSystem(work, work.getFilepath())) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.WARNING, "当前来源不支持在系统文件管理器中打开"));
        }
    }
    /**
     * 获取封面图（缩略版，与卡片尺寸一致）。
     */
    public Image getThumbCoverImage(AsmrWork work) {
        return CoverUtils.getCoverForWork(work, "thumb", "cover-thumb", COVER_WIDTH, COVER_HEIGHT, true, DEFAULT_IMAGE);
    }

}
