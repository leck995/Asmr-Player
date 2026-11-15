package cn.tealc.asmr.ui.dialog;

import cn.tealc.asmr.task.background.TaskManager;
import cn.tealc.asmr.ui.component.OverlayLayer;
import cn.tealc.asmr.ui.component.PageHeader;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogsTest {

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
            Platform.setImplicitExit(false);
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

    @AfterEach
    void tearDown() throws Exception {
        fx(() -> {
            root.getChildren().clear();
            return null;
        });
    }

    @Test
    void overlayClickClosesByDefaultAndCanBeDisabled() {
        assertTrue(DialogSpec.builder().build().options().overlayClickCloses());
        assertFalse(DialogSpec.builder().overlayClickCloses(false).build().options().overlayClickCloses());
    }

    @Test
    void taskListDialogShowsEmptyStateWhenNoTasks() throws Exception {
        fx(() -> {
            new TaskListDialog(new TaskManager()).show();
            return null;
        });
        fx(() -> {
            Node empty = root.lookup(".empty-state");
            assertNotNull(empty);
            assertTrue(empty.isVisible());
            Label title = (Label) empty.lookup(".empty-state-title");
            assertNotNull(title);
            assertEquals("当前没有后台任务", title.getText());
            return null;
        });
    }

    @Test
    void actionsDoNotCloseUntilHandleCloses() throws Exception {
        DialogHandle handle = fx(() -> Dialogs.show(DialogSpec.builder()
                .title("测试")
                .action(DialogAction.of("确定", h -> h.setResult("ok")))
                .build()));

        Button ok = fx(() -> button(handle, "确定"));
        fx(() -> {
            ok.fire();
            return null;
        });

        assertTrue(fx(handle::isShowing));
        assertEquals("ok", fx(handle::result));

        fx(() -> {
            handle.close();
            return null;
        });
        assertFalse(fx(handle::isShowing));
    }

    @Test
    void cancelActionClosesAndNotifiesOnce() throws Exception {
        AtomicInteger closed = new AtomicInteger();
        CountDownLatch closedLatch = new CountDownLatch(1);
        DialogHandle handle = fx(() -> Dialogs.show(DialogSpec.builder()
                .title("测试")
                .cancel("取消")
                .onClosed(h -> {
                    closed.incrementAndGet();
                    closedLatch.countDown();
                })
                .build()));

        Button cancel = fx(() -> button(handle, "取消"));
        fx(() -> {
            cancel.fire();
            return null;
        });

        assertFalse(fx(handle::isShowing));
        assertTrue(closedLatch.await(3, TimeUnit.SECONDS));
        fx(() -> {
            handle.close();
            return null;
        });
        assertEquals(1, closed.get());
    }

    @Test
    void busyDisablesActionsAndBlocksEscape() throws Exception {
        AtomicBoolean fired = new AtomicBoolean();
        DialogHandle handle = fx(() -> Dialogs.show(DialogSpec.builder()
                .title("测试")
                .action("执行", h -> fired.set(true))
                .build()));

        Button action = fx(() -> button(handle, "执行"));
        fx(() -> {
            handle.setBusy(true);
            return null;
        });

        assertTrue(fx(action::isDisabled));
        fx(() -> {
            root.fireEvent(escape());
            return null;
        });
        assertTrue(fx(handle::isShowing));
        assertFalse(fired.get());

        fx(() -> {
            handle.setBusy(false);
            return null;
        });
        assertFalse(fx(action::isDisabled));
        fx(() -> {
            root.fireEvent(escape());
            return null;
        });
        assertFalse(fx(handle::isShowing));
    }

    @Test
    void escapeCanBeDisabled() throws Exception {
        DialogHandle handle = fx(() -> Dialogs.show(DialogSpec.builder()
                .title("测试")
                .escapeCloses(false)
                .build()));

        fx(() -> {
            root.fireEvent(escape());
            return null;
        });
        assertTrue(fx(handle::isShowing));

        fx(() -> {
            handle.close();
            return null;
        });
    }

    @Test
    void disabledWhenIsCombinedWithBusy() throws Exception {
        SimpleBooleanProperty ready = new SimpleBooleanProperty(false);
        DialogHandle handle = fx(() -> Dialogs.show(DialogSpec.builder()
                .title("测试")
                .action(DialogAction.of("下一步", h -> { }).disabledWhen(ready.not()))
                .build()));

        Button action = fx(() -> button(handle, "下一步"));
        assertTrue(fx(action::isDisabled));

        fx(() -> {
            ready.set(true);
            return null;
        });
        assertFalse(fx(action::isDisabled));

        fx(() -> {
            handle.setBusy(true);
            return null;
        });
        assertTrue(fx(action::isDisabled));

        fx(() -> {
            handle.setBusy(false);
            handle.close();
            return null;
        });
    }

    @Test
    void titleRendersAsPageHeader() throws Exception {
        DialogHandle handle = fx(() -> Dialogs.show(DialogSpec.builder()
                .title("编辑歌单")
                .build()));

        JFXDialogLayout layout = (JFXDialogLayout) fx(handle::content);
        Node heading = layout.getHeading().getFirst();
        assertTrue(heading instanceof PageHeader);
        assertEquals("编辑歌单", ((PageHeader) heading).getTitle());
        assertTrue(heading.getStyleClass().contains("dialog-page-header"));
        assertFalse(((PageHeader) heading).isSeparatorVisible());

        fx(() -> {
            handle.close();
            return null;
        });
    }

    @Test
    void dialogPageMapsHeaderBodyAndActionsToSlots() throws Exception {
        AtomicInteger shown = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        CountDownLatch closedLatch = new CountDownLatch(1);
        AtomicReference<DialogHandle> attached = new AtomicReference<>();

        PageHeader header = new PageHeader();
        header.setIcon(Material2OutlinedMZ.MOVE_TO_INBOX);
        header.setTitle("移动作品");
        header.setSubtitle("选择目标目录后移动");
        Label body = new Label("正文");

        DialogPage page = new DialogPage() {
            @Override
            public PageHeader header() {
                return header;
            }

            @Override
            public Node content() {
                return body;
            }

            @Override
            public List<DialogAction> actions() {
                return List.of(DialogAction.cancel("关闭"));
            }

            @Override
            public void onDialogAttached(DialogHandle handle) {
                attached.set(handle);
            }

            @Override
            public void onDialogShown() {
                shown.incrementAndGet();
            }

            @Override
            public void onDialogClosed() {
                closed.incrementAndGet();
                closedLatch.countDown();
            }
        };

        DialogHandle handle = fx(() -> Dialogs.show(page));

        assertSame(handle, attached.get());
        assertTrue(fx(handle::isShowing));
        assertEquals(1, shown.get());

        JFXDialogLayout layout = (JFXDialogLayout) fx(handle::content);
        assertSame(header, layout.getHeading().getFirst());
        assertSame(body, layout.getBody().getFirst());
        assertEquals("关闭", ((Button) layout.getActions().getFirst()).getText());
        assertTrue(header.getStyleClass().contains("dialog-page-header"));
        assertFalse(header.isSeparatorVisible());

        fx(() -> {
            handle.setBusy(true);
            return null;
        });
        fx(() -> {
            root.fireEvent(escape());
            return null;
        });
        assertTrue(fx(handle::isShowing));
        fx(() -> {
            handle.setBusy(false);
            return null;
        });

        fx(() -> {
            ((Button) layout.getActions().getFirst()).fire();
            return null;
        });
        assertFalse(fx(handle::isShowing));
        assertTrue(closedLatch.await(3, TimeUnit.SECONDS));
        assertEquals(1, closed.get());
    }

    private static Button button(DialogHandle handle, String text) {
        JFXDialogLayout layout = (JFXDialogLayout) handle.content();
        return layout.getActions().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到按钮: " + text));
    }

    private static KeyEvent escape() {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                false, false, false, false);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(12, TimeUnit.SECONDS);
    }
}
