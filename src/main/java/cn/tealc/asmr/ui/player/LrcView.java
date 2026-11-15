package cn.tealc.asmr.ui.player;

import cn.tealc.asmr.model.SubtitleCue;
import cn.tealc.asmr.player.VlcPlayer;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.subtitle.SubtitleDisplayService;
import cn.tealc.asmr.subtitle.SubtitleTiming;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * 歌词视图，显示 {@link SubtitleCue} 并跟随播放进度高亮滚动。
 * <p>
 * 基于 ScrollPane + VBox 实现，不继承 ListView。支持：
 * <ul>
 *   <li>跟随播放进度高亮选中行并居中滚动（带动画）</li>
 *   <li>双击歌词行跳转到对应时间</li>
 *   <li>手动滚动后 3 秒自动返回选中位置（带动画）</li>
 *   <li>显示/隐藏翻译</li>
 *   <li>居中/居左对齐</li>
 * </ul>
 *
 * @author Leck
 */
public class LrcView extends StackPane {

    private final IntegerProperty selectedIndex;
    private final Consumer<Double> onSeek;

    private final SimpleBooleanProperty showTrans = new SimpleBooleanProperty(true);

    private final ScrollPane scrollPane;
    private final VBox container;

    private ObservableList<SubtitleCue> items;

    /** 程序控制滚动时为 true，避免误触手动滚动检测 */
    private boolean isAutoScrolling = false;
    /** 用户手动滚动后等待自动返回的定时器 */
    private final PauseTransition autoReturnTimer = new PauseTransition(Duration.seconds(3));
    /** 当前滚动动画，用于取消上一次未完成的动画 */
    private Timeline scrollAnimation;

    /** 单次创建的占位 Text，列表为空时显示 */
    private final Text placeholder;
    /** 占位容器，叠在 ScrollPane 上方居中显示 */
    private final StackPane placeholderPane;

    /** 选中索引监听器 */
    private final ChangeListener<Number> indexListener = (obs, oldIdx, newIdx) -> {
        if (newIdx == null) return;
        updateActiveRow(oldIdx != null ? oldIdx.intValue() : -1, newIdx.intValue());
        scrollToCenteredAnimated(newIdx.intValue());
    };

    /** 列表内容变化监听器 */
    private final ListChangeListener<SubtitleCue> itemsListener = change -> rebuildRows();
    /** 字幕显示模式变化监听器 */
    private final ChangeListener<String> displayModeListener = (observable, oldValue, newValue) -> rebuildRows();
    /** 弱引用模式监听器，避免全局设置长期持有歌词视图 */
    private final WeakChangeListener<String> weakDisplayModeListener = new WeakChangeListener<>(displayModeListener);

