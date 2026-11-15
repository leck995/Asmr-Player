package cn.tealc.asmr.ui.dialog;

import cn.tealc.asmr.ui.component.PageHeader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 简单确认与提示弹窗门面，基于 {@link Dialogs} 渲染。
 * <p>
 * 适用于消息提示、确认操作以及携带单选/复选等简单控件的场景；
 * 表单、列表、进度等复杂界面请直接使用 {@link Dialogs}。
 * <p>
 * {@code ok} 动作在执行完处理器后自动关闭弹窗，{@code cancel} 直接关闭。
 *
 * @author Leck
 */
public final class Alerts {

    /** 弹窗类型：决定标题图标、图标着色与主按钮配色 */
    public enum AlertType {
        BASIC(Material2AL.HELP_OUTLINE, null),
        INFO(Material2AL.INFO, "info"),
        WARNING(Material2MZ.WARNING, "warning"),
        DANGER(Material2AL.HIGHLIGHT_OFF, "danger"),
        SUCCESS(Material2AL.CHECK_CIRCLE, "success");

        private final Ikon icon;
        private final String styleClass;

        AlertType(Ikon icon, String styleClass) {
            this.icon = icon;
            this.styleClass = styleClass;
        }

        public Ikon icon() {
            return icon;
        }

        public String styleClass() {
            return styleClass;
        }
    }

    private Alerts() { }

    public static Builder create() {
        return basic();
    }

    public static Builder basic() {
        return new Builder(AlertType.BASIC);
    }

    public static Builder info() {
        return new Builder(AlertType.INFO);
    }

    public static Builder warning() {
        return new Builder(AlertType.WARNING);
    }

    public static Builder danger() {
        return new Builder(AlertType.DANGER);
    }

    public static Builder success() {
        return new Builder(AlertType.SUCCESS);
    }

    public static final class Builder {
        private final AlertType type;
        private final List<DialogAction> actions = new ArrayList<>();
        private final List<String> stylesheets = new ArrayList<>();
        private String title;
        private String message;
        private Node content;

        private Builder(AlertType type) {
            this.type = type;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * 消息下方的自定义内容，用于放置单选、复选等简单控件。
         */
        public Builder content(Node content) {
            this.content = content;
            return this;
        }

        /**
         * 为弹窗追加样式表，用于复用调用方界面的既有样式。
         */
        public Builder stylesheet(String... stylesheets) {
            this.stylesheets.addAll(List.of(stylesheets));
            return this;
        }

        /**
         * 主按钮：执行处理器后自动关闭弹窗。
         */
        public Builder ok(String text, Consumer<DialogHandle> action) {
            actions.add(DialogAction.of(text, "default-button", handle -> {
                try {
                    if (action != null) action.accept(handle);
                } finally {
                    handle.close();
                }
            }).defaultButton());
            return this;
        }

        public Builder ok(String text) {
            return ok(text, null);
        }

        public Builder ok(Consumer<DialogHandle> action) {
            return ok("确定", action);
        }

        public Builder cancel() {
            return cancel("取消");
        }

        public Builder cancel(String text) {
            actions.add(DialogAction.of(text, "cancel-button", DialogHandle::close).cancelButton());
            return this;
        }

        /**
         * 自定义次要按钮，是否关闭由处理器自行决定。
         */
        public Builder button(String text, Consumer<DialogHandle> action) {
            return button(text, "cancel-button", action);
        }

        /**
         * 自定义按钮及样式类，是否关闭由处理器自行决定。
         * 主操作可传 {@code default-button}，颜色由弹窗类型决定。
         */
        public Builder button(String text, String styleClass, Consumer<DialogHandle> action) {
            actions.add(DialogAction.of(text, styleClass, action));
            return this;
        }

        public DialogHandle show() {
            DialogSpec.Builder spec = DialogSpec.builder().styleClass("jfx-alter");
            if (type.styleClass() != null) {
                spec.styleClass(type.styleClass());
            }
            if (!stylesheets.isEmpty()) {
                spec.stylesheet(stylesheets.toArray(String[]::new));
            }
            if (title != null && !title.isBlank()) {
                spec.heading(buildHeading());
            }
            Node body = buildBody();
            if (body != null) {
                spec.content(body);
            }
            actions.forEach(spec::action);
            return Dialogs.show(spec.build());
        }

        private PageHeader buildHeading() {
            PageHeader header = new PageHeader();
            header.setIcon(type.icon());
            header.setTitle(title);
            return header;
        }

        private Node buildBody() {
            if (message == null) {
                return content;
            }
            Label label = new Label(message);
            label.setWrapText(true);
            label.getStyleClass().add("alter-message");
            if (content == null) {
                return label;
            }
            VBox body = new VBox(10.0, label, content);
            body.getStyleClass().add("alter-body");
            return body;
        }
    }
}
