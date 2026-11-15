package cn.tealc.asmr.ui.album;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.JdbcUtils;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.service.WorkAlbumService;
import cn.tealc.asmr.ui.component.OverlayLayer;
import cn.tealc.asmr.ui.work.cells.SmallWorkCell;
import cn.tealc.asmr.ui.dialog.DialogHandle;
import cn.tealc.asmr.ui.navigation.AlbumDetailRoute;
import cn.tealc.asmr.ui.navigation.NavigationService;
import cn.tealc.asmr.ui.navigation.RouteView;
import cn.tealc.asmr.utils.CssLoader;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.embed.swing.SwingFXUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kordamp.ikonli.javafx.FontIcon;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class WorkAlbumViewTest extends BaseDatabaseTest {
    private StackPane root;

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try { Platform.startup(latch::countDown); }
        catch (IllegalStateException running) { Platform.runLater(latch::countDown); }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        fx(() -> {
            Platform.setImplicitExit(false);
            new OverlayLayer(new StackPane());
            return null;
        });
    }

    @BeforeEach
    void bindServices() {
        DI.bind(WorkDao.class, AsmrWorkDao::new);
    }

    @AfterEach
    void detach() throws Exception {
        fx(() -> { if (root != null) root.getChildren().clear(); return null; });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void overviewCardsNavigateToDetailRouteInBothThemes(boolean dark) throws Exception {
        var service = DI.get(WorkAlbumService.class);
        var album = service.create("夜晚的作品集", "收藏的作品描述。".repeat(100));
        var empty = service.create("空作品集", "");
        var work = insertWork("BJ123", "雨夜的故事", "/missing/one");
        service.addWorks(List.of(album.id()), List.of(work.getId()));

        NavigationService navigation = DI.get(NavigationService.class);
        StackPane navigationHost = new StackPane();
        fx(() -> {
            navigation.attach(navigationHost, route -> new RouteView() {
                private final Label view = new Label(route.title());

                @Override
                public Node node() {
                    return view;
                }
            });
            return null;
        });

        var view = attach(fx(WorkAlbumView::new), dark, 1000, 700);
        await(() -> view.lookupAll(".album-card").size() == 2);
        fx(() -> {
            layout();
            var cards = view.lookupAll(".album-card");
            for (Node card : cards) {
                assertEquals(240, card.getBoundsInParent().getWidth(), 2);
                ImageView cover = (ImageView) card.lookup(".image-view");
                assertEquals(4.0 / 3, cover.getFitWidth() / cover.getFitHeight(), 0.001);
                assertNotNull(cover.getImage());
            }
            FontIcon icon = (FontIcon) view.lookup(".album-icon-button .ikonli-font-icon");
            assertNotNull(icon.getIconCode());
            if (!dark) assertTrue(((Color) icon.getIconColor()).getBrightness() < 0.6);
            snapshot("overview-" + (dark ? "dark" : "light"));
            Node card = cards.stream().filter(node -> album.name().equals(node.getAccessibleText())).findFirst().orElseThrow();
            click(card);
            return null;
        });
        await(() -> navigation.getCurrentRoute() instanceof AlbumDetailRoute detail && detail.albumId() == album.id());
        assertEquals(new AlbumDetailRoute(album.id()), fx(navigation::getCurrentRoute));

        service.delete(album.id());
        await(() -> view.lookupAll(".album-card").size() == 1);
        assertEquals(empty.id(), service.summaries().getFirst().album().id());
    }

    @Test
    void overviewShowsEmptyStateWhenNoAlbums() throws Exception {
        var view = attach(fx(WorkAlbumView::new), false, 1000, 700);
        await(() -> {
            Node empty = view.lookup(".empty-state");
            return empty != null && empty.isVisible();
        });
        fx(() -> {
            Label title = (Label) view.lookup(".empty-state-title");
            assertNotNull(title);
            assertEquals("暂无作品集", title.getText());
            return null;
        });
    }

    @Test
    void createDialogRejectsBlankNameAndCancelIsOnRight() throws Exception {
        DialogHandle handle = fx(() -> WorkAlbumDialogs.edit(null, ignored -> { }));
        JFXDialogLayout dialog = (JFXDialogLayout) handle.content();
        fx(() -> {
            TextField name = (TextField) dialog.lookup(".text-field");
            Button save = (Button) dialog.getActions().getFirst();
            Button cancel = (Button) dialog.getActions().getLast();
            assertEquals("取消", cancel.getText());
            assertTrue(cancel.isCancelButton());
            assertTrue(save.isDisabled());
            name.setText(" ");
            assertTrue(save.isDisabled());
            name.setText("新作品集");
            assertFalse(save.isDisabled());
            save.fire();
            return null;
        });
        await(() -> DI.get(WorkAlbumService.class).summaries().size() == 1);
        fx(() -> { handle.close(); return null; });
    }

    @Test
    void albumChooserAddsOnlyNewMembershipsAndSupportsDuplicateNames() throws Exception {
        var service = DI.get(WorkAlbumService.class);
        var first = service.create("同名作品集", "一");
        var second = service.create("同名作品集", "二");
        var work = insertWork(null, "无编号作品", "/chooser");
        service.addWorks(List.of(first.id()), List.of(work.getId()));
        DialogHandle handle = fx(() -> WorkAlbumDialogs.chooseAlbums(work));
        JFXDialogLayout dialog = (JFXDialogLayout) handle.content();
        VBox body = (VBox) dialog.getBody().getFirst();
        ScrollPane scroll = (ScrollPane) body.getChildrenUnmodifiable().getFirst();
        VBox choices = (VBox) scroll.getContent();
        await(() -> choices.getChildren().stream().filter(CheckBox.class::isInstance).count() == 2);
        fx(() -> {
            List<CheckBox> boxes = choices.getChildren().stream()
                    .filter(CheckBox.class::isInstance).map(CheckBox.class::cast).toList();
            CheckBox existing = boxes.stream()
                    .filter(box -> box.getUserData().equals(first.id())).findFirst().orElseThrow();
            assertTrue(existing.isSelected());
            assertTrue(existing.isDisabled());
            CheckBox target = boxes.stream()
                    .filter(box -> box.getUserData().equals(second.id())).findFirst().orElseThrow();
            target.setSelected(true);
            Button add = (Button) dialog.getActions().getFirst();
            assertFalse(add.isDisabled());
            add.fire();
            return null;
        });
        await(() -> service.albumsForWork(work.getId()).size() == 2);
        assertTrue(service.summaries().stream().allMatch(summary -> summary.workCount() == 1));
        fx(() -> { handle.close(); return null; });
    }

    @Test
    void albumMemberDeleteDoesNotUseWorkDeletionAndDetailsUseRecordId() throws Exception {
        var first = insertWork("RJ123", "来源一", "/shared");
        var second = insertWork("BJ124", "来源二", "/other");
        try (var connection = JdbcUtils.getConnection(); var statement = connection.prepareStatement(
                "UPDATE asmr_works SET filepath = ?, work_id = 'RJ123', storage_source_id = 2 WHERE id = ?")) {
            statement.setString(1, first.getFilepath());
            statement.setInt(2, second.getId());
            statement.executeUpdate();
        }
        fx(() -> {
            var detail = new cn.tealc.asmr.ui.work.WorkDetailViewModel(second);
            assertEquals(second.getId(), detail.getWork().getId());
            assertEquals("来源二", detail.getTitle());
            boolean[] removed = {false};
            var cell = new SmallWorkCell(first, work -> removed[0] = true);
            cell.onDelete(first);
            assertTrue(removed[0]);
            assertNotNull(new AsmrWorkDao().getWorkById(first.getId()));
            return null;
        });
    }

    private AsmrWork insertWork(String code, String title, String path) throws Exception {
        try (var connection = JdbcUtils.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO asmr_works(work_id,title,filepath,storage_source_id) VALUES (?,?,?,1)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            statement.setString(2, title);
            statement.setString(3, path);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) { return new AsmrWorkDao().getWorkById(keys.getInt(1)); }
        }
    }

    private <T extends Pane> T attach(T view, boolean dark, int width, int height) throws Exception {
        return fx(() -> {
            root = new StackPane(view);
            root.getStylesheets().add(dark ? new PrimerDark().getUserAgentStylesheet() : new PrimerLight().getUserAgentStylesheet());
            root.getStylesheets().add(CssLoader.getCommonCss(dark ? "/black-skin/atlantafx.css" : "/white-skin/atlantafx.css"));
            new Scene(root, width, height);
            root.resize(width, height);
            layout();
            return view;
        });
    }

    private void layout() { root.applyCss(); root.layout(); }

    private void snapshot(String name) throws Exception {
        String directory = System.getProperty("asmr.album.screenshots");
        if (directory == null) return;
        Path target = Path.of(directory);
        Files.createDirectories(target);
        ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png", target.resolve(name + ".png").toFile());
    }

    private static void click(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null));
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
            })) return;
            Thread.sleep(40);
        }
        fail("等待界面更新超时");
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(12, TimeUnit.SECONDS);
    }
}
