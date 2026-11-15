package cn.tealc.asmr.ui.main;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.model.AnywhereDoor;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MainNavigationLayoutTest extends BaseDatabaseTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        onFxThread(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
    }

    @ParameterizedTest
    @CsvSource({"0, 540, false", "3, 720, false", "80, 720, false", "80, 540, true", "200, 1080, true"})
    void shortcutCountDoesNotExpandNavigation(int count, int height, boolean dark) throws Exception {
        onFxThread(() -> {
            MainView main = createNavigation(count);
            VBox nav = navigation(main);
            BorderPane root = layout(main, nav, height, dark);
            ScrollPane scroll = (ScrollPane) nav.getChildren().getLast();
            VBox shortcuts = (VBox) scroll.getContent();
            assertEquals(200, nav.getWidth(), 0.1);
            assertEquals(height, nav.getHeight(), 0.1);
            assertTrue(root.minHeight(-1) < height);
            assertTrue(root.prefHeight(-1) < height);
            assertTrue(scroll.getLayoutY() + scroll.getHeight() <= height + 0.1);
            assertEquals(ScrollPane.ScrollBarPolicy.NEVER, scroll.getHbarPolicy());
            for (var child : shortcuts.getChildren()) {
                assertTrue(child.getLayoutX() + child.getBoundsInParent().getWidth()
                        <= scroll.getViewportBounds().getWidth() + 0.1);
            }
            ScrollBar vertical = scroll.lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast)
                    .filter(bar -> bar.getOrientation() == Orientation.VERTICAL).findFirst().orElseThrow();
            assertNotNull(vertical);
            if (count >= 80) {
                assertTrue(vertical.isVisible());
                double firstY = shortcuts.localToScene(0, 0).getY();
                scroll.setVvalue(1);
                root.layout();
                assertTrue(shortcuts.localToScene(0, 0).getY() < firstY);
                var last = shortcuts.getChildren().getLast();
                assertTrue(last.localToScene(last.getBoundsInLocal()).getMaxY()
                        <= scroll.localToScene(scroll.getBoundsInLocal()).getMaxY() + 1);
            } else {
                assertFalse(vertical.isVisible());
            }
            return null;
        });
    }

    @Test
    void albumsButtonFollowsPlaylistsAndSharesNavigationGroup() throws Exception {
        onFxThread(() -> {
            VBox nav = navigation(createNavigation(0));
            ToggleButton playlists = toggle(nav, "歌单");
            ToggleButton albums = toggle(nav, "集册");
            assertEquals("歌单", playlists.getText());
            assertEquals("集册", albums.getText());
            assertSame(playlists.getToggleGroup(), albums.getToggleGroup());
            assertNotNull(albums.getGraphic());
            return null;
        });
    }

    /**
     * 按文字查找左侧导航按钮，避免直接用索引导致导航顺序调整后测试失效。
     */
    private static ToggleButton toggle(VBox nav, String text) {
        return nav.getChildren().stream()
                .filter(ToggleButton.class::isInstance)
                .map(ToggleButton.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到导航按钮: " + text));
    }

    @Test
    void refreshingShortcutsUpdatesScrollRangeWithoutExpandingNavigation() throws Exception {
        onFxThread(() -> {
            MainView main = createNavigation(3);
            VBox nav = navigation(main);
            BorderPane root = layout(main, nav, 540, false);
            ScrollPane scroll = (ScrollPane) nav.getChildren().getLast();
            double originalHeight = nav.getHeight();
            setShortcutCount(main, 100);
            root.applyCss();
            root.layout();
            assertEquals(originalHeight, nav.getHeight(), 0.1);
            assertTrue(scroll.getContent().getBoundsInLocal().getHeight() > scroll.getViewportBounds().getHeight());
            setShortcutCount(main, 1);
            root.applyCss();
            root.layout();
            assertEquals(originalHeight, nav.getHeight(), 0.1);
            assertTrue(scroll.getContent().getBoundsInLocal().getHeight() <= scroll.getViewportBounds().getHeight());
            return null;
        });
    }

    @Test
    void foldingAndResizingKeepMainButtonsVisible() throws Exception {
        onFxThread(() -> {
            MainView main = createNavigation(100);
            VBox nav = navigation(main);
            BorderPane root = layout(main, nav, 720, false);
            ToggleButton heading = nav.getChildren().stream().filter(ToggleButton.class::isInstance)
                    .map(ToggleButton.class::cast).filter(button -> "快捷入口".equals(button.getText())).findFirst().orElseThrow();
            ScrollPane scroll = (ScrollPane) nav.getChildren().getLast();
            double mainButtonsBottom = heading.getBoundsInParent().getMaxY();
            heading.fire();
            root.layout();
            assertFalse(scroll.isVisible());
            assertFalse(scroll.isManaged());
            heading.fire();
            root.resize(1280, 540);
            root.layout();
            assertTrue(scroll.isVisible());
            assertTrue(scroll.isManaged());
            assertEquals(mainButtonsBottom, heading.getBoundsInParent().getMaxY(), 0.1);
            assertTrue(scroll.getHeight() > 0);
            assertTrue(scroll.getBoundsInParent().getMaxY() <= 540.1);
            return null;
        });
    }

    private static MainView createNavigation(int count) throws Exception {
        MainView main = new MainView();
        var create = MainView.class.getDeclaredMethod("createLeftTiledPane");
        create.setAccessible(true);
        create.invoke(main);
        setShortcutCount(main, count);
        return main;
    }

    private static void setShortcutCount(MainView main, int count) throws Exception {
        var shortcuts = new ArrayList<AnywhereDoor>();
        for (int index = 0; index < count; index++) {
            AnywhereDoor shortcut = new AnywhereDoor("分类" + index, AsmrKeyType.CATEGORY,
                    index == 0 ? "很长的快捷入口别名".repeat(30) : "快捷入口 " + index);
            shortcut.setId(index + 1);
            shortcuts.add(shortcut);
        }
        var refresh = MainView.class.getDeclaredMethod("refreshAnywhereDoors", java.util.List.class, ToggleGroup.class);
        refresh.setAccessible(true);
        refresh.invoke(main, shortcuts, new ToggleGroup());
    }

    private static VBox navigation(MainView main) throws Exception {
        var field = MainView.class.getDeclaredField("navPane");
        field.setAccessible(true);
        return (VBox) field.get(main);
    }

    private static BorderPane layout(MainView main, VBox nav, int height, boolean dark) {
        BorderPane root = new BorderPane(new Pane());
        root.setLeft(nav);
        root.getStylesheets().add(dark ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        root.getStylesheets().addAll(main.getStylesheets());
        new Scene(root, 1280, height);
        root.applyCss();
        root.resize(1280, height);
        root.layout();
        return root;
    }

    private static <T> T onFxThread(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
