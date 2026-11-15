package cn.tealc.asmr.factory;


import cn.tealc.asmr.player.VlcPlayer;
import cn.tealc.asmr.utils.CssLoader;
import com.jfoenix.controls.JFXSlider;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * 通用 JavaFX 节点工厂，集中构建跨页面复用的控件。
 *
 * @author Leck
 */
public class NodeFactory {

    /** 音量弹窗宽度，与 ContextMenu 宽度保持一致。 */
    private static final double VOLUME_POPUP_WIDTH = 56;
    /** 音量弹窗高度。 */
    private static final double VOLUME_POPUP_HEIGHT = 160;
    /** 弹窗底边与触发按钮之间的间距。 */
    private static final double VOLUME_POPUP_GAP = 10;

    /**
     * 创建音量调节弹窗：垂直滑条与播放器音量属性双向绑定，静音开关与静音属性双向绑定。
     *
     * @return 包含静音按钮、分隔线、音量滑条与百分比标签的弹出面板
     */
    public static Parent getVolumePopup(){
        return createVolumePopup(VlcPlayer.getInstance().volumeProperty(), VlcPlayer.getInstance().muteProperty());
    }

    /**
     * 构建音量面板，音量与静音状态由调用方提供。
     */
    private static Parent createVolumePopup(DoubleProperty volume, BooleanProperty mute) {
        JFXSlider volumeSlider=new JFXSlider();
        volumeSlider.valueProperty().bindBidirectional(volume);
        volumeSlider.setMin(0);
        volumeSlider.setMax(1);

        volumeSlider.getStyleClass().add("popup-volume-slider");
        volumeSlider.setOrientation(Orientation.VERTICAL);
        VBox.setVgrow(volumeSlider, Priority.ALWAYS);

        ToggleButton muteBtn=new ToggleButton();
        muteBtn.selectedProperty().bindBidirectional(mute);
        muteBtn.getStyleClass().add("popup-volume-mute-btn");
        muteBtn.setGraphic(new Region());
        muteBtn.setTooltip(new Tooltip("静音"));
        muteBtn.setFocusTraversable(false);

        // 用细线代替 Separator：避免默认分隔符自带的上下留白
        Region divider = new Region();
        divider.getStyleClass().add("popup-volume-divider");
        divider.setMinHeight(1);
        divider.setPrefHeight(1);
        divider.setMaxHeight(1);
        divider.setMaxWidth(28);

        Label valueLabel = new Label();
        valueLabel.getStyleClass().add("popup-volume-value");
        valueLabel.setMinWidth(24);
        valueLabel.setPrefWidth(24);
        valueLabel.setAlignment(Pos.CENTER);
        valueLabel.textProperty().bind(volumeSlider.valueProperty().multiply(100).asString("%.0f"));

        VBox volumePane=new VBox(muteBtn,divider,volumeSlider,valueLabel);
        volumePane.setPrefWidth(VOLUME_POPUP_WIDTH);
        volumePane.setPrefHeight(VOLUME_POPUP_HEIGHT);
        volumePane.setSpacing(6);
        volumePane.setPadding(new Insets(10,8,10,8));
        volumePane.setAlignment(Pos.CENTER);
        volumePane.getStylesheets().add(CssLoader.getCss(CssLoader.popupVolume));
        volumePane.getStyleClass().addAll("popup-volume-sound-popup");
        // 固定裁剪到弹窗尺寸：JFoenix 拖动时仍会缩放隐藏在滑块外的数值气泡，
        // 气泡溢出会撑大 ContextMenu 的内容边界，导致弹窗左右跳动
        volumePane.setClip(new Rectangle(VOLUME_POPUP_WIDTH, VOLUME_POPUP_HEIGHT));
        return volumePane;
    }

    /**
     * 创建音量弹窗的 ContextMenu 宿主，统一弹窗宽度并挂载音量面板。
     */
    public static ContextMenu createVolumePopupMenu() {
        ContextMenu popup = new ContextMenu();
        popup.setPrefWidth(VOLUME_POPUP_WIDTH);
        popup.setMaxWidth(VOLUME_POPUP_WIDTH);
        popup.getScene().setRoot(getVolumePopup());
        return popup;
    }

    /**
     * 在触发按钮正上方展示音量弹窗。
     *
     * @param popup  音量弹窗
     * @param anchor 触发按钮
     */
    public static void showVolumePopup(ContextMenu popup, Node anchor) {
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        // 水平居中到按钮/图标正上方
        double x = bounds.getMinX() + (bounds.getWidth() - VOLUME_POPUP_WIDTH) / 2;
        popup.show(anchor.getScene().getWindow(), x,
                bounds.getMinY() - VOLUME_POPUP_HEIGHT - VOLUME_POPUP_GAP);
    }


}
