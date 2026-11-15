package cn.tealc.asmr.task.background;

import org.kordamp.ikonli.Ikon;

/**
 * 后台任务的额外操作项。
 * <p>
 * 用于在任务列表行内显示自定义按钮（如「打开日志」），
 * 由具体任务在创建时通过 {@link BackgroundTask#addExtraAction(TaskAction)} 注册。
 * 操作在 JavaFX Application Thread 执行，因此 handler 内可直接操作 UI。
 *
 * @param name    按钮文案
 * @param icon    按钮图标
 * @param handler 点击回调
 * @author Leck
 */
public record TaskAction(String name, Ikon icon, Runnable handler) {
}
