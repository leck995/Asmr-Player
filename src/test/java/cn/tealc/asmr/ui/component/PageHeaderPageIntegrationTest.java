package cn.tealc.asmr.ui.component;

import atlantafx.base.theme.PrimerLight;
import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.mvvm.FluentViewLoader;
import cn.tealc.asmr.mvvm.ViewTuple;
import cn.tealc.asmr.ui.anywheredoor.AnywhereDoorView;
import cn.tealc.asmr.ui.anywheredoor.AnywhereDoorViewModel;
import cn.tealc.asmr.ui.collection.CollectionView;
import cn.tealc.asmr.ui.collection.CollectionViewModel;
import cn.tealc.asmr.ui.main.CategoriesView;
import cn.tealc.asmr.ui.main.CategoriesViewModel;
import cn.tealc.asmr.ui.library.MediaLibraryView;
import cn.tealc.asmr.ui.library.MediaLibraryViewModel;
import cn.tealc.asmr.ui.library.StorageSourceView;
import cn.tealc.asmr.ui.library.StorageSourceViewModel;
import cn.tealc.asmr.utils.CssLoader;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageHeaderPageIntegrationTest extends BaseDatabaseTest {

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
    void collectionPageUsesPageHeader() throws Exception {
        ViewTuple<CollectionView, CollectionViewModel> tuple =
                fx(() -> FluentViewLoader.fxmlView(CollectionView.class).load());
        attachAndSnapshot(tuple.getView(), "collection");
        assertHeader(tuple.getView(), "歌单");
    }

    @Test
    void collectionPageShowsEmptyStateWhenNoPlaylists() throws Exception {
        ViewTuple<CollectionView, CollectionViewModel> tuple =
                fx(() -> FluentViewLoader.fxmlView(CollectionView.class).load());
        attachAndSnapshot(tuple.getView(), "collection-empty");
        assertEmptyState(tuple.getView(), "暂无歌单");
    }

    @Test
    void anywhereDoorPageShowsEmptyStateWhenNoEntries() throws Exception {
        ViewTuple<AnywhereDoorView, AnywhereDoorViewModel> tuple =
                fx(() -> FluentViewLoader.fxmlView(AnywhereDoorView.class).load());
        attachAndSnapshot(tuple.getView(), "anywhere-door-empty");
        assertEmptyState(tuple.getView(), "暂无快捷入口");
    }

    @Test
    void mediaLibraryPageUsesPageHeader() throws Exception {
        ViewTuple<MediaLibraryView, MediaLibraryViewModel> tuple =
                fx(() -> FluentViewLoader.fxmlView(MediaLibraryView.class).load());
        attachAndSnapshot(tuple.getView(), "media-library");
        assertHeader(tuple.getView(), "管理");
        assertEmptyState(tuple.getView(), "暂无媒体库");
    }

    @Test
    void storageSourcePageShowsEmptyStateWhenNoSources() throws Exception {
        ViewTuple<StorageSourceView, StorageSourceViewModel> tuple =
                fx(() -> FluentViewLoader.javaView(StorageSourceView.class).load());
        attachAndSnapshot(tuple.getView(), "storage-source-empty");
        assertEmptyState(tuple.getView(), "暂无存储源");
    }

    @Test
    void categoriesPageUsesPageHeaderAndFlowsBelowIt() throws Exception {
        ViewTuple<CategoriesView, CategoriesViewModel> tuple =
                fx(() -> FluentViewLoader.javaView(CategoriesView.class).load());
        attachAndSnapshot(tuple.getView(), "categories");
        assertHeader(tuple.getView(), "分类浏览");
        fx(() -> {
            assertNotNull(tuple.getView().lookup(".category-content-header"));
            assertNotNull(tuple.getView().lookup(".category-scroll"));
            return null;
        });
    }

    private static void attachAndSnapshot(Node view, String name) throws Exception {
        fx(() -> {
            StackPane root = new StackPane(view);
            root.getStylesheets().add(new PrimerLight().getUserAgentStylesheet());
            root.getStylesheets().add(CssLoader.getCommonCss("/white-skin/atlantafx.css"));
            new Scene(root, 1000, 700);
            root.resize(1000, 700);
            root.applyCss();
            root.layout();

            String directory = System.getProperty("asmr.pageheader.screenshots");
            if (directory != null) {
                Path target = Path.of(directory);
                Files.createDirectories(target);
                ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png",
                        target.resolve(name + ".png").toFile());
            }
            return null;
        });
    }

    private static void assertHeader(Node root, String title) throws Exception {
        fx(() -> {
            Label titleLabel = (Label) root.lookup(".page-header-title");
            Label subtitleLabel = (Label) root.lookup(".page-header-subtitle");
            assertNotNull(titleLabel);
            assertNotNull(subtitleLabel);
            assertEquals(title, titleLabel.getText());
            assertTrue(subtitleLabel.getText() != null && !subtitleLabel.getText().isBlank());
            return null;
        });
    }

    private static void assertEmptyState(Node root, String title) throws Exception {
        fx(() -> {
            Node empty = root.lookup(".empty-state");
            assertNotNull(empty);
            assertTrue(empty.isVisible());
            Label titleLabel = (Label) empty.lookup(".empty-state-title");
            assertNotNull(titleLabel);
            assertEquals(title, titleLabel.getText());
            return null;
        });
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(12, TimeUnit.SECONDS);
    }
}
