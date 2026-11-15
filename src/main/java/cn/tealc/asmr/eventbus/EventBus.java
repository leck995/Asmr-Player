package cn.tealc.asmr.eventbus;

import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.mvvm.MvvmFX;
import cn.tealc.asmr.mvvm.NotificationObserver;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.scene.layout.Pane;

/**
 * 全局事件总线，封装 MvvmFX 通知中心。
 * <p>
 * 提供 dialog / alert / message 三类快捷方法，由 {@code OverlayLayer} 统一监听并展示。
 *
 * @author Leck
 */
public class EventBus{
    public static final String DIALOG = "dialog";
    public static final String MESSAGE = "MESSAGE";


    /**
     * 订阅事件，返回可取消的订阅句柄；不需要取消时可忽略返回值。
     */
    public static Subscription subscribe(String s, NotificationObserver notificationObserver) {
        MvvmFX.getNotificationCenter().subscribe(s, notificationObserver);
        return new Subscription(() -> unsubscribe(s, notificationObserver));
    }


    public static  void unsubscribe(String s, NotificationObserver notificationObserver) {
        MvvmFX.getNotificationCenter().unsubscribe(s, notificationObserver);
    }


    public static  void unsubscribe(NotificationObserver notificationObserver) {
        MvvmFX.getNotificationCenter().unsubscribe(notificationObserver);
    }


    public static  void publish(String s, Object... objects) {
        MvvmFX.getNotificationCenter().publish(s,objects);
    }


    public static  void publish(Object o, String s, Object[] objects) {
        MvvmFX.getNotificationCenter().publish(o,s,objects);
    }

    /**
     * 按主题订阅事件，返回可取消的订阅句柄；不需要取消时可忽略返回值。
     */
    public static Subscription subscribe(Object o, String s, NotificationObserver notificationObserver) {
        MvvmFX.getNotificationCenter().subscribe(o,s,notificationObserver);
        return new Subscription(() -> unsubscribe(o, s, notificationObserver));
    }


    public static  void unsubscribe(Object o, String s, NotificationObserver notificationObserver) {
        MvvmFX.getNotificationCenter().unsubscribe(o,s,notificationObserver);
    }


    public static  void unsubscribe(Object o, NotificationObserver notificationObserver) {
        MvvmFX.getNotificationCenter().unsubscribe(o,notificationObserver);
    }


    public static  void clear() {
        MvvmFX.getNotificationCenter().clear();
    }

    /**
     * 订阅句柄：持有取消订阅所需的信息，重复调用 {@link #unsubscribe()} 无副作用。
     */
    public static final class Subscription {
        private final Runnable unsubscribeAction;
        private boolean active = true;

        private Subscription(Runnable unsubscribeAction) {
            this.unsubscribeAction = unsubscribeAction;
        }

        public void unsubscribe() {
            if (!active) return;
            active = false;
            unsubscribeAction.run();
        }
    }


    public static void message(MessageInfo event) {
        MvvmFX.getNotificationCenter().publish(MESSAGE, event);
    }

    /**
     * 展示弹窗。
     * <p>
     * 弹窗入口已统一到 {@link cn.tealc.asmr.ui.component.OverlayLayer}：
     * 简单确认使用 {@link cn.tealc.asmr.ui.dialog.Dialogs} 或
     * {@link cn.tealc.asmr.ui.dialog.Alerts}，页面式弹窗使用
     * {@code Dialogs.show(DialogPage)}。
     *
     * @param pane 弹窗内容
     * @deprecated 请改用 {@code Dialogs.show(DialogPage)} 或 Dialogs 体系。
     */
    @Deprecated
    public static void dialog(Pane pane) {
        cn.tealc.asmr.ui.component.OverlayLayer.show(pane);
    }
}