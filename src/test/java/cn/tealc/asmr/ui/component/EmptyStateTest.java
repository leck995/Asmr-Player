package cn.tealc.asmr.ui.component;

import atlantafx.base.theme.PrimerLight;
import cn.tealc.asmr.utils.CssLoader;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmptyStateTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        fx(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
    }

    @Test
    void showsTitleAndDescriptionAndHidesUnusedSlots() throws Exception {
        fx(() -> {
            EmptyState state = new EmptyState();
            state.setTitle("暂无作品");
            state.setDescription("导入媒体库后，作品会显示在这里。");
            attach(state);

            Label title = (Label) state.lookup(".empty-state-title");
            Label description = (Label) state.lookup(".empty-state-description");
            assertNotNull(title);
            assertNotNull(description);
            assertEquals("暂无作品", title.getText());
            assertEquals(Pos.CENTER, title.getAlignment());
            assertEquals(Pos.CENTER, description.getAlignment());
            assertEquals(state.getLayoutBounds().getWidth() / 2,
                    title.getBoundsInParent().getCenterX(), 1.0);
            assertEquals(state.getLayoutBounds().getWidth() / 2,
                    description.getBoundsInParent().getCenterX(), 1.0);
            assertTrue(title.isVisible());
            assertTrue(description.isVisible());
            assertFalse(state.lookup(".empty-state-graphic").isVisible());
            assertFalse(state.lookup(".empty-state-action").isVisible());

            state.setTitle("");
            state.setDescription("");
            state.getParent().applyCss();
            state.getParent().layout();
            assertFalse(title.isVisible());
            assertFalse(description.isVisible());
            return null;
        });
    }

    @Test
    void showsGraphicAndActionAndAppliesCompactStyle() throws Exception {
        fx(() -> {
            EmptyState state = new EmptyState();
            state.setIcon(Material2OutlinedMZ.MUSIC_OFF);
            state.setTitle("暂无作品");
            state.setAction(new Button("返回"));
            state.setCompact(true);
            attach(state);

            assertNotNull(state.getGraphic());
            assertTrue(state.lookup(".empty-state-graphic").isVisible());
            assertTrue(state.lookup(".empty-state-action").isVisible());
            assertTrue(state.getStyleClass().contains("compact"));

            state.setCompact(false);
            assertFalse(state.getStyleClass().contains("compact"));
            return null;
        });
    }

    private static void attach(EmptyState state) {
        StackPane root = new StackPane(state);
        root.getStylesheets().add(new PrimerLight().getUserAgentStylesheet());
        root.getStylesheets().add(CssLoader.getCommonCss("/white-skin/atlantafx.css"));
        new Scene(root, 500, 300);
        root.applyCss();
        root.layout();
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(12, TimeUnit.SECONDS);
    }
}
