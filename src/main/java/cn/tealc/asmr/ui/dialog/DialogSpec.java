package cn.tealc.asmr.ui.dialog;

import cn.tealc.asmr.ui.component.PageHeader;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 弹窗声明式描述：标题、消息、自定义内容、宽度、动作与行为选项。
 * <p>
 * 标题统一由 {@link PageHeader} 呈现；通过 {@link #builder()} 构建，
 * 交给 {@link Dialogs#show(DialogSpec)} 展示。
 *
 * @author Leck
 */
public final class DialogSpec {

    private final String title;
    private final PageHeader heading;
    private final String message;
    private final Node content;
    private final double width;
    private final List<String> styleClasses;
    private final List<String> stylesheets;
    private final List<DialogAction> actions;
    private final DialogOptions options;

    private DialogSpec(Builder builder) {
        this.title = builder.title;
        this.heading = builder.heading;
        this.message = builder.message;
        this.content = builder.content;
        this.width = builder.width;
        this.styleClasses = List.copyOf(builder.styleClasses);
        this.stylesheets = List.copyOf(builder.stylesheets);
        this.actions = List.copyOf(builder.actions);
        this.options = new DialogOptions(builder.overlayClickCloses, builder.escapeCloses, builder.onClosed);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String title() {
        return title;
    }

    /**
     * 自定义标题页头，优先级高于 {@link #title()}。
     */
    public PageHeader heading() {
        return heading;
    }

    public String message() {
        return message;
    }

    public Node content() {
        return content;
    }

    public double width() {
        return width;
    }

    public List<String> styleClasses() {
        return styleClasses;
    }

    public List<String> stylesheets() {
        return stylesheets;
    }

    public List<DialogAction> actions() {
        return actions;
    }

    public DialogOptions options() {
        return options;
    }

    public static final class Builder {
        private String title;
        private PageHeader heading;
        private String message;
        private Node content;
        private double width;
        private final List<String> styleClasses = new ArrayList<>();
        private final List<String> stylesheets = new ArrayList<>();
        private final List<DialogAction> actions = new ArrayList<>();
        private boolean overlayClickCloses = true;
        private boolean escapeCloses = true;
        private Consumer<DialogHandle> onClosed;

        private Builder() { }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * 自定义标题页头，设置后忽略 {@link #title(String)}。
         * <p>
         * 弹窗标题统一使用 {@link PageHeader}；仅设置标题文本时，宿主会自动创建页头。
         */
        public Builder heading(PageHeader heading) {
            this.heading = heading;
            return this;
        }

        /**
         * 顶部消息文本，显示在自定义内容上方。
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * 自定义 body 内容。
         */
        public Builder content(Node content) {
            this.content = content;
            return this;
        }

        /**
         * 弹窗宽度，0 表示由内容决定。
         */
        public Builder width(double width) {
            this.width = width;
            return this;
        }

        public Builder styleClass(String... styleClasses) {
            this.styleClasses.addAll(List.of(styleClasses));
            return this;
        }

        public Builder stylesheet(String... stylesheets) {
            this.stylesheets.addAll(List.of(stylesheets));
            return this;
        }

        public Builder action(DialogAction action) {
            this.actions.add(Objects.requireNonNull(action, "action"));
            return this;
        }

        public Builder action(String text, Consumer<DialogHandle> handler) {
            return action(DialogAction.of(text, handler));
        }

        public Builder action(String text, String styleClass, Consumer<DialogHandle> handler) {
            return action(DialogAction.of(text, styleClass, handler));
        }

        /**
         * 添加一个点击后关闭弹窗的取消按钮。
         */
        public Builder cancel() {
            return cancel("取消");
        }

        public Builder cancel(String text) {
            return action(DialogAction.cancel(text));
        }

        public Builder overlayClickCloses(boolean overlayClickCloses) {
            this.overlayClickCloses = overlayClickCloses;
            return this;
        }

        public Builder escapeCloses(boolean escapeCloses) {
            this.escapeCloses = escapeCloses;
            return this;
        }

        public Builder onClosed(Consumer<DialogHandle> onClosed) {
            this.onClosed = onClosed;
            return this;
        }

        public DialogSpec build() {
            return new DialogSpec(this);
        }
    }
}
