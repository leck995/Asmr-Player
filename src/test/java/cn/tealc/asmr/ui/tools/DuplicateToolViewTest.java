package cn.tealc.asmr.ui.tools;

import atlantafx.base.controls.TabLine;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.mvvm.FluentViewLoader;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.ui.component.OverlayLayer;
import cn.tealc.asmr.task.background.TaskManager;
import com.jfoenix.controls.JFXDialogLayout;
import cn.tealc.asmr.ui.work.cells.DuplicateWorkCell;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateToolViewTest extends BaseDatabaseTest {
    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    void confirmationDefaultsToKeepingFilesAndBatchRemovalRefreshesResults() throws Exception {
        var tuple = onFxThread(() -> {
            DI.bind(WorkDao.class, AsmrWorkDao::new);
            WorkService service = DI.get(WorkService.class);
            AsmrWork first = work(1, "RJ100000");
            first.setStorageSourceId(1);
            AsmrWork second = work(2, "RJ100000");
            second.setStorageSourceId(2);
            second.setFilepath(first.getFilepath());
            assertTrue(service.addWork(first));
            assertTrue(service.addWork(second));
            var loaded = FluentViewLoader.fxmlView(ToolView.class).load();
            Parent root = loaded.getView();
            StackPane host = new StackPane(root);
            Scene scene = new Scene(host, 1050, 650);
            scene.getStylesheets().add(new PrimerLight().getUserAgentStylesheet());
            new OverlayLayer(host);
            ((TabLine) root.lookup(".tools-tabs")).getSelectionModel().select(1);
            ((Button) root.lookup("#checkDuplicateBtn")).fire();
            layout(host);
            click(card(root, 1), false, false);
            ((Button) root.lookup("#removeDuplicatesBtn")).fire();
            JFXDialogLayout dialog = (JFXDialogLayout) host.lookup(".jfx-alter");
            assertNotNull(dialog);
            CheckBox deleteStorage = (CheckBox) dialog.lookup(".check-box");
            assertNotNull(deleteStorage);
            assertFalse(deleteStorage.isSelected());
            assertEquals("取消", ((Button) dialog.getActions().getLast()).getText());
            ((Button) dialog.getActions().getFirst()).fire();
            assertTrue(((Button) root.lookup("#removeDuplicatesBtn")).isDisabled());
            return loaded;
        });
        var task = onFxThread(() -> DI.get(TaskManager.class).getTasks().getLast());
        task.get(20, TimeUnit.SECONDS);
        onFxThread(() -> {
            assertTrue(tuple.getViewModel().getDuplicatedWorks().isEmpty());
            assertCount(tuple.getView(), 0);
            assertEquals(1, DI.get(WorkService.class).getAllWorks().size());
            assertEquals("RJ100002", DI.get(WorkService.class).getAllWorks().getFirst().getWorkId());
            assertFalse(((Button) tuple.getView().lookup("#checkDuplicateBtn")).isDisabled());
            return null;
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cardsSupportCtrlShiftCheckboxAndPreserveSelectionAfterRefresh(boolean dark) throws Exception {
        onFxThread(() -> {
            DI.bind(WorkDao.class, AsmrWorkDao::new);
            var tuple = FluentViewLoader.fxmlView(ToolView.class).load();
            Parent root = tuple.getView();
            var vm = tuple.getViewModel();
            List<AsmrWork> works = List.of(work(1, "RJ100000"), work(2, "RJ100000"),
                    work(3, "RJ200000"), work(4, "RJ200000"));
            vm.getDuplicatedWorks().setAll(ToolViewModel.groupDuplicates(works));
            Scene scene = new Scene(root, 1050, 650);
            scene.getStylesheets().add(dark ? new PrimerDark().getUserAgentStylesheet()
                    : new PrimerLight().getUserAgentStylesheet());
            ((TabLine) root.lookup(".tools-tabs")).getSelectionModel().select(1);
            layout(root);
            var table = (TableView<?>) root.lookup("#duplicateTableView");
            var remove = (Button) root.lookup("#removeDuplicatesBtn");
            assertTrue(remove.isDisabled());
            var first = card(root, 1);
            SnapshotParameters snapshotParameters = new SnapshotParameters();
            snapshotParameters.setViewport(new Rectangle2D(0, 0, 105, 79));
            var unselectedColor = first.snapshot(snapshotParameters, null).getPixelReader().getColor(1, 60);
            click(first, false, false);
            layout(root);
            assertTrue(first.getPseudoClassStates().contains(PseudoClass.getPseudoClass("selected")));
            assertFalse(remove.isDisabled());
            var selectedColor = first.snapshot(snapshotParameters, null).getPixelReader().getColor(1, 60);
            assertNotEquals(unselectedColor, selectedColor);
            click(card(root, 3), true, false);
            assertCount(root, 2);
            click(card(root, 4), false, true);
            assertCount(root, 2);
            assertFalse(((CheckBox) card(root, 1).lookup(".check-box")).isSelected());
            assertTrue(((CheckBox) card(root, 3).lookup(".check-box")).isSelected());
            assertTrue(((CheckBox) card(root, 4).lookup(".check-box")).isSelected());
            click(card(root, 1), false, true);
            assertCount(root, 3);
            table.refresh();
            layout(root);
            assertTrue(((CheckBox) card(root, 1).lookup(".check-box")).isSelected());
            var checkbox = (CheckBox) card(root, 2).lookup(".check-box");
            Event.fireEvent(checkbox, mouse(MouseEvent.MOUSE_PRESSED, false, false));
            Event.fireEvent(checkbox, mouse(MouseEvent.MOUSE_RELEASED, false, false));
            Event.fireEvent(checkbox, mouse(MouseEvent.MOUSE_CLICKED, false, false));
            assertCount(root, 2);
            assertFalse(checkbox.isSelected());
            Event.fireEvent(table, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.A, false, true, false, false));
            assertCount(root, 4);
            Event.fireEvent(table, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
            assertCount(root, 0);
            assertTrue(remove.isDisabled());
            ((Button) root.lookup("#selectAllDuplicatesBtn")).fire();
            assertCount(root, 4);
            ((Button) root.lookup("#clearDuplicateSelectionBtn")).fire();
            assertCount(root, 0);
            return null;
        });
    }

    private static AsmrWork work(int id, String origin) {
        AsmrWork work = new AsmrWork();
        work.setId(id);
        work.setWorkId("RJ" + (100000 + id));
        work.setTitle("测试作品" + id);
        work.setOriginWorkId(origin);
        work.setStorageType(0);
        work.setFilepath("H:/tmp/asmr-selection-test/" + id);
        return work;
    }

    private static DuplicateWorkCell card(Parent root, int id) {
        return root.lookupAll(".duplicate-work-cell").stream().map(DuplicateWorkCell.class::cast)
                .filter(cell -> cell.getChildren().stream().anyMatch(node -> node instanceof Label label
                        && ("RJ" + (100000 + id)).equals(label.getText())))
                .findFirst().orElseThrow();
    }

    private static void click(DuplicateWorkCell cell, boolean ctrl, boolean shift) {
        Event.fireEvent(cell, mouse(MouseEvent.MOUSE_CLICKED, ctrl, shift));
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, boolean ctrl, boolean shift) {
        return new MouseEvent(type, 80, 60, 80, 60, MouseButton.PRIMARY, 1,
                shift, ctrl, false, false, false, false, false, false, false, true, null);
    }

    private static void assertCount(Parent root, int count) {
        assertEquals("已选 " + count + " 部", ((Label) root.lookup("#duplicateSelectedTip")).getText());
    }

    private static void layout(Parent root) {
        root.applyCss();
        root.layout();
    }

    private static <T> T onFxThread(Callable<T> callable) throws Exception {
        FutureTask<T> task = new FutureTask<>(callable);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }
}
