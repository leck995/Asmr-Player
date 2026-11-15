package cn.tealc.asmr.ui.dialog;

import cn.tealc.asmr.task.background.BackgroundTask;
import cn.tealc.asmr.task.background.TaskManager;
import cn.tealc.asmr.ui.component.EmptyState;
import cn.tealc.asmr.ui.component.PageHeader;
import cn.tealc.asmr.ui.component.TaskListCell;
import javafx.beans.binding.Bindings;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

/**
 * 后台任务列表面板。
 * <p>
 * 通过 {@link Dialogs} 展示在 OverlayLayer 浮层中，
 * 列表绑定 {@link TaskManager#getTasks()}，所有状态变化由属性绑定驱动刷新。
 *
 * @author Leck
 */
public class TaskListDialog {

    private final TaskManager taskManager;

    public TaskListDialog(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    /**
     * 构建并展示任务列表面板。
     */
    public void show() {
        ListView<BackgroundTask<?>> listView = new ListView<>();
        listView.setItems(taskManager.getTasks());
        listView.setCellFactory(lv -> new TaskListCell(taskManager));
        listView.setMinHeight(310);
        listView.getStyleClass().add("task-list-view");
        VBox.setVgrow(listView, Priority.ALWAYS);

        EmptyState emptyState = EmptyState.of(Material2OutlinedMZ.PENDING_ACTIONS,
                "当前没有后台任务", "任务开始后，会在这里显示执行进度。");
        emptyState.setMouseTransparent(true);
        emptyState.visibleProperty().bind(Bindings.isEmpty(taskManager.getTasks()));
        emptyState.managedProperty().bind(emptyState.visibleProperty());

        StackPane stack = new StackPane(listView, emptyState);
        stack.setPrefHeight(410);
        stack.setMinHeight(310);
        VBox.setVgrow(stack, Priority.ALWAYS);
        stack.getStyleClass().add("task-list-pane");

        PageHeader header = new PageHeader();
        header.setIcon(Material2OutlinedMZ.PENDING_ACTIONS);
        header.setTitle("后台任务");
        header.setSubtitle("查看任务的执行进度、处理结果与详细日志。");

        Dialogs.show(DialogSpec.builder()
                .styleClass("task-dialog")
                .heading(header)
                .content(stack)
                .action(DialogAction.of("清空已完成", "default-button",
                                handle -> taskManager.clearFinished())
                        .disabledWhen(Bindings.isEmpty(taskManager.getTasks())))
                .action("关闭", "cancel-button", DialogHandle::close)
                .build());
    }
}
