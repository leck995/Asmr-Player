package cn.tealc.asmr.ui.dialog;

import javafx.scene.Node;

/**
 * 弹窗运行时句柄：由 {@link Dialogs#show} 返回，用于显式关闭弹窗、控制忙碌状态与读取结果。
 * <p>
 * 所有操作都会在 JavaFX 线程执行；在后台线程调用 {@link #close()} 或 {@link #setBusy(boolean)}
 * 时会自动调度到 JavaFX 线程。
 *
 * @author Leck
 */
public interface DialogHandle {

    /**
     * 关闭弹窗。重复调用无副作用。
     */
    void close();

    /**
     * 弹窗是否仍然打开（已请求关闭即返回 false）。
     */
    boolean isShowing();

    /**
     * 是否处于忙碌状态。忙碌时所有动作按钮禁用，且 ESC / 点击遮罩不会关闭弹窗。
     */
    boolean isBusy();

    /**
     * 设置忙碌状态。仅控制动作按钮与隐式关闭，不会禁用 body 中的自定义控件。
     */
    void setBusy(boolean busy);

    /**
     * 设置弹窗结果，供 {@code onClosed} 回调读取。
     */
    void setResult(Object result);

    /**
     * 读取弹窗结果。
     */
    Object result();

    /**
     * 弹窗内容根节点（{@code JFXDialogLayout}），供高级定制或测试使用。
     */
    Node content();
}
