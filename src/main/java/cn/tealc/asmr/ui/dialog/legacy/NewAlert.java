package cn.tealc.asmr.ui.dialog.legacy;

import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.Objects;

/**
 * 带应用共享 HeaderBar 和 shadcn 风格皮肤的 Alert。
 * 提供 INFO/WARNING/DANGER/SUCCESS/DEFAULT 五种语义样式，每种对应不同边框色、图标色、按钮配色。
 */
public class NewAlert extends Alert {

    /** 默认内容宽度：长文本在 CONTENT_PREF_WIDTH 内换行，弹窗宽度不会随文本变长。 */
    private static final double CONTENT_PREF_WIDTH = 300;
    private static final double CONTENT_MAX_WIDTH = 300;

    /**
     * Alert 的语义样式枚举，映射 styleClass、Ikon 图标与 JavaFX AlertType。
     */
    public enum AlertStyle {
        INFO("shadcn-alert-info", Material2OutlinedAL.INFO, AlertType.INFORMATION),
        WARNING("shadcn-alert-warning", Material2OutlinedMZ.WARNING, AlertType.WARNING),
        DANGER("shadcn-alert-danger", Material2OutlinedAL.ERROR_OUTLINE, AlertType.ERROR),
        SUCCESS("shadcn-alert-success", Material2OutlinedAL.CHECK_CIRCLE, AlertType.INFORMATION),
        DEFAULT("shadcn-alert-default", Material2OutlinedAL.HELP_OUTLINE, AlertType.NONE);

        private final String styleClass;
        private final Ikon icon;
        private final AlertType alertType;

        AlertStyle(String styleClass, Ikon icon, AlertType alertType) {
            this.styleClass = styleClass;
            this.icon = icon;
            this.alertType = alertType;
        }

        String styleClass() {
            return styleClass;
        }

        Ikon icon() {
            return icon;
        }

        AlertType alertType() {
            return alertType;
        }

        static AlertStyle from(AlertType alertType) {
            return switch (alertType) {
                case INFORMATION -> INFO;
                case WARNING -> WARNING;
                case ERROR -> DANGER;
                case CONFIRMATION, NONE -> DEFAULT;
            };
        }
    }

    private AlertStyle alertStyle;
    private final HBox alertContent = new HBox();
    private final Label contentLabel = new Label();
    private FontIcon alertIcon;

    public NewAlert(AlertType alertType) {
        super(alertType);
        DialogStyler.apply(this);
        installAlertContent();
        setAlertStyle(AlertStyle.from(alertType));
    }

    public NewAlert(AlertType alertType, String contentText, ButtonType... buttons) {
        super(alertType, contentText, buttons);
        DialogStyler.apply(this);
        installAlertContent();
        setAlertStyle(AlertStyle.from(alertType));
    }

    public NewAlert(AlertStyle style) {
        super(Objects.requireNonNull(style, "style").alertType());
        DialogStyler.apply(this);
        installAlertContent();
        setAlertStyle(style);
    }

    public NewAlert(AlertStyle style, String contentText, ButtonType... buttons) {
        super(Objects.requireNonNull(style, "style").alertType(), contentText, buttons);
        DialogStyler.apply(this);
        installAlertContent();
        setAlertStyle(style);
    }

    public static NewAlert info(String contentText) {
        return new NewAlert(AlertStyle.INFO, contentText, ButtonType.OK);
    }

    public static NewAlert warning(String contentText) {
        return new NewAlert(AlertStyle.WARNING, contentText, ButtonType.OK);
    }

    public static NewAlert success(String contentText) {
        return new NewAlert(AlertStyle.SUCCESS, contentText, ButtonType.OK);
    }

    public static NewAlert danger(String contentText) {
        return new NewAlert(AlertStyle.DANGER, contentText, ButtonType.OK);
    }

    public static NewAlert defaultAlert(String contentText) {
        return new NewAlert(AlertStyle.DEFAULT, contentText, ButtonType.OK);
    }

    public AlertStyle getAlertStyle() {
        return alertStyle;
    }

    /**
     * 切换 Alert 的语义样式：更新 pane 的 styleClass 与图标。
     *
     * @param style 语义样式，不能为 null
     */
    public final void setAlertStyle(AlertStyle style) {
        this.alertStyle = Objects.requireNonNull(style, "style");

        if (!(getDialogPane() instanceof NewDialogPane pane)) {
            return;
        }

        pane.getStyleClass().removeIf(cssClass ->
                cssClass.startsWith("shadcn-alert-") && !cssClass.equals("shadcn-alert-pane"));
        pane.getStyleClass().add(style.styleClass());
        pane.setGraphic(null);
        pane.setHeader(null);
        pane.setHeaderText(null);
        pane.setHeaderRightGraphic(null);

        if (alertIcon != null) {
            alertContent.getChildren().remove(alertIcon);
        }
        alertIcon = new FontIcon(style.icon());
        alertIcon.getStyleClass().add("shadcn-alert-icon");
        alertContent.getChildren().addFirst(alertIcon);
    }

    /**
     * 安装自定义 Alert 内容：左侧图标 + 右侧换行文本，替换默认 content。
     */
    private void installAlertContent() {
        if (!(getDialogPane() instanceof NewDialogPane pane)) {
            return;
        }

        pane.getStyleClass().add("shadcn-alert-pane");
        pane.setHeader(null);
        pane.setHeaderText(null);
        pane.setGraphic(null);

        contentLabel.getStyleClass().add("shadcn-alert-content-label");
        contentLabel.setWrapText(true);
        contentLabel.setPrefWidth(CONTENT_PREF_WIDTH);
        contentLabel.setMaxWidth(CONTENT_MAX_WIDTH);
        HBox.setHgrow(contentLabel, Priority.ALWAYS);

        alertContent.getStyleClass().add("shadcn-alert-content");
        alertContent.setMaxWidth(CONTENT_MAX_WIDTH + 40);
        alertContent.setAlignment(Pos.TOP_LEFT);
        alertContent.getChildren().add(contentLabel);

        pane.contentTextProperty().addListener((observable, oldValue, newValue) ->
                contentLabel.setText(newValue == null ? "" : newValue));
        contentLabel.setText(pane.getContentText() == null ? "" : pane.getContentText());
        pane.setContent(alertContent);
    }
}
