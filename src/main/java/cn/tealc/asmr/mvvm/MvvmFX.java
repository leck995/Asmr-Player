package cn.tealc.asmr.mvvm;

/**
 * 框架门面类。
 * <p>
 * 提供对全局通知中心的静态访问入口。
 * <p>
 * 用法：
 * <pre>{@code
 * MvvmFX.getNotificationCenter().subscribe("TO_PLAY", (s, objects) -> {...});
 * MvvmFX.getNotificationCenter().publish("TO_PLAY", work);
 * }</pre>
 *
 * @author Leck
 */
public class MvvmFX {
    private static final NotificationCenter INSTANCE = new DefaultNotificationCenter();

    /**
     * 获取全局通知中心实例。
     */
    public static NotificationCenter getNotificationCenter() {
        return INSTANCE;
    }
}
