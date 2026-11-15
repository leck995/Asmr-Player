package cn.tealc.asmr.ui.dialog.legacy;

import javafx.scene.control.ChoiceDialog;

import java.util.Collection;

/**
 * ChoiceDialog，构造时安装应用共享的 shadcn 风格 HeaderBar 皮肤。
 */
public class NewChoiceDialog<T> extends ChoiceDialog<T> {
    public NewChoiceDialog() {
        super();
        DialogStyler.apply(this);
    }

    @SafeVarargs
    public NewChoiceDialog(T defaultChoice, T... choices) {
        super(defaultChoice, choices);
        DialogStyler.apply(this);
    }

    public NewChoiceDialog(T defaultChoice, Collection<T> choices) {
        super(defaultChoice, choices);
        DialogStyler.apply(this);
    }
}
