package cn.tealc.asmr.mvvm;

/**
 * ViewModel 标记接口。
 * <p>
 * 所有 ViewModel 类实现此接口，由 {@link FluentViewLoader} 在加载 View 时
 * 自动实例化并通过 {@link InjectViewModel} 注入到对应的 View 中。
 * <p>
 * 提供 publish/subscribe/unsubscribe 的便捷方法，委托给全局通知中心，
 * 使 ViewModel 内部可直接 {@code publish("EVENT", payload)} 而无需显式获取 MvvmFX 实例。
 *
 * @author Leck
 */
public interface ViewModel {

    /**
     * 发布消息到全局通知中心。
     *
     * @param messageName 消息主题名
     * @param payload     消息载荷
     */
    default void publish(String messageName, Object... payload) {
        MvvmFX.getNotificationCenter().publish(messageName, payload);
    }

    /**
     * 订阅全局消息。
     *
     * @param messageName 消息主题名
     * @param observer   观察者回调
     */
    default void subscribe(String messageName, NotificationObserver observer) {
        MvvmFX.getNotificationCenter().subscribe(messageName, observer);
    }

    /**
     * 取消订阅指定主题的观察者。
     *
     * @param messageName 消息主题名
     * @param observer   要移除的观察者
     */
    default void unsubscribe(String messageName, NotificationObserver observer) {
        MvvmFX.getNotificationCenter().unsubscribe(messageName, observer);
    }

    /**
     * 取消订阅所有主题下的指定观察者。
     *
     * @param observer 要移除的观察者
     */
    default void unsubscribe(NotificationObserver observer) {
        MvvmFX.getNotificationCenter().unsubscribe(observer);
    }
}
