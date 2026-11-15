package cn.tealc.asmr.mvvm;

/**
 * 通知观察者函数式接口。
 * <p>
 * 通过 {@link NotificationCenter#subscribe(String, NotificationObserver)} 注册，
 * 当对应主题的消息发布时被回调。
 *
 * @author Leck
 */
@FunctionalInterface
public interface NotificationObserver {

    /**
     * 收到通知时调用。
     *
     * @param key     消息主题名
     * @param payload 消息载荷
     */
    void receivedNotification(String key, Object... payload);
}
