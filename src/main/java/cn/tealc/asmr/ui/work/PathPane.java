package cn.tealc.asmr.ui.work;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;

/**
 * 面包屑路径导航控件。
 * <p>
 * 将一个相对路径按分隔符拆分成多段，每段渲染为一个按钮，点击某段时通过
 * {@link #onActionProperty()} 通知外部，值为该段对应的完整相对路径前缀。
 *
 * @author Leck
 */
public class PathPane extends HBox {

    /** 点击某段路径时通知外部，值为该段对应的完整相对路径前缀 */
    private final SimpleStringProperty onAction = new SimpleStringProperty();

    public PathPane() {
        setSpacing(0);
    }

    /**
     * 设置相对路径，按分隔符拆分成路径段，重建所有面包屑按钮。
     *
     * @param relativePath 相对路径（如 {@code "audio\sub\track1"} 或 {@code "audio/sub/track1"}）
     */
    public void setPath(String relativePath) {
        // 空路径：直接清空
        if (relativePath == null || relativePath.isBlank()) {
            getChildren().clear();
            return;
        }
        // 同时兼容 \ 和 / 分隔符，避免路径混用导致拆分失败
        String[] segments = relativePath.split("[/\\\\]+");
        // 拼接时沿用输入路径的分隔符：远程路径是 /，本地 Windows 路径是 \
        String separator = relativePath.indexOf('/') >= 0 ? "/" : File.separator;
        StringBuilder prefix = new StringBuilder();
        int existing = getChildren().size();

        for (int i = 0; i < segments.length; i++) {
            if (prefix.length() > 0) {
                prefix.append(separator);
            }
            prefix.append(segments[i]);
            String fullPath = prefix.toString();

            if (i < existing) {
                // 复用已有按钮，只更新文本和路径，避免重建导致闪烁
                Button button = (Button) getChildren().get(i);
                button.setText(segments[i]);
                button.setUserData(fullPath);
            } else {
                // 已有按钮不够，追加新的
                getChildren().add(createItem(segments[i], fullPath));
            }
        }

        // 路径段比已有按钮少时，删除多余的尾部按钮
        if (segments.length < existing) {
            getChildren().remove(segments.length, existing);
        }
    }

    /**
     * 创建一个路径段按钮，点击时将其完整路径前缀通过 onAction 通知外部。
     *
     * @param name      按钮显示的段名
     * @param fullPath  该段对应的完整相对路径前缀
     */
    private Button createItem(String name, String fullPath) {
        Button button = new Button(name);
        button.setMaxWidth(300.0);
        button.setGraphic(new FontIcon());
        button.setGraphicTextGap(10.0);
        button.setUserData(fullPath);
        button.setOnAction(e -> {
            // set(null) 再 set(value)，确保同值也能触发通知
            onAction.set(null);
            onAction.set(fullPath);
        });
        return button;
    }

    /**
     * 获取当前 onAction 值（最近点击的路径段前缀）
     */
    public String getOnActionProperty() {
        return onAction.get();
    }

    /**
     * onAction 属性，点击路径段按钮时触发
     */
    public SimpleStringProperty onActionProperty() {
        return onAction;
    }
}