    public LrcView(IntegerProperty selectedIndex, Consumer<Double> onSeek) {
        this.selectedIndex = selectedIndex;
        this.onSeek = onSeek;

        getStyleClass().add("lrc-view");

        placeholder = new Text("暂无歌词");
        placeholder.setFont(Font.font(24));
        placeholder.getStyleClass().add("lrc-placeholder");
        placeholderPane = new StackPane(placeholder);
        placeholderPane.setAlignment(Pos.CENTER);
        placeholderPane.setMouseTransparent(true);
        placeholderPane.getStyleClass().add("lrc-placeholder-pane");

        container = new VBox();
        container.getStyleClass().add("lrc-container");
        container.setAlignment(Pos.TOP_CENTER);

        scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("lrc-scroll-pane");

        getChildren().addAll(scrollPane, placeholderPane);

        // 监听选中索引变化
        selectedIndex.addListener(indexListener);

        // 用户手动滚动 → 3 秒后自动返回
        scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (!isAutoScrolling) {
                autoReturnTimer.playFromStart();
            }
        });
        autoReturnTimer.setOnFinished(e -> {
            int idx = selectedIndex.get();
            if (idx >= 0) {
                scrollToCenteredAnimated(idx);
            }
        });

        // 初始显示占位
        rebuildRows();
        SubtitleDisplayService.modeProperty().addListener(weakDisplayModeListener);
    }

    /**
     * 设置歌词数据列表，自动重建行并绑定监听。
     */
    public void setItems(ObservableList<SubtitleCue> items) {
        if (this.items != null) {
            this.items.removeListener(itemsListener);
        }
        this.items = items;
        if (items != null) {
            items.addListener(itemsListener);
        }
        rebuildRows();
    }

    public ObservableList<SubtitleCue> getItems() {
        return items;
    }

    /**
     * 重建所有歌词行。
     */
    private void rebuildRows() {
        container.getChildren().clear();
        if (items == null || items.isEmpty()) {
            placeholderPane.setVisible(true);
            placeholderPane.setManaged(true);
            return;
        }
        placeholderPane.setVisible(false);
        placeholderPane.setManaged(false);

        for (int i = 0; i < items.size(); i++) {
            SubtitleCue cue = items.get(i);
            LrcRow row = new LrcRow(i, cue, showTrans);
            container.getChildren().add(row);
        }

        // 如果已有选中索引，立即高亮
        int idx = selectedIndex.get();
        if (idx >= 0 && idx < items.size()) {
            updateActiveRow(-1, idx);
            scrollToCenteredAnimated(idx);
        }
    }

    /**
     * 切换高亮行：取消旧行高亮，设置新行高亮。
     */
    private void updateActiveRow(int oldIdx, int newIdx) {
        if (oldIdx >= 0 && oldIdx < container.getChildren().size()) {
            ((LrcRow) container.getChildren().get(oldIdx)).setActive(false);
        }
        if (newIdx >= 0 && newIdx < container.getChildren().size()) {
            ((LrcRow) container.getChildren().get(newIdx)).setActive(true);
        }
    }

    /**
     * 将指定行滚动到视口居中位置（带动画）。
     */
    private void scrollToCenteredAnimated(int index) {
        if (items == null || items.isEmpty() || index < 0 || index >= items.size()) return;

        // 取消上一次未完成的动画和手动滚动计时
        if (scrollAnimation != null && scrollAnimation.getStatus() == Animation.Status.RUNNING) {
            scrollAnimation.stop();
        }
        autoReturnTimer.stop();

        isAutoScrolling = true;

        // 延迟到下一帧计算坐标，确保布局已更新
        javafx.application.Platform.runLater(() -> {
            double contentHeight = container.getHeight();
            double viewportHeight = scrollPane.getViewportBounds() != null
                    ? scrollPane.getViewportBounds().getHeight() : getHeight();
            if (contentHeight <= 0 || viewportHeight <= 0) {
                isAutoScrolling = false;
                return;
            }

            LrcRow row = (LrcRow) container.getChildren().get(index);
            double rowY = row.getLayoutY();
            double rowHeight = row.getHeight();
            double targetY = rowY + rowHeight / 2 - viewportHeight / 2;
            double maxScroll = contentHeight - viewportHeight;
            if (maxScroll <= 0) {
                isAutoScrolling = false;
                return;
            }
            double targetV = Math.max(0, Math.min(1, targetY / maxScroll));

            double currentV = scrollPane.getVvalue();

            scrollAnimation = new Timeline(
                    new KeyFrame(Duration.millis(350),
                            new KeyValue(scrollPane.vvalueProperty(), targetV, Interpolator.EASE_BOTH))
            );
            scrollAnimation.setOnFinished(e -> isAutoScrolling = false);
            scrollAnimation.play();
        });
    }

    public boolean isShowTrans() {
        return showTrans.get();
    }

    public SimpleBooleanProperty showTransProperty() {
        return showTrans;
    }

    public void setShowTrans(boolean showTrans) {
        this.showTrans.set(showTrans);
    }

    /**
     * 单行歌词，含原文 + 翻译，支持双击跳转。
     */
    private class LrcRow extends VBox {
        private final int index;
        private final Text textNode;
        private final Text tranNode;

        LrcRow(int index, SubtitleCue cue, BooleanProperty showTransProp) {
            this.index = index;
            getStyleClass().add("lrc-row");
            setAlignment(Pos.CENTER);
            setSpacing(4);
            setPadding(new Insets(12, 0, 12, 0));
            setFillWidth(true);

            // 对齐方式绑定配置（true=居中 false=居左）
            alignmentProperty().bind(Bindings.createObjectBinding(
                    () -> Config.setting.detailLrcAlignment.get() ? Pos.CENTER : Pos.CENTER_LEFT,
                    Config.setting.detailLrcAlignment));

            textNode = new Text(SubtitleDisplayService.convert(cue.getText()));
            textNode.getStyleClass().add("lrc-text");
            textNode.setTextAlignment(TextAlignment.CENTER);
            textNode.wrappingWidthProperty().bind(widthProperty().multiply(0.85));
            textNode.setFontSmoothingType(javafx.scene.text.FontSmoothingType.GRAY);

            tranNode = new Text();
            tranNode.getStyleClass().add("lrc-tran");
            tranNode.setTextAlignment(TextAlignment.CENTER);
            tranNode.wrappingWidthProperty().bind(widthProperty().multiply(0.85));
            tranNode.setFontSmoothingType(javafx.scene.text.FontSmoothingType.GRAY);

            String translation = SubtitleDisplayService.convert(cue.getTranslation());
            tranNode.setText(translation != null ? translation : "");
            tranNode.managedProperty().bind(
                    showTransProp.and(Bindings.createBooleanBinding(
                            () -> cue.getTranslation() != null)));
            tranNode.visibleProperty().bind(tranNode.managedProperty());

            getChildren().addAll(textNode, tranNode);

            setCursor(javafx.scene.Cursor.HAND);

            // 点击跳转，并恢复播放
            setOnMouseClicked(event -> {
                if (event.getClickCount() == 1 && onSeek != null && items != null
                        && index >= 0 && index < items.size()) {
                    long targetMs = SubtitleTiming.seekTargetMillis(
                            items.get(index).getStartTime(), Config.setting.subtitleDelayMs.get());
                    onSeek.accept(targetMs / 1000.0);
                    // 跳转后恢复播放（暂停状态点击歌词自动继续播放）
                    VlcPlayer.getInstance().play();
                }
            });
        }

        void setActive(boolean active) {
            if (active) {
                if (!getStyleClass().contains("lrc-row-active")) {
                    getStyleClass().add("lrc-row-active");
                }
            } else {
                getStyleClass().remove("lrc-row-active");
            }
        }
    }
}
