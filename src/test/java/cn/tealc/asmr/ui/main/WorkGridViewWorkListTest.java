package cn.tealc.asmr.ui.main;

import atlantafx.base.theme.PrimerLight;
import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.JdbcUtils;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.mvvm.FluentViewLoader;
import cn.tealc.asmr.mvvm.ViewTuple;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.ui.navigation.Route;
import cn.tealc.asmr.ui.navigation.RouteView;
import cn.tealc.asmr.ui.navigation.WorkSource;
import cn.tealc.asmr.ui.navigation.WorksRoute;
import cn.tealc.asmr.utils.CssLoader;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WorkGridViewWorkListTest extends BaseDatabaseTest {

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
    void loadsAllAndFavoriteSourcesAndRemovesUnfavoritedWork() throws Exception {
        AsmrWork favorite = insertWork("RJ100", "收藏作品", true);
        insertWork("RJ101", "普通作品", false);
        WorkGridView grid = attach();

        fx(() -> {
            grid.applySource(WorkSource.all());
            return null;
        });
        await(() -> grid.lookupAll(".work-cell").size() == 2);
        assertEquals("全部", fx(() -> ((Label) grid.lookup(".page-header-title")).getText()));
        assertTrue(fx(() -> ((Label) grid.lookup(".page-header-subtitle")).getText().contains("共 2 个作品")));
        Thread.sleep(150);
        fx(() -> {
            root.applyCss();
            root.layout();
            snapshot("all");
            return null;
        });

        fx(() -> {
            grid.applySource(WorkSource.favorites());
            return null;
        });
        await(() -> grid.lookupAll(".work-cell").size() == 1);
        assertEquals("收藏", fx(() -> ((Label) grid.lookup(".page-header-title")).getText()));
        Thread.sleep(150);
        fx(() -> {
            root.applyCss();
            root.layout();
            snapshot("favorites");
            assertHeaderLayout();
            return null;
        });

        fx(() -> {
            favorite.setFavorites(false);
            EventBus.publish(NotificationKey.MAIN_GRID_WORK_UPDATE, favorite);
            return null;
        });
        await(() -> grid.lookupAll(".work-cell").isEmpty());
    }

    @Test
    void pagerOnlyAppearsAtBottom() throws Exception {
        for (int index = 0; index < 12; index++) {
            insertWork("RJ2" + index, "作品 " + index, index % 2 == 0);
        }
        int previousPageSize = Config.setting.gridListCount.get();
        Config.setting.gridListCount.set(8);
        try {
            WorkGridView grid = attach();
            fx(() -> {
                grid.applySource(WorkSource.all());
                return null;
            });
            await(() -> grid.lookupAll(".work-cell").size() == 8);

            Node pager = fx(() -> grid.lookup(".pagination-bar"));
            assertNotNull(pager);
            assertFalse(fx(pager::isVisible));

            fx(() -> {
                ScrollPane scroll = (ScrollPane) grid.lookup(".scroll-pane");
                assertNotNull(scroll);
                scroll.setVvalue(1);
                root.applyCss();
                root.layout();
                return null;
            });
            await(pager::isVisible);
            fx(() -> {
                root.applyCss();
                root.layout();
                snapshot("pager");
                return null;
            });
        } finally {
            Config.setting.gridListCount.set(previousPageSize);
        }
    }

    @Test
    void nonAllWorksRoutesCreateSeparatePageInstances() throws Exception {
        WorkGridView shared = attach();
        MainView mainUI = fx(MainView::new);
        var createRouteView = MainView.class.getDeclaredMethod("createRouteView", Route.class, WorkGridView.class);
        createRouteView.setAccessible(true);

        RouteView all = fx(() -> (RouteView) createRouteView.invoke(
                mainUI, new WorksRoute(WorkSource.all()), shared));
        RouteView favorite = fx(() -> (RouteView) createRouteView.invoke(
                mainUI, new WorksRoute(WorkSource.favorites()), shared));

        assertSame(shared, all.node());
        assertNotSame(shared, favorite.node());
    }

    private WorkGridView attach() throws Exception {
        WorkGridView grid = fx(() -> {
            ViewTuple<WorkGridView, WorkGridViewModel> tuple =
                    FluentViewLoader.javaView(WorkGridView.class).load();
            return (WorkGridView) tuple.getView();
        });
        return fx(() -> {
            root = new StackPane(grid);
            root.getStylesheets().add(new PrimerLight().getUserAgentStylesheet());
            root.getStylesheets().add(CssLoader.getCommonCss("/white-skin/atlantafx.css"));
            new Scene(root, 1000, 700);
            root.resize(1000, 700);
            root.applyCss();
            root.layout();
            return grid;
        });
    }

    private AsmrWork insertWork(String code, String title, boolean favorites) throws Exception {
        try (var connection = JdbcUtils.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO asmr_works(work_id,title,filepath,storage_source_id,favorites) VALUES (?,?,?,1,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            statement.setString(2, title);
            statement.setString(3, "/test/" + code);
            statement.setBoolean(4, favorites);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                AsmrWork work = new AsmrWorkDao().getWorkById(keys.getInt(1));
                assertEquals(favorites, work.getFavorites());
                return work;
            }
        }
    }

    private void assertHeaderLayout() {
        Node header = root.lookup(".work-page-header");
        Node list = root.lookup(".work-page-list");
        Node heading = root.lookup(".page-header-title");
        Node description = root.lookup(".page-header-subtitle");
        Node actions = root.lookup(".page-header-actions");
        Node pagination = root.lookup(".pagination-bar");
        Node scrollContent = root.lookup(".work-list-content");
        Node cards = root.lookup(".work-list-grid");
        assertNotNull(header);
        assertNotNull(list);
        assertNotNull(heading);
        assertNotNull(description);
        assertNotNull(actions);
        assertNotNull(pagination);
        assertNotNull(scrollContent);
        assertNotNull(cards);

        Bounds headerBounds = header.localToScene(header.getBoundsInLocal());
        Bounds listBounds = list.localToScene(list.getBoundsInLocal());
        Bounds cardsBounds = cards.localToScene(cards.getBoundsInLocal());
        Bounds actionsBounds = actions.localToScene(actions.getBoundsInLocal());
        assertTrue(headerBounds.getHeight() >= 60);
        // 页头现在位于滚动内容内部，处于卡片上方
        assertSame(scrollContent, header.getParent());
        assertTrue(listBounds.getMinY() <= headerBounds.getMinY() + 0.5);
        assertTrue(cardsBounds.getMinY() >= headerBounds.getMaxY() - 0.5);
        assertTrue(heading.isVisible() && heading.getBoundsInParent().getWidth() > 0);
        assertTrue(description.isVisible() && description.getBoundsInParent().getWidth() > 0);
        assertTrue(actionsBounds.getMaxX() <= headerBounds.getMaxX() + 0.5);
        assertTrue(actionsBounds.getHeight() >= 32);
        assertFalse(pagination.isVisible());
    }

    @Test
    void headerScrollsAwayWithTheWorkList() throws Exception {
        for (int index = 0; index < 12; index++) {
            insertWork("RJ3" + index, "作品 " + index, index % 2 == 0);
        }
        int previousPageSize = Config.setting.gridListCount.get();
        Config.setting.gridListCount.set(8);
        try {
            WorkGridView grid = attach();
            fx(() -> {
                grid.applySource(WorkSource.all());
                return null;
            });
            await(() -> grid.lookupAll(".work-cell").size() == 8);

            Node header = fx(() -> grid.lookup(".work-page-header"));
            assertNotNull(header);
            double headerTopAtStart = fx(() -> header.localToScene(header.getBoundsInLocal()).getMinY());
            fx(() -> {
                ScrollPane scroll = (ScrollPane) grid.lookup(".scroll-pane");
                assertNotNull(scroll);
                scroll.setVvalue(1);
                root.applyCss();
                root.layout();
                return null;
            });
            double headerTopAfterScroll = fx(() -> header.localToScene(header.getBoundsInLocal()).getMinY());
            // 页头随内容上移，滚到底部时完全离开视口
            assertTrue(headerTopAfterScroll < headerTopAtStart,
                    "页头应随列表一起向上滚动：" + headerTopAfterScroll + " < " + headerTopAtStart);
            assertTrue(headerTopAfterScroll + header.getLayoutBounds().getHeight() <= headerTopAtStart,
                    "滚到底部时页头应移出视口");
        } finally {
            Config.setting.gridListCount.set(previousPageSize);
        }
    }

    private void snapshot(String name) throws Exception {
        String directory = System.getProperty("asmr.grid.screenshots");
        if (directory == null) {
            return;
        }
        Path target = Path.of(directory);
        Files.createDirectories(target);
        ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png",
                target.resolve(name + ".png").toFile());
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
        fail("等待作品列表更新超时");
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(12, TimeUnit.SECONDS);
    }
}
