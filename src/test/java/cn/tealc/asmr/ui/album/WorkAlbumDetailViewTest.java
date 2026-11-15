package cn.tealc.asmr.ui.album;

import atlantafx.base.theme.PrimerLight;
import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.JdbcUtils;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.service.WorkAlbumService;
import cn.tealc.asmr.utils.CssLoader;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.geometry.Bounds;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WorkAlbumDetailViewTest extends BaseDatabaseTest {

    private StackPane root;

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

    @BeforeEach
    void bindServices() {
        DI.bind(WorkDao.class, AsmrWorkDao::new);
    }

    @Test
    void rendersAlbumMetadataWorksAndWorkListControls() throws Exception {
        var service = DI.get(WorkAlbumService.class);
        var album = service.create("夜晚的作品集", "这是一段作品集描述。");
        var work = insertWork("RJ100", "雨夜的故事");
        service.addWorks(List.of(album.id()), List.of(work.getId()));

        var view = attach(fx(() -> new WorkAlbumDetailView(album.id())));
        await(() -> view.lookupAll(".work-cell").size() == 1);
        await(() -> {
            Label title = (Label) view.lookup(".page-header-title");
            return title != null && "夜晚的作品集".equals(title.getText());
        });

        fx(() -> {
            Label title = (Label) view.lookup(".page-header-title");
            Label subtitle = (Label) view.lookup(".page-header-subtitle");
            Label description = (Label) view.lookup(".album-subtitle-description");
            TextField search = (TextField) view.lookup(".text-field");
            assertEquals("夜晚的作品集", title.getText());
            assertEquals("1 个作品", subtitle.getText());
            assertNotNull(description);
            assertEquals("这是一段作品集描述。", description.getText());
            assertNotNull(view.lookup(".album-subtitle-separator"));
            assertNotNull(view.lookup(".tool-sort-btn"));
            assertNotNull(view.lookup(".tool-view-btn"));
            assertNotNull(view.lookup(".tool-sync-btn"));
            assertNotNull(search);
            assertFalse(hasStyleClass(search, "grid-toolbar"));

            HBox actionHost = (HBox) view.lookup(".page-header-actions");
            HBox actions = (HBox) actionHost.getChildren().getFirst();
            assertTrue(actions.getChildren().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .noneMatch(button -> "添加作品".equals(button.getText())));
            Button edit = (Button) actions.getChildren().getLast();
            assertEquals("修改作品集", edit.getTooltip().getText());

            root.applyCss();
            root.layout();
            Bounds subtitleBounds = subtitle.localToScene(subtitle.getBoundsInLocal());
            Bounds descriptionBounds = description.localToScene(description.getBoundsInLocal());
            assertTrue(descriptionBounds.getMinX() >= subtitleBounds.getMaxX());
            snapshot("detail");
            return null;
        });
    }

    @Test
    void viewToggleSwitchesBetweenSmallAndBigCards() throws Exception {
        var service = DI.get(WorkAlbumService.class);
        var album = service.create("视图切换", "");
        var work = insertWork("RJ101", "视图切换作品");
        service.addWorks(List.of(album.id()), List.of(work.getId()));

        boolean previous = Config.setting.gridItemDetailModel.get();
        Config.setting.gridItemDetailModel.set(false);
        try {
            var view = attach(fx(() -> new WorkAlbumDetailView(album.id())));
            await(() -> view.lookupAll(".small-work-cell").size() == 1);

            fx(() -> {
                Button viewButton = (Button) view.lookup(".tool-view-btn");
                assertNotNull(viewButton);
                viewButton.fire();
                return null;
            });
            await(() -> view.lookupAll(".big-work-cell").size() == 1);
            assertTrue(Config.setting.gridItemDetailModel.get());
        } finally {
            Config.setting.gridItemDetailModel.set(previous);
        }
    }

    private WorkAlbumDetailView attach(WorkAlbumDetailView view) throws Exception {
        return fx(() -> {
            root = new StackPane(view);
            root.getStylesheets().add(new PrimerLight().getUserAgentStylesheet());
            root.getStylesheets().add(CssLoader.getCommonCss("/white-skin/atlantafx.css"));
            new Scene(root, 1000, 700);
            root.resize(1000, 700);
            root.applyCss();
            root.layout();
            return view;
        });
    }

    private void snapshot(String name) throws Exception {
        Path target = Path.of(System.getProperty("asmr.album.detail.screenshots", "target/album-detail-shots"));
        Files.createDirectories(target);
        ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png",
                target.resolve(name + ".png").toFile());
    }

    private AsmrWork insertWork(String code, String title) throws Exception {
        try (var connection = JdbcUtils.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO asmr_works(work_id,title,filepath,storage_source_id) VALUES (?,?,?,1)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            statement.setString(2, title);
            statement.setString(3, "/detail/" + code);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                return new AsmrWorkDao().getWorkById(keys.getInt(1));
            }
        }
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < deadline) {
            if (fx(() -> {
                if (root != null) {
                    root.applyCss();
                    root.layout();
                }
                return condition.getAsBoolean();
            })) {
                return;
            }
            Thread.sleep(20);
        }
        fail("等待作品集详情加载超时");
    }

    private static boolean hasStyleClass(Node node, String styleClass) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current.getStyleClass().contains(styleClass)) {
                return true;
            }
        }
        return false;
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(12, TimeUnit.SECONDS);
    }
}
