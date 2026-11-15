package cn.tealc.asmr.ui.component;

import atlantafx.base.theme.PrimerLight;
import cn.tealc.asmr.utils.CssLoader;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.material2.Material2OutlinedAL;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageHeaderTest {

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
    void supportsTitleOnlySeparatorAndSlotOptions() throws Exception {
        fx(() -> {
            PageHeader header = new PageHeader();
            header.setIcon(Material2OutlinedAL.CATEGORY);
            header.setTitle("分类浏览");
            header.setSubtitle("按类别、声优、标签或社团筛选作品。");
            header.setActions(new Button("操作"));

            StackPane root = new StackPane(header);
            root.getStylesheets().add(new PrimerLight().getUserAgentStylesheet());
            root.getStylesheets().add(CssLoader.getCommonCss("/white-skin/atlantafx.css"));
            new Scene(root, 800, 200);
            root.applyCss();
            root.layout();

            Label title = (Label) header.lookup(".page-header-title");
            Label subtitle = (Label) header.lookup(".page-header-subtitle");
            assertNotNull(title);
            assertNotNull(subtitle);
            assertEquals("分类浏览", title.getText());
            assertTrue(subtitle.isVisible());
            javafx.scene.Node iconBox = header.lookup(".page-header-icon-box");
            Bounds iconBounds = iconBox.localToScene(iconBox.getBoundsInLocal());
            Bounds titleBounds = title.localToScene(title.getBoundsInLocal());
            Bounds subtitleBounds = subtitle.localToScene(subtitle.getBoundsInLocal());
            assertTrue(Math.abs(iconBounds.getCenterY() - titleBounds.getCenterY()) < 4);
            assertTrue(Math.abs(subtitleBounds.getMinX() - iconBounds.getMinX()) < 2);
            double fullHeight = header.prefHeight(-1);

            header.setTitleOnly(true);
            root.layout();
            assertFalse(subtitle.isVisible());
            assertFalse(subtitle.isManaged());
            assertTrue(header.prefHeight(-1) < fullHeight);

            javafx.scene.Node separator = header.lookup(".page-header-separator");
            assertNotNull(separator);
            assertTrue(separator.isVisible());
            assertEquals(header.getWidth() - header.getInsets().getLeft() - header.getInsets().getRight(),
                    separator.getBoundsInParent().getWidth(), 1);
            header.setSeparatorVisible(false);
            assertFalse(separator.isVisible());
            assertFalse(separator.isManaged());

            header.setIconVisible(false);
            assertFalse(iconBox.isVisible());
            assertFalse(iconBox.isManaged());

            header.setActions(null);
            javafx.scene.Node actions = header.lookup(".page-header-actions");
            assertFalse(actions.isVisible());
            assertFalse(actions.isManaged());

            header.setLeading(new Button("返回"));
            assertTrue(header.lookup(".page-header-leading").isVisible());

            header.setExtra(new Label("额外内容"));
            assertTrue(header.lookup(".page-header-extra").isVisible());

            header.setCompact(true);
            assertTrue(header.getStyleClass().contains("compact"));

            header.setTitle("临时标题");
            assertEquals("临时标题", title.getText());
            return null;
        });
    }

    @Test
    void subtitleExtraSitsAfterSubtitleAndHidesWithTitleOnly() throws Exception {
        fx(() -> {
            PageHeader header = new PageHeader();
            header.setTitle("作品集");
            header.setSubtitle("12 个作品");
            Label description = new Label("一段较长的作品集描述");
            description.getStyleClass().add("page-header-subtitle");
            header.setSubtitleExtra(description);

            StackPane root = new StackPane(header);
            root.getStylesheets().add(new PrimerLight().getUserAgentStylesheet());
            root.getStylesheets().add(CssLoader.getCommonCss("/white-skin/atlantafx.css"));
            new Scene(root, 480, 120);
            root.applyCss();
            root.layout();

            Label subtitle = (Label) header.lookup(".page-header-subtitle");
            javafx.scene.Node extra = header.lookup(".page-header-subtitle-extra");
            assertNotNull(extra);
            assertTrue(description.isVisible());
            assertTrue(extra.isVisible());
            Bounds subtitleBounds = subtitle.localToScene(subtitle.getBoundsInLocal());
            Bounds descriptionBounds = description.localToScene(description.getBoundsInLocal());
            assertTrue(descriptionBounds.getMinX() >= subtitleBounds.getMaxX());

            header.setTitleOnly(true);
            root.layout();
            assertFalse(subtitle.isVisible());
            assertFalse(extra.isVisible());

            header.setTitleOnly(false);
            header.setSubtitleExtra(null);
            root.layout();
            assertFalse(extra.isVisible());
            assertFalse(extra.isManaged());
            return null;
        });
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(12, TimeUnit.SECONDS);
    }
}
