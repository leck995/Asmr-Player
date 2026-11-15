package cn.tealc.asmr.ui.dialog;

import java.util.function.Consumer;

/**
 * 弹窗行为选项。
 *
 * @param overlayClickCloses 点击遮罩层是否关闭弹窗，默认 true
 * @param escapeCloses       按 ESC 是否关闭弹窗，默认 true
 * @param onClosed           弹窗完全关闭后的回调，可为 null
 * @author Leck
 */
public record DialogOptions(boolean overlayClickCloses, boolean escapeCloses, Consumer<DialogHandle> onClosed) {
}
