package cn.tealc.asmr.mvvm;

/**
 * 通知中心接口。
 * <p>
 * 提供全局发布-订阅消息总线，用于组件间解耦通信。
 * 通过 {@link MvvmFX#getNotificationCenter()} 获取全局实例。
 *
 * @author Leck
 */
public interface NotificationCenter {

    /**
     * 订阅全局消息。
     *
     * @param messageName 消息主题名
     * @param observer    观察者回调
     */
    void subscribe(String messageName, NotificationObserver observer);

    /**
     * 取消订阅指定主题的指定观察者。
     *
     * @param messageName 消息主题名
     * @param observer    要移除的观察者
     */
    void unsubscribe(String messageName, NotificationObserver observer);

    /**
     * 取消订阅所有主题下的指定观察者。
     *
     * @param observer 要移除的观察者
     */
    void unsubscribe(NotificationObserver observer);

    /**
     * 发布全局消息，同步调用所有匹配的观察者。
     *
     * @param messageName 消息主题名
     * @param payload      消息载荷
     */
    void publish(String messageName, Object... payload);

    /**
     * 发布对象级消息，仅通知订阅了该 channel 的观察者。
     *
     * @param channel     通道对象
     * @param messageName 消息主题名
     * @param payload      消息载荷数组
     */
    void publish(Object channel, String messageName, Object[] payload);

    /**
     * 订阅对象级消息（仅在该 channel 对象范围内通知）。
     *
     * @param channel     通道对象
     * @param messageName 消息主题名
     * @param observer    观察者回调
     */
    void subscribe(Object channel, String messageName, NotificationObserver observer);

    /**
     * 取消订阅指定通道和主题的观察者。
     *
     * @param channel     通道对象
     * @param messageName 消息主题名
     * @param observer    要移除的观察者
     */
    void unsubscribe(Object channel, String messageName, NotificationObserver observer);

    /**
     * 取消订阅指定通道的所有观察者。
     *
     * @param channel  通道对象
     * @param observer 要移除的观察者
     */
    void unsubscribe(Object channel, NotificationObserver observer);

    /**
     * 清除所有订阅。
     */
    void clear();
}
