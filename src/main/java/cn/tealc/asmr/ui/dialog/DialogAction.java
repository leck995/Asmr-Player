package cn.tealc.asmr.ui.dialog;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 弹窗动作按钮定义。
 * <p>
 * 点击后是否关闭弹窗完全由 {@code handler} 决定，不再依赖 {@code isCancelButton}：
 * 取消按钮需要显式调用 {@link DialogHandle#close()}，异步动作可以先
 * {@link DialogHandle#setBusy(boolean)} 再在完成时关闭。
 *
 * @author Leck
 */
public final class DialogAction {

    private final String text;
    private final String styleClass;
    private final Node graphic;
    private final boolean defaultButton;
    private final boolean cancelButton;
    private final ObservableValue<Boolean> disabled;
    private final Consumer<DialogHandle> handler;

    private DialogAction(String text, String styleClass, Node graphic, boolean defaultButton,
                         boolean cancelButton, ObservableValue<Boolean> disabled, Consumer<DialogHandle> handler) {
        this.text = Objects.requireNonNull(text, "text");
        this.styleClass = styleClass;
        this.graphic = graphic;
        this.defaultButton = defaultButton;
        this.cancelButton = cancelButton;
        this.disabled = disabled;
        this.handler = handler;
    }

    /**
     * 创建普通动作按钮，无额外样式。
     */
    public static DialogAction of(String text, Consumer<DialogHandle> handler) {
        return new DialogAction(text, null, null, false, false, null, handler);
    }

    /**
     * 创建带 AtlantaFX 样式（如 {@code accent}、{@code danger}）的动作按钮。
     */
    public static DialogAction of(String text, String styleClass, Consumer<DialogHandle> handler) {
        return new DialogAction(text, styleClass, null, false, false, null, handler);
    }

    /**
     * 创建点击后关闭弹窗的普通按钮。
     */
    public static DialogAction close(String text) {
        return of(text, DialogHandle::close);
    }

    /**
     * 创建点击后关闭弹窗的取消按钮。
     */
    public static DialogAction cancel(String text) {
        return close(text).cancelButton();
    }

    public DialogAction defaultButton() {
        return new DialogAction(text, styleClass, graphic, true, cancelButton, disabled, handler);
    }

    public DialogAction cancelButton() {
        return new DialogAction(text, styleClass, graphic, defaultButton, true, disabled, handler);
    }

    public DialogAction graphic(Node graphic) {
        return new DialogAction(text, styleClass, graphic, defaultButton, cancelButton, disabled, handler);
    }

    /**
     * 动作按钮的附加禁用条件，与忙碌状态取或。
     */
    public DialogAction disabledWhen(ObservableValue<Boolean> disabled) {
        return new DialogAction(text, styleClass, graphic, defaultButton, cancelButton, disabled, handler);
    }

    public String text() {
        return text;
    }

    public String styleClass() {
        return styleClass;
    }

    public Node graphic() {
        return graphic;
    }

    public boolean isDefaultButton() {
        return defaultButton;
    }

    public boolean isCancelButton() {
        return cancelButton;
    }

    public ObservableValue<Boolean> disabled() {
        return disabled;
    }

    public Consumer<DialogHandle> handler() {
        return handler;
    }
}
