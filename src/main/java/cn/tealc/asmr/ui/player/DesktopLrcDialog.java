package cn.tealc.asmr.ui.player;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.teafx.stage.handler.DragStageHandler;
import cn.tealc.teafx.stage.handler.DragWindowHandler;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.player.VlcPlayer;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.utils.CssLoader;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Timer;
import java.util.TimerTask;

/**
 * 桌面歌词悬浮窗：无边框、透明、置顶的单例窗口，只显示当前一句歌词。
 * <p>
 * 依赖 {@link VlcPlayer} 单例，无法脱离播放器独立使用：歌词文本直接绑定
 * {@code VlcPlayer.lrcSelectedTextProperty}，显隐由 {@code desktopLrcShowProperty}
 * 的监听驱动。全项目仅由 {@code VlcPlayer} 在 {@code desktopLrcShow} 首次变为 true 时
 * 通过 {@link #getInstance()} 懒创建，之后开关由本类的监听自行显示或关闭窗口。
 * <p>
 * 字体、颜色、描边、阴影、加粗等样式全部绑定 {@code Config.setting.desktopLRC*}
 * 配置属性，改配置即时生效。窗口可拖拽移动；鼠标悬停 1 秒显示锁定按钮，
 * 锁定后歌词区域整体鼠标穿透，点击落到下层窗口，避免误拖动。
 * <p>
 * 窗口的创建与显隐必须在 JavaFX 应用线程进行；悬停计时用后台 {@code Timer}，
 * 回调统一经 {@code Platform.runLater} 回到 FX 线程。
 *
 * @author Leck
 */
public class DesktopLrcDialog extends Stage {
    private static DesktopLrcDialog desktopLrcDialog;
    /** 显隐标志位，仅记录意图，不参与 Stage 的实际显示/关闭 */
    private SimpleBooleanProperty isVisible;
    private Text text;

    private Timer timer;

    private SimpleBooleanProperty hasEffect=new SimpleBooleanProperty();
    private SimpleBooleanProperty hasStroke=new SimpleBooleanProperty();
    private SimpleBooleanProperty hasBold=new SimpleBooleanProperty();
    /**
     * 获取全局唯一的桌面歌词窗口，首次调用时才构建。
     * <p>
     * 懒加载期间无锁，多线程并发调用可能各自构建实例；实际调用方只有
     * {@code VlcPlayer} 的属性监听，恒在 FX 线程触发。
     * <p>
     * 注意返回的单例不保证已显示：构造时只对当前 {@code desktopLrcShow} 值补一次显示。
     *
     * @return 桌面歌词窗口单例，永不为 null
     */
    public static DesktopLrcDialog getInstance(){
        if (desktopLrcDialog==null){
            desktopLrcDialog=new DesktopLrcDialog();
        }
        return desktopLrcDialog;
    }

