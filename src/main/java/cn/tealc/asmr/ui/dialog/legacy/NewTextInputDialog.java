package cn.tealc.asmr.ui.dialog.legacy;

import javafx.scene.control.TextInputDialog;

/**
 * TextInputDialog，构造时安装应用共享的 shadcn 风格 HeaderBar 皮肤。
 */
public class NewTextInputDialog extends TextInputDialog {
    public NewTextInputDialog() {
        super();
        DialogStyler.apply(this);
    }

    public NewTextInputDialog(String defaultValue) {
        super(defaultValue);
        DialogStyler.apply(this);
    }
}
