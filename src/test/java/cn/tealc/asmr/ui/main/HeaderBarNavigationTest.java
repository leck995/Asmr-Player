package cn.tealc.asmr.ui.main;

import atlantafx.base.theme.PrimerLight;
import cn.tealc.asmr.utils.CssLoader;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderBarNavigationTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        System.setProperty("javafx.enablePreview", "true");
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
    void globalNavigationButtonsUseHeaderStyles() throws Exception {
        fx(() -> {
            HeaderBar header = new HeaderBar();
            header.getStyleClass().add("headbar");

            Button back = navButton(MaterialDesignC.CHEVRON_LEFT, "后退");
            Button forward = navButton(MaterialDesignC.CHEVRON_RIGHT, "前进");
            forward.setDisable(true);
            HBox nav = new HBox(2, back, forward);
            nav.getStyleClass().add("header-nav");
            nav.setAlignment(Pos.CENTER_LEFT);
            nav.setPickOnBounds(false);

            Button settings = new Button(null, new FontIcon(Material2OutlinedMZ.SETTINGS));
            Button tools = new Button(null, new FontIcon(Material2OutlinedAL.BUILD));
            HBox trailing = new HBox(8, settings, tools);
            trailing.getStyleClass().add("trailing");
            trailing.setAlignment(Pos.CENTER_RIGHT);
            header.setRight(trailing);

            HBox navHost = new HBox(nav);
            navHost.setAlignment(Pos.CENTER_LEFT);
            navHost.setPadding(new javafx.geometry.Insets(0, 0, 0, 20));
            navHost.setPickOnBounds(false);
            header.setCenter(navHost);
            HeaderBar.setAlignment(navHost, Pos.CENTER_LEFT);

            Region icon = new Region();
            icon.getStyleClass().add("icon");
            Text title = new Text("未来道具10号");
            title.getStyleClass().add("title");
            HBox leading = new HBox(icon, title);
            leading.getStyleClass().add("leading");
            leading.setAlignment(Pos.CENTER_LEFT);
            header.setLeft(leading);

            StackPane root = new StackPane(header);
            root.getStylesheets().add(new PrimerLight().getUserAgentStylesheet());
            root.getStylesheets().add(CssLoader.getCommonCss("/white-skin/atlantafx.css"));
            root.getStylesheets().add(CssLoader.getCss(CssLoader.main));
            new Scene(root, 1000, 72);
            root.resize(1000, 72);
            root.applyCss();
            root.layout();

            assertEquals(35, back.getWidth(), 1);
            assertEquals(35, back.getHeight(), 1);
            assertTrue(back.getStyleClass().contains("header-nav-btn"));
            assertTrue(nav.getWidth() <= nav.prefWidth(-1) + 1);
            assertTrue(!nav.isPickOnBounds());
            assertTrue(!navHost.isPickOnBounds());
            var titleBounds = title.localToScene(title.getBoundsInLocal());
            var backBounds = back.localToScene(back.getBoundsInLocal());
            assertTrue(backBounds.getMinX() > titleBounds.getMaxX());

            Path target = Path.of(System.getProperty("asmr.header.screenshots", "target/header-shots"));
            Files.createDirectories(target);
            ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png",
                    target.resolve("header-nav.png").toFile());
            return null;
        });
    }

    private static Button navButton(org.kordamp.ikonli.Ikon icon, String tooltip) {
        Button button = new Button(null, new FontIcon(icon));
        button.getStyleClass().add("header-nav-btn");
        button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        return button;
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(12, TimeUnit.SECONDS);
    }
}
