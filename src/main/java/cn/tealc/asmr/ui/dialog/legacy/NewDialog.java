package cn.tealc.asmr.ui.dialog.legacy;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;

/**
 * 通用 JavaFX Dialog，构造时安装应用共享的 shadcn 风格 HeaderBar 皮肤。
 */
public class NewDialog<R> extends Dialog<R> {
    public NewDialog() {
        DialogStyler.apply(this);
    }

    public NewDialog(String title, Node content, ButtonType... buttonTypes) {
        this();
        setTitle(title);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().setAll(buttonTypes);
    }
}