    /**
     * 构建悬浮窗：注册播放器与配置绑定、组装歌词节点与锁定按钮、加载样式表。
     * <p>
     * 构造结束时若播放器已要求显示桌面歌词（多为懒加载错过自身监听），
     * 会补发一次 {@code MESSAGE} 提示“悬停 1 秒可锁定”并立即显示窗口。
     */
    private DesktopLrcDialog() {
        isVisible=new SimpleBooleanProperty(false);
        //多创建一个stage，实现曲线救国
        //StageStyle.UTILITY创建的窗口在任务栏是没有图标的，但有默认的标题栏，过通过多创建一下stage，让歌词stage在依附于其，这样就不会在任务栏出现窗口了
        //前人的智慧，我tm怎么也想不出来啊
        Stage stage = new Stage();
        stage.setWidth(1.0);
        stage.setHeight(1.0);
        stage.initStyle(StageStyle.UTILITY);
        stage.setOpacity(0.0);
        stage.show();

        //设置拥有者
        initOwner(stage);
        initStyle(StageStyle.TRANSPARENT);

        VBox textPane=new VBox();
        textPane.setAlignment(Pos.CENTER);

        textPane.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT,null,null)));
        textPane.getStyleClass().add("dashboard-lrc-dialog");
        text=new Text();
        text.setMouseTransparent(true);
        text.setTextAlignment(TextAlignment.CENTER);
        text.setWrappingWidth(1800);
        text.setFontSmoothingType(FontSmoothingType.GRAY);
        text.textProperty().bind(VlcPlayer.getInstance().lrcSelectedTextProperty());

        // 自行监听 player 的 desktopLrcShow 属性控制显隐（依赖方向 UI → player）
        VlcPlayer.getInstance().desktopLrcShowProperty().addListener((observableValue, aBoolean, t1) -> {
            if (t1){
                EventBus.publish("MESSAGE",new MessageInfo(cn.tealc.asmr.model.message.MessageType.INFO,"鼠标悬停歌词弹窗1s可进行锁定与解锁"));
                this.show();
            }else{
                this.close();
            }
        });
        // 懒加载场景：set(true) 先于单例创建时，listener 错过事件，需补判当前值
        if (VlcPlayer.getInstance().isDesktopLrcShow()){
            EventBus.publish("MESSAGE",new MessageInfo(cn.tealc.asmr.model.message.MessageType.INFO,"鼠标悬停歌词弹窗1s可进行锁定与解锁"));
            this.show();
        }

        hasBold.bind(Config.setting.desktopLRCBoldModel);
        hasBold.addListener((observableValue, aBoolean, t1) -> {
            if (t1){
                text.fontProperty().bind(Bindings.createObjectBinding(
                        ()->
                                (Font.font(Font.getDefault().getFamily(),FontWeight.BOLD,Config.setting.desktopLRCFontSize.get())),
                        Config.setting.desktopLRCFontSize)
                );
            }else{
                text.fontProperty().bind(Bindings.createObjectBinding(
                        ()->
                                (Font.font(Config.setting.desktopLRCFontSize.get())),
                        Config.setting.desktopLRCFontSize)
                );
            }
        });



        if (hasBold.get()){
            text.fontProperty().bind(Bindings.createObjectBinding(
                    ()->
                            (Font.font(Font.getDefault().getFamily(),FontWeight.BOLD,Config.setting.desktopLRCFontSize.get())),
                    Config.setting.desktopLRCFontSize)
            );
        }else{
            text.fontProperty().bind(Bindings.createObjectBinding(
                    ()->
                            (Font.font(Config.setting.desktopLRCFontSize.get())),
                    Config.setting.desktopLRCFontSize)
            );
        }

        text.fillProperty().bind(Bindings.createObjectBinding(
                () ->
                        (Color.web(Config.setting.desktopLRCFontColor.get())),
                Config.setting.desktopLRCFontColor)
        );

        hasEffect.bind(Config.setting.desktopLRCBorderModel);
        hasEffect.addListener((observableValue, aBoolean, t1) -> {
            if (!t1){
                text.effectProperty().unbind();
                text.setEffect(null);
            }
            else
                text.effectProperty().bind(
                        Bindings.createObjectBinding(
                                ()->
                                        (new DropShadow(BlurType.THREE_PASS_BOX,Color.web(Config.setting.desktopLRCBorderColor.get()),10.0,0,0,0)),
                                Config.setting.desktopLRCBorderColor));
        });
        if (hasEffect.get()){
            text.effectProperty().bind(
                    Bindings.createObjectBinding(
                            ()->
                                    (new DropShadow(BlurType.THREE_PASS_BOX,Color.web(Config.setting.desktopLRCBorderColor.get()),10.0,0,0,0)),
                            Config.setting.desktopLRCBorderColor));
        }



        hasStroke.bind(Config.setting.desktopLRCStrokeModel);
        hasStroke.addListener((observableValue, aBoolean, t1) -> {
            if (!t1){
                text.strokeProperty().unbind();
                text.setStroke(null);
            }
            else
                text.strokeProperty().bind(Bindings.createObjectBinding(
                        () ->
                                (Color.web(Config.setting.desktopLRCStrokeColor.get())),
                        Config.setting.desktopLRCStrokeColor));
        });
        if (hasStroke.get()){
            text.strokeProperty().bind(Bindings.createObjectBinding(
                    () ->
                            (Color.web(Config.setting.desktopLRCStrokeColor.get())),
                    Config.setting.desktopLRCStrokeColor));
        }




        ToggleButton lockBtn=new ToggleButton();
        lockBtn.setGraphic(new Region());
        lockBtn.getStyleClass().add("lrc-lock-btn");



        textPane.getChildren().add(text);
        DragStageHandler dragStageHandler=new DragStageHandler(this);
        textPane.setOnMousePressed(dragStageHandler);
        textPane.setOnMouseDragged(dragStageHandler);

        
        VBox parentPane=new VBox(textPane,lockBtn);
        parentPane.setAlignment(Pos.CENTER);
        HBox root=new HBox(parentPane);
        root.setAlignment(Pos.CENTER);
        root.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT,null,null)));
        root.setPrefSize(1800,200);

        lockBtn.setVisible(false);
        lockBtn.setOnAction(actionEvent -> {
            if (lockBtn.isSelected()){
                textPane.setMouseTransparent(true);
            }else {
                textPane.setMouseTransparent(false);
            }
        });


        parentPane.setOnMouseEntered(mouseEvent -> {
            if (timer != null){
                timer.cancel();
                timer=null;
            }
            timer=new Timer(true);;
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() ->lockBtn.setVisible(true));
                }
            }, 1000);

        });
        parentPane.setOnMouseExited(mouseEvent -> {
            if (timer != null){
                timer.cancel();
                timer=null;
            }
            timer=new Timer(true);;
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() ->lockBtn.setVisible(false));
                }
            }, 1000);
        });





        Scene scene=new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        scene.getStylesheets().add(CssLoader.getCss(CssLoader.atlantafx));
        scene.getStylesheets().add(CssLoader.getCss(CssLoader.baseUI));
        scene.getStylesheets().add(CssLoader.getCss(CssLoader.lrcStage));
        setScene(scene);
        setAlwaysOnTop(true);









    }




    public boolean isVisible() {
        return isVisible.get();
    }

    public SimpleBooleanProperty isVisibleProperty() {
        return isVisible;
    }
    /**
     * 记录一次显隐意图。
     * <p>
     * 只写 {@code isVisible} 标志位，不会真正显示或关闭窗口——窗口的实际显隐
     * 始终由构造时注册的 {@code VlcPlayer.desktopLrcShow} 监听驱动。
     *
     * @param isVisible 新的显隐标志值
     */
    public void setVisible(boolean isVisible) {
        this.isVisible.set(isVisible);
    }
}