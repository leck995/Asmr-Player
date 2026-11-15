package cn.tealc.asmr.ui.dialog;

import cn.tealc.asmr.ui.component.OverlayLayer;

/**
 * 全局弹窗入口。
 * <p>
 * 与 {@code EventBus.dialog/alert} 不同，{@code Dialogs} 返回 {@link DialogHandle}，
 * 调用方可以显式关闭弹窗、控制忙碌状态、读取结果，并通过 {@link DialogOptions} 配置
 * 遮罩点击、ESC 与关闭回调。
 *
 * @author Leck
 */
public final class Dialogs {

    private Dialogs() { }

    /**
     * 展示页面式弹窗，返回可用于显式关闭、忙碌控制与结果读取的句柄。
     * <p>
     * 页面分别提供标题、正文与动作，宿主放入弹窗对应插槽；
     * 实现类通过 {@link DialogPage#onDialogAttached(DialogHandle)} 获取同一个句柄。
     */
    public static DialogHandle show(DialogPage page) {
        return OverlayLayer.show(page);
    }

    /**
     * 展示弹窗，必须在 JavaFX 线程调用。
     */
    public static DialogHandle show(DialogSpec spec) {
        return OverlayLayer.show(spec);
    }

    /**
     * 展示弹窗。
     */
    public static DialogHandle show(DialogSpec.Builder builder) {
        return show(builder.build());
    }
}
