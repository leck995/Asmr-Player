package cn.tealc.asmr.ui.dialog;

import cn.tealc.asmr.ui.component.OverlayLayer;
import cn.tealc.asmr.ui.component.PageHeader;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertsTest {

    private static StackPane root;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException running) {
            Platform.runLater(latch::countDown);
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        fx(() -> {
            root = new StackPane();
            new OverlayLayer(root);
            return null;
        });
    }

    @BeforeEach
    void setUp() throws Exception {
        fx(() -> {
            root.getChildren().clear();
            return null;
        });
    }

    @Test
    void typeStylesHeadingAndButtonsArePreset() throws Exception {
        DialogHandle handle = fx(() -> Alerts.warning()
                .title("提醒")
                .message("确认继续吗？")
                .ok("继续", h -> { })
                .cancel()
                .show());
        JFXDialogLayout layout = (JFXDialogLayout) fx(handle::content);

        assertTrue(layout.getStyleClass().contains("jfx-alter"));
        assertTrue(layout.getStyleClass().contains("warning"));
        Node heading = layout.getHeading().getFirst();
        assertTrue(heading instanceof PageHeader);
        assertEquals("提醒", ((PageHeader) heading).getTitle());

        Button ok = action(layout, "继续");
        assertTrue(ok.isDefaultButton());
        assertTrue(ok.getStyleClass().contains("default-button"));

        Button cancel = action(layout, "取消");
        assertTrue(cancel.isCancelButton());
        assertTrue(cancel.getStyleClass().contains("cancel-button"));

        fx(() -> {
            ok.fire();
            return null;
        });
        assertFalse(fx(handle::isShowing));
    }

    @Test
    void okRunsHandlerAndContentIsComposedWithMessage() throws Exception {
        AtomicBoolean ran = new AtomicBoolean();
        Label marker = new Label("简单控件");
        DialogHandle handle = fx(() -> Alerts.basic()
                .title("标题")
                .message("说明")
                .content(marker)
                .ok("确定", h -> ran.set(true))
                .show());
        JFXDialogLayout layout = (JFXDialogLayout) fx(handle::content);

        VBox body = (VBox) layout.getBody().getFirst();
        assertEquals("说明", ((Label) body.getChildren().getFirst()).getText());
        assertSame(marker, body.getChildren().getLast());

        fx(() -> {
            action(layout, "确定").fire();
            return null;
        });
        assertTrue(ran.get());
        assertFalse(fx(handle::isShowing));
    }

    @Test
    void customButtonDoesNotCloseByDefault() throws Exception {
        DialogHandle handle = fx(() -> Alerts.basic()
                .title("提示")
                .button("稍后", h -> { })
                .cancel()
                .show());
        JFXDialogLayout layout = (JFXDialogLayout) fx(handle::content);

        fx(() -> {
            action(layout, "稍后").fire();
            return null;
        });
        assertTrue(fx(handle::isShowing));

        fx(() -> {
            action(layout, "取消").fire();
            return null;
        });
        assertFalse(fx(handle::isShowing));
    }

    @Test
    void stylesheetAndStyledButtonAreApplied() throws Exception {
        DialogHandle handle = fx(() -> Alerts.danger()
                .title("删除")
                .stylesheet("test-alert.css")
                .button("删除", "default-button", h -> { })
                .cancel()
                .show());
        JFXDialogLayout layout = (JFXDialogLayout) fx(handle::content);

        assertTrue(layout.getStylesheets().contains("test-alert.css"));
        Button remove = action(layout, "删除");
        assertTrue(remove.getStyleClass().contains("default-button"));

        fx(() -> {
            remove.fire();
            return null;
        });
        assertTrue(fx(handle::isShowing));

        fx(() -> {
            handle.close();
            return null;
        });
    }

    private static Button action(JFXDialogLayout layout, String text) {
        return layout.getActions().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到按钮: " + text));
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(12, TimeUnit.SECONDS);
    }
}
