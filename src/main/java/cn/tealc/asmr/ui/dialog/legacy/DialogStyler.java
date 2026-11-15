package cn.tealc.asmr.ui.dialog.legacy;

import cn.tealc.asmr.utils.CssLoader;
import cn.tealc.asmr.utils.FontUtils;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.HeaderBar;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;
import java.util.Objects;

/**
 * 为 JavaFX Dialog 安装共享的 HeaderBar 和 shadcn 风格皮肤的样式器。
 * 所有 New* 组件构造时都调用 {@link #apply(Dialog)} 完成统一装配。
 */
public final class DialogStyler {
    private static final String DIALOG_STYLESHEET =
            "/cn/tealc/asmr/css/Dialog.css";

    private DialogStyler() {
    }

    /**
     * 把共享的 HeaderBar 和 shadcn 风格皮肤应用到指定 dialog。
     *
     * @param dialog 待样式化的 dialog，不能为 null
     * @param <R>    dialog 的返回值类型
     * @return 传入的 dialog（便于链式调用）
     */
    public static <R> Dialog<R> apply(Dialog<R> dialog) {
        Objects.requireNonNull(dialog, "dialog");

        DialogPane original = dialog.getDialogPane();
        NewDialogPane styledPane;
        if (original instanceof NewDialogPane existing) {
            styledPane = existing;
        } else {
            styledPane = new NewDialogPane();
            copyPaneState(original, styledPane);
            dialog.setDialogPane(styledPane);
        }

        URL stylesheet = DialogStyler.class.getResource(DIALOG_STYLESHEET);
        if (stylesheet != null && !styledPane.getStylesheets().contains(stylesheet.toExternalForm())) {
            styledPane.getStylesheets().add(stylesheet.toExternalForm());
        }

        // 弹窗是独立 Stage，不会继承主窗口 root 的内联样式与 -color-* 变量，
        // 这里显式加载 atlantafx.css 以保证 Dialog.css 中的颜色变量能够解析。
        String atlantafx = CssLoader.getCss(CssLoader.atlantafx);
        if (!styledPane.getStylesheets().contains(atlantafx)) {
            styledPane.getStylesheets().add(atlantafx);
        }

        styledPane.attach(dialog);
        // 弹窗是独立 Stage，不会继承主窗口 root 的内联样式，这里显式套用同一字体
        FontUtils.applyTo(styledPane);
        dialog.initStyle(StageStyle.EXTENDED);
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWN, event -> hideSystemButtons(dialog));
        return dialog;
    }

    /**
     * 把源 DialogPane 的状态（styleClass、buttonTypes、graphic、header、content 等）迁移到目标 pane。
     *
     * @param source 源 pane
     * @param target 目标 pane
     */
    private static void copyPaneState(DialogPane source, NewDialogPane target) {
        for (String styleClass : source.getStyleClass()) {
            if (!target.getStyleClass().contains(styleClass)) {
                target.getStyleClass().add(styleClass);
            }
        }

        target.getButtonTypes().setAll(source.getButtonTypes());
        target.setGraphic(source.getGraphic());
        target.setHeaderText(source.getHeaderText());
        target.setContentText(source.getContentText());
        if (source.getHeaderText() == null && source.getHeader() != null) {
            target.setHeader(source.getHeader());
        }
        if (source.getContentText() == null && source.getContent() != null) {
            target.setContent(source.getContent());
        }
        target.setExpandableContent(source.getExpandableContent());
        target.setExpanded(source.isExpanded());
    }

    /**
     * 隐藏 EXTENDED 样式默认渲染的系统按钮区，由自定义 HeaderBar 完全接管标题区。
     *
     * @param dialog 已显示的 dialog
     */
    private static void hideSystemButtons(Dialog<?> dialog) {
        Scene scene = dialog.getDialogPane().getScene();
        if (scene != null && scene.getWindow() instanceof Stage stage) {
            HeaderBar.setPrefButtonHeight(stage, 0);
        }
    }
}
