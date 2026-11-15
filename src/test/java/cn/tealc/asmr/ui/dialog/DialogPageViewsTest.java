package cn.tealc.asmr.ui.dialog;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.mvvm.FluentViewLoader;
import cn.tealc.asmr.mvvm.ViewTuple;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.ui.collection.AddToSheetView;
import cn.tealc.asmr.ui.collection.AddToSheetViewModel;
import cn.tealc.asmr.ui.component.OverlayLayer;
import cn.tealc.asmr.ui.work.UpdateInfoView;
import cn.tealc.asmr.ui.work.UpdateInfoViewModel;
import cn.tealc.asmr.ui.work.UpdateTagView;
import cn.tealc.asmr.ui.work.UpdateTagViewModel;
import cn.tealc.asmr.ui.player.AudioChannelView;
import cn.tealc.asmr.ui.tools.SubtitleRenameView;
import cn.tealc.asmr.ui.tools.SubtitleRenameViewModel;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 独立界面改造为 {@link DialogPage} 后的加载与插槽映射冒烟测试。
 */
class DialogPageViewsTest extends BaseDatabaseTest {

    private static StackPane root;

    @TempDir
    Path tempDir;

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
            root = new StackPane();
            new OverlayLayer(root);
            return null;
        });
    }

    @BeforeEach
    void bindServices() {
        DI.bind(WorkDao.class, AsmrWorkDao::new);
    }

    @AfterEach
    void clearOverlay() throws Exception {
        fx(() -> {
            root.getChildren().clear();
            return null;
        });
    }

    @Test
    void updateInfoPageMapsToDialogSlots() throws Exception {
        AsmrWork work = persistedWork();
        ViewTuple<UpdateInfoView, UpdateInfoViewModel> tuple = fx(() ->
                FluentViewLoader.fxmlView(UpdateInfoView.class)
                        .viewModel(new UpdateInfoViewModel(work))
                        .load());
        assertPage("编辑作品", tuple.getCodeBehind());
    }

    @Test
    void updateTagPageMapsToDialogSlots() throws Exception {
        AsmrWork work = persistedWork();
        ViewTuple<UpdateTagView, UpdateTagViewModel> tuple = fx(() ->
                FluentViewLoader.fxmlView(UpdateTagView.class)
                        .viewModel(new UpdateTagViewModel(work))
                        .load());
        assertPage("编辑作品标签", tuple.getCodeBehind());
    }

    @Test
    void addToSheetPageMapsToDialogSlots() throws Exception {
        AsmrWork work = persistedWork();
        Path audio = Files.writeString(tempDir.resolve("01.mp3"), "audio");
        AsmrFile file = new AsmrFile(audio.toFile(), null);
        ViewTuple<AddToSheetView, AddToSheetViewModel> tuple = fx(() ->
                FluentViewLoader.fxmlView(AddToSheetView.class)
                        .viewModel(new AddToSheetViewModel(work, file))
                        .load());
        assertPage("添加到歌单", tuple.getCodeBehind());
    }

    @Test
    void subtitleRenamePageMapsToDialogSlots() throws Exception {
        Files.writeString(tempDir.resolve("01.mp3"), "audio");
        Files.writeString(tempDir.resolve("01.lrc"), "lyrics");
        AsmrWork work = new AsmrWork();
        work.setStorageType(0);
        work.setFilepath(tempDir.toString());
        ViewTuple<SubtitleRenameView, SubtitleRenameViewModel> tuple = fx(() ->
                FluentViewLoader.javaView(SubtitleRenameView.class)
                        .viewModel(new SubtitleRenameViewModel(work, tempDir.toString()))
                        .load());
        assertNotNull(tuple.getCodeBehind().content().lookup(".subtitle-rename-action"));
        assertPage("字幕文件配对重命名", tuple.getCodeBehind());
    }

    @Test
    void audioChannelFxmlLoadsWithoutVlc() throws Exception {
        DialogPage page = fx(() -> {
            FXMLLoader loader = new FXMLLoader(
                    AudioChannelView.class.getResource("AudioChannelView.fxml"));
            loader.setControllerFactory(type -> new AudioChannelView() {
                @Override
                public void initialize() {
                }
            });
            loader.load();
            return loader.getController();
        });
        assertNotNull(page.header());
        assertNotNull(page.content());
    }

    private void assertPage(String expectedTitle, DialogPage page) throws Exception {
        assertFalse(page.header().getTitle().isBlank());
        assertEquals(expectedTitle, page.header().getTitle());

        DialogHandle handle = fx(() -> Dialogs.show(page));
        try {
            JFXDialogLayout layout = (JFXDialogLayout) fx(handle::content);
            assertTrue(fx(handle::isShowing));
            assertSame(page.header(), layout.getHeading().getFirst());
            assertSame(page.content(), layout.getBody().getFirst());
            assertEquals(page.actions().size(), layout.getActions().size());
            List<Button> buttons = layout.getActions().stream().map(Button.class::cast).toList();
            for (int i = 0; i < buttons.size(); i++) {
                Button button = buttons.get(i);
                DialogAction action = page.actions().get(i);
                assertFalse(button.getText().isBlank());
                if (action.isCancelButton()) {
                    assertTrue(button.getStyleClass().contains("cancel-button"));
                }
                if (action.isDefaultButton()) {
                    assertTrue(button.getStyleClass().contains("default-button"));
                }
            }
        } finally {
            fx(() -> {
                handle.close();
                return null;
            });
        }
    }

    private AsmrWork persistedWork() {
        AsmrWork work = new AsmrWork();
        work.setWorkId("RJ123456");
        work.setTitle("测试作品");
        work.setStorageType(0);
        work.setStorageSourceId(1);
        work.setFilepath(tempDir.toString());
        WorkService service = DI.get(WorkService.class);
        assertTrue(service.addWork(work));
        return service.getWorkByTitle("测试作品");
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
