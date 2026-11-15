package cn.tealc.asmr.ui.component;

import atlantafx.base.controls.Message;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.dialog.DialogAction;
import cn.tealc.asmr.ui.dialog.DialogHandle;
import cn.tealc.asmr.ui.dialog.DialogOptions;
import cn.tealc.asmr.ui.dialog.DialogPage;
import cn.tealc.asmr.ui.dialog.DialogSpec;
import cn.tealc.asmr.utils.CssLoader;
import com.jfoenix.controls.JFXDialog;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import com.jfoenix.controls.events.JFXDialogEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 应用级覆盖层：统一承载 dialog / message 两类全局展示。
 * <p>
 * 作为 {@code root} StackPane 的最顶层子节点；JFXDialog 挂载在 root 上以保持遮罩/居中行为。
 * 透明区域通过 {@code pickOnBounds=false} 透传点击，仅 message 实际占用区域拾取事件。
 * <p>
 * 同时作为静态工具类，提供 {@link #show(DialogSpec)} 声明式弹窗、
 * {@link #show(Region)} 与 {@link #showAndWait(JFXDialogLayout)} 供调用方直接使用。
 *
 * @author Leck
 */
public class OverlayLayer extends AnchorPane {

    private static StackPane root;
    private static final Deque<DialogEntry> openDialogs = new ArrayDeque<>();
    private static final List<EventBus.Subscription> subscriptions = new ArrayList<>(2);
    private final VBox messagePane = new VBox(5.0);

    /**
     * 创建覆盖层并订阅全局事件。
     *
     * @param root 主窗口的根 StackPane，JFXDialog 挂载于此
     */
    public OverlayLayer(StackPane root) {
        OverlayLayer.root = root;
        setPickOnBounds(false);

        AnchorPane.setTopAnchor(messagePane, 60.0);
        AnchorPane.setRightAnchor(messagePane, 10.0);
        messagePane.setPickOnBounds(false);
        getChildren().add(messagePane);

        root.addEventFilter(KeyEvent.KEY_PRESSED, OverlayLayer::handleEscape);
        initGlobalEvents();
    }

    /**
     * ESC 只关闭最上层弹窗；busy 或显式禁用时吞掉事件但不关闭。
     */
    private static void handleEscape(KeyEvent event) {
        if (event.getCode() != KeyCode.ESCAPE) return;
        DialogEntry top = topDialog();
        if (top == null) return;
        event.consume();
        if (top.busy.get() || !top.options.escapeCloses()) return;
        top.close();
    }

    private static DialogEntry topDialog() {
        for (DialogEntry entry : openDialogs) {
            if (entry.isShowing()) return entry;
        }
        return null;
    }

    private void initGlobalEvents() {
        subscriptions.forEach(EventBus.Subscription::unsubscribe);
        subscriptions.clear();
        // MESSAGE：右上角消息卡片
        subscriptions.add(EventBus.subscribe(EventBus.MESSAGE,
                (s, objects) -> showMessage((MessageInfo) objects[0])));
    }

    // ==================== 静态弹窗 API ====================

    /**
     * 展示 JFXDialogLayout 弹窗，返回 JFXDialog 引用供调用方控制。
     * <p>
     * cancelButton 自动绑定关闭逻辑（先关闭弹窗，再执行原有 onAction）。
     *
     * @param layout 弹窗内容
     * @return JFXDialog 引用，可设 overlayClose、onDialogClosed 等
     */
    public static JFXDialog show(JFXDialogLayout layout) {
        JFXDialog dialog = createDialog(layout);
        bindActions(layout.getActions(), dialog);
        dialog.show();
        return dialog;
    }

    /**
     * 展示页面式弹窗，返回运行时句柄供界面与调用方控制。
     * <p>
     * 标题、正文与动作分别进入弹窗的三个插槽；界面通过
     * {@link DialogPage#onDialogAttached(DialogHandle)} 获取句柄，自行决定何时关闭。
     *
     * @param page 弹窗页面
     * @return 弹窗句柄
     */
    public static DialogHandle show(DialogPage page) {
        Objects.requireNonNull(page, "page");
        requireDialogReady();

        PageHeader header = Objects.requireNonNull(page.header(), "header");
        Node body = Objects.requireNonNull(page.content(), "content");

        DialogSpec.Builder builder = DialogSpec.builder()
                .heading(header)
                .content(body)
                .overlayClickCloses(page.overlayClickCloses())
                .escapeCloses(page.escapeCloses())
                .onClosed(handle -> page.onDialogClosed());
        if (page.width() > 0) {
            builder.width(page.width());
        }
        if (!page.stylesheets().isEmpty()) {
            builder.stylesheet(page.stylesheets().toArray(String[]::new));
        }
        List<DialogAction> actions = Objects.requireNonNull(page.actions(), "actions");
        actions.forEach(builder::action);

        DialogSpec spec = builder.build();
        DialogEntry entry = new DialogEntry(spec.options());
        JFXDialogLayout layout = buildLayout(spec, entry);
        DialogHandle handle = present(entry, layout, spec.options().overlayClickCloses(),
                page::onDialogAttached);
        page.onDialogShown();
        return handle;
    }

    /**
     * 展示 Pane 弹窗，返回 JFXDialog 引用供调用方控制。
     * <p>
     * cancelButton 自动绑定关闭逻辑；兜底查找 id=cancel 的按钮。
     *
     * @param pane 弹窗内容
     * @return JFXDialog 引用
     */
    public static JFXDialog show(Pane pane) {
        JFXDialog dialog = createDialog(pane);
        for (Node node : pane.lookupAll("Button")) {
            if (node instanceof Button btn && btn.isCancelButton()) {
                bindButton(btn, dialog);
            }
        }
        Node cancelNode = pane.lookup("#cancel");
        if (cancelNode instanceof Button btn && !btn.isCancelButton()) {
            bindButton(btn, dialog);
        }
        dialog.show();
        return dialog;
    }

    /**
     * 展示声明式弹窗，返回可用于显式关闭、忙碌控制与结果读取的句柄。
     * <p>
     * 动作按钮不会自动关闭弹窗，关闭必须由 handler 调用 {@link DialogHandle#close()}。
     *
     * @param spec 弹窗描述
     * @return 弹窗句柄
     */
    public static DialogHandle show(DialogSpec spec) {
        Objects.requireNonNull(spec, "spec");
        requireDialogReady();

        DialogEntry entry = new DialogEntry(spec.options());
        JFXDialogLayout layout = buildLayout(spec, entry);
        return present(entry, layout, spec.options().overlayClickCloses(), null);
    }

    private static void requireDialogReady() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Dialogs.show 必须在 JavaFX 线程调用");
        }
        if (root == null) {
            throw new IllegalStateException("OverlayLayer 尚未初始化");
        }
    }

    private static DialogHandle present(DialogEntry entry, Region content, boolean overlayClickCloses,
                                        Consumer<DialogHandle> beforeShow) {
        JFXDialog dialog = createDialog(content);
        entry.dialog = dialog;
        entry.contentNode = content;
        dialog.setOverlayClose(overlayClickCloses);
        entry.busy.addListener((observable, previous, busy) ->
                dialog.setOverlayClose(!busy && overlayClickCloses));
        dialog.setOnDialogClosed(event -> entry.notifyClosed());

        if (beforeShow != null) {
            beforeShow.accept(entry);
        }
        openDialogs.push(entry);
        entry.showing = true;
        dialog.show();
        return entry;
    }

    private static JFXDialogLayout buildLayout(DialogSpec spec, DialogEntry entry) {
        JFXDialogLayout layout = new JFXDialogLayout();
        layout.getStyleClass().add("app-dialog");
        layout.getStyleClass().addAll(spec.styleClasses());
        // 通用弹窗样式挂在这里，保证 Dialogs/Alerts 产出的弹窗外观一致
        layout.getStylesheets().add(CssLoader.getCss(CssLoader.dialogCommon));
        layout.getStylesheets().addAll(spec.stylesheets());

        PageHeader heading = spec.heading();
        if (heading == null && spec.title() != null && !spec.title().isBlank()) {
            heading = new PageHeader();
            heading.setTitle(spec.title());
        }
        if (heading != null) {
            layout.setHeading(prepareHeading(heading));
        }

        Node body = buildBody(spec);
        if (body != null) {
            layout.setBody(body);
        }

        List<Node> actions = new ArrayList<>();
        for (DialogAction action : spec.actions()) {
            actions.add(createButton(action, entry));
        }
        if (!actions.isEmpty()) {
            layout.setActions(actions);
        }

        if (spec.width() > 0) {
            layout.setPrefWidth(spec.width());
            layout.setMaxWidth(spec.width());
        }
        return layout;
    }

    /**
     * 弹窗标题区已经负责分隔线与内边距，页头只保留标题内容。
     */
    private static PageHeader prepareHeading(PageHeader header) {
        header.setSeparatorVisible(false);
        if (!header.getStyleClass().contains("dialog-page-header")) {
            header.getStyleClass().add("dialog-page-header");
        }
        return header;
    }

    private static Node buildBody(DialogSpec spec) {
        Label message = null;
        if (spec.message() != null && !spec.message().isBlank()) {
            message = new Label(spec.message());
            message.setWrapText(true);
            message.getStyleClass().add("dialog-message");
        }
        if (message == null) {
            return spec.content();
        }
        if (spec.content() == null) {
            return message;
        }
        return new VBox(10.0, message, spec.content());
    }

    private static Button createButton(DialogAction action, DialogEntry entry) {
        Button button = new Button(action.text());
        if (action.graphic() != null) {
            button.setGraphic(action.graphic());
        }
        boolean hasCustomStyle = action.styleClass() != null && !action.styleClass().isBlank();
        if (hasCustomStyle) {
            button.getStyleClass().add(action.styleClass());
        }
        if (action.isDefaultButton() && !button.getStyleClass().contains("default-button")) {
            button.getStyleClass().add("default-button");
        }
        if (action.isCancelButton() && !button.getStyleClass().contains("cancel-button")) {
            button.getStyleClass().add("cancel-button");
        }
        if (!hasCustomStyle && !action.isDefaultButton() && !action.isCancelButton()) {
            button.getStyleClass().add("plain-action-button");
        }
        button.setDefaultButton(action.isDefaultButton());
        button.setCancelButton(action.isCancelButton());

        if (action.disabled() == null) {
            button.disableProperty().bind(entry.busy);
        } else {
            button.disableProperty().bind(entry.busy.or(Bindings.createBooleanBinding(
                    () -> Boolean.TRUE.equals(action.disabled().getValue()), action.disabled())));
        }

        button.setOnAction(event -> {
            if (entry.busy.get() || action.handler() == null) return;
            action.handler().accept(entry);
        });
        return button;
    }

    /**
     * 声明式弹窗的运行时状态。
     */
    private static final class DialogEntry implements DialogHandle {
        private final DialogOptions options;
        private final BooleanProperty busy = new SimpleBooleanProperty(false);
        private final AtomicReference<Object> result = new AtomicReference<>();
        private JFXDialog dialog;
        private Node contentNode;
        private volatile boolean showing;
        private volatile boolean closeRequested;

        private DialogEntry(DialogOptions options) {
            this.options = options;
        }

        @Override
        public void close() {
            if (Platform.isFxApplicationThread()) {
                closeNow();
            } else {
                Platform.runLater(this::closeNow);
            }
        }

        private void closeNow() {
            if (!showing || closeRequested) return;
            closeRequested = true;
            dialog.close();
        }

        @Override
        public boolean isShowing() {
            return showing && !closeRequested;
        }

        @Override
        public boolean isBusy() {
            return busy.get();
        }

        @Override
        public void setBusy(boolean busy) {
            if (Platform.isFxApplicationThread()) {
                this.busy.set(busy);
            } else {
                Platform.runLater(() -> this.busy.set(busy));
            }
        }

        @Override
        public void setResult(Object result) {
            this.result.set(result);
        }

        @Override
        public Object result() {
            return result.get();
        }

        @Override
        public Node content() {
            return contentNode;
        }

        private void notifyClosed() {
            if (!showing) return;
            showing = false;
            closeRequested = true;
            openDialogs.remove(this);
            if (options.onClosed() != null) {
                options.onClosed().accept(this);
            }
        }
    }

    /**
     * 展示弹窗并阻塞当前线程，直到用户点击任意按钮。
     * <p>
     * 底层用 {@link Platform#enterNestedEventLoop(Object)} 实现非冻结式等待。
     * 必须在 JavaFX Application Thread 调用。所有按钮都会触发关闭并返回。
     *
     * @param layout 弹窗内容，所有按钮都会触发关闭并返回
     * @return 被点击的按钮，null 表示通过遮罩层关闭
     */
    public static Button showAndWait(JFXDialogLayout layout) {
        JFXDialog dialog = createDialog(layout);
        ObjectProperty<Button> result = new SimpleObjectProperty<>();

        for (Node node : layout.getActions()) {
            if (node instanceof Button btn) {
                EventHandler<ActionEvent> original = btn.getOnAction();
                btn.setOnAction(e -> {
                    result.set(btn);
                    dialog.close();
                    if (original != null) original.handle(e);
                });
            }
        }

        dialog.addEventHandler(JFXDialogEvent.CLOSED, e ->
                Platform.exitNestedEventLoop(dialog, result.get()));

        dialog.show();
        Platform.enterNestedEventLoop(dialog);
        return result.get();
    }

    /**
     * 创建 JFXDialog 并设置容器、内容、动画、样式。
     */
    private static JFXDialog createDialog(Region content) {
        JFXDialog dialog = new JFXDialog();
        dialog.setDialogContainer(root);
        dialog.setContent(content);
        dialog.setTransitionType(JFXDialog.DialogTransition.CENTER);
        dialog.getStyleClass().add("common-alert");
        // 旧式 EventBus.dialog 也走这里，为其内容补上通用弹窗样式，
        // 使尚未迁移到 Dialogs 的弹窗获得与其他弹窗一致的按钮与表单外观
        if (!content.getStylesheets().contains(CssLoader.getCss(CssLoader.dialogCommon))) {
            content.getStylesheets().add(CssLoader.getCss(CssLoader.dialogCommon));
        }
        // 未显式标注外壳类的旧弹窗补一个 dialog-surface，套用统一背景与圆角
        if (content instanceof JFXDialogLayout layout
                && !layout.getStyleClass().contains("app-dialog")
                && !layout.getStyleClass().contains("jfx-alter")
                && !layout.getStyleClass().contains("dialog-surface")) {
            layout.getStyleClass().add("dialog-surface");
        }
        return dialog;
    }

    /**
     * 遍历按钮列表，为 cancelButton 绑定自动关闭逻辑。
     */
    private static void bindActions(Collection<Node> actions, JFXDialog dialog) {
        for (Node node : actions) {
            if (node instanceof Button btn && btn.isCancelButton()) {
                bindButton(btn, dialog);
            }
        }
    }

    /**
     * 绑定按钮：点击后先关闭弹窗，再执行原有 onAction。
     */
    private static void bindButton(Button btn, JFXDialog dialog) {
        EventHandler<ActionEvent> original = btn.getOnAction();
        btn.setOnAction(e -> {
            dialog.close();
            if (original != null) original.handle(e);
        });
    }

    // ==================== 消息卡片 ====================

    /**
     * 展示右上角消息卡片，支持滑入动画、自动关闭。
     */
    private void showMessage(MessageInfo info) {
        if (messagePane.getChildren().size() > 7) {
            messagePane.getChildren().removeFirst();
        }
        Message message = createMessage(info);
        message.setOnClose(e -> {
            var out = Animations.slideOutRight(message, Duration.millis(250));
            out.setOnFinished(f -> messagePane.getChildren().remove(message));
            out.playFromStart();
        });
        Platform.runLater(() -> {
            messagePane.getChildren().add(message);
            message.setTranslateX(300);
            Timeline timeline = new Timeline(new KeyFrame(Duration.millis(250), new KeyValue(message.translateXProperty(), 0)));
            timeline.play();
        });
        if (info.getAutoClose()) {
            Timeline autoClose = new Timeline(new KeyFrame(info.getShowTime(), new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    var out = Animations.slideOutRight(message, Duration.millis(250));
                    out.setOnFinished(f -> messagePane.getChildren().remove(message));
                    out.playFromStart();
                }
            }));
            autoClose.play();
        }
    }

    /**
     * 根据消息类型构建带图标与样式的 Message 控件。
     */
    private Message createMessage(MessageInfo messageInfo) {
        Message message = null;
        String title = messageInfo.getTitle() != null ? messageInfo.getTitle() : null;
        switch (messageInfo.getType()) {
            case SUCCESS -> {
                message = new Message(title, messageInfo.getMessage(), new FontIcon(Material2AL.CHECK_CIRCLE));
                message.getStyleClass().addAll(Styles.SUCCESS, "glass-message");
            }
            case WARNING -> {
                message = new Message(title, messageInfo.getMessage(), new FontIcon(Material2MZ.WARNING));
                message.getStyleClass().addAll(Styles.WARNING, "glass-message");
            }
            case INFO -> {
                message = new Message(title, messageInfo.getMessage(), new FontIcon(Material2AL.INFO));
                message.getStyleClass().addAll(Styles.ACCENT, "glass-message");
            }
            case ERROR -> {
                message = new Message(title, messageInfo.getMessage(), new FontIcon(Material2AL.HIGHLIGHT_OFF));
                message.getStyleClass().addAll(Styles.DANGER, "glass-message");
            }
        }
        message.setPrefSize(300.0, 60.0);
        message.setMaxSize(300.0, 80.0);
        return message;
    }
}
