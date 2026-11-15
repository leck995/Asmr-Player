package cn.tealc.asmr.ui.component;

import cn.tealc.asmr.task.background.BackgroundTask;
import cn.tealc.asmr.task.background.TaskAction;
import cn.tealc.asmr.task.background.TaskManager;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ListCell;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.concurrent.Worker;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;
import org.kordamp.ikonli.material2.Material2OutlinedAL;

/**
 * 后台任务列表行的自定义 Cell。
 * <p>
 * 每行展示：类型图标（随状态着色）、任务名称、进度条、当前消息、操作按钮。
 * 图标颜色通过 PseudoClass 驱动 CSS 控制，不直接操作 FontIcon.iconColor。
 *
 * @author Leck
 */
public class TaskListCell extends ListCell<BackgroundTask<?>> {

    private static final PseudoClass RUNNING   = PseudoClass.getPseudoClass("running");
    private static final PseudoClass SUCCEEDED = PseudoClass.getPseudoClass("succeeded");
    private static final PseudoClass FAILED    = PseudoClass.getPseudoClass("failed");
    private static final PseudoClass CANCELLED  = PseudoClass.getPseudoClass("cancelled");

    private final TaskManager taskManager;

    public TaskListCell(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    protected void updateItem(BackgroundTask<?> task, boolean empty) {
        super.updateItem(task, empty);
        if (empty || task == null) {
            setGraphic(null);
            setText(null);
            return;
        }

        // —— 左侧：类型图标（颜色由 CSS 伪类控制）——
        FontIcon icon = new FontIcon(task.getType().getIcon());
        icon.setIconSize(28);
        icon.getStyleClass().add("task-cell-icon");
        applyStatePseudoClass(icon, task.getState());
        task.stateProperty().addListener((obs, oldState, newState) -> applyStatePseudoClass(icon, newState));

        // —— 中间：名称 + 消息 ——
        Label nameLabel = new Label();
        nameLabel.textProperty().bind(task.titleProperty());
        nameLabel.getStyleClass().add("task-cell-name");
        nameLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.setMinWidth(0);

        Label msgLabel = new Label();
        msgLabel.textProperty().bind(task.messageProperty());
        msgLabel.getStyleClass().add("task-cell-msg");
        msgLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        msgLabel.setMaxWidth(300);

        VBox infoBox = new VBox(nameLabel, msgLabel);
        infoBox.setSpacing(4);
        infoBox.setFillWidth(true);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        infoBox.setMinWidth(0);
        infoBox.setMaxWidth(Double.MAX_VALUE);

        // —— 右侧：操作按钮 ——
        HBox actionsBox = new HBox(4);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);
        actionsBox.setSpacing(4);

        // 暂停/恢复按钮：仅 pausable 且运行中时显示
        Button pauseBtn = new Button();
        pauseBtn.setGraphic(new FontIcon(Material2OutlinedMZ.PAUSE));
        pauseBtn.getStyleClass().add("task-action-btn");
        pauseBtn.setTooltip(new Tooltip("暂停"));
        updatePauseButtonState(pauseBtn, task);
        // 可见性：pausable 且处于 RUNNING
        boolean pausableRunning = task.isPausable() && task.getState() == Worker.State.RUNNING;
        pauseBtn.setVisible(pausableRunning);
        pauseBtn.setManaged(pausableRunning);
        // 状态变化时刷新可见性与图标
        task.stateProperty().addListener((obs, oldState, newState) -> {
            boolean show = task.isPausable() && newState == Worker.State.RUNNING;
            pauseBtn.setVisible(show);
            pauseBtn.setManaged(show);
            updatePauseButtonState(pauseBtn, task);
        });
        pauseBtn.setOnAction(e -> {
            if (task.isPaused()) {
                task.resume();
                pauseBtn.setGraphic(new FontIcon(Material2OutlinedMZ.PAUSE));
                pauseBtn.setTooltip(new Tooltip("暂停"));
            } else {
                task.pause();
                pauseBtn.setGraphic(new FontIcon(Material2OutlinedMZ.PLAY_ARROW));
                pauseBtn.setTooltip(new Tooltip("恢复"));
            }
        });

        // 取消按钮：运行中时显示
        Button cancelBtn = new Button();
        cancelBtn.setGraphic(new FontIcon(Material2OutlinedAL.CANCEL));
        cancelBtn.getStyleClass().add("task-action-btn");
        cancelBtn.setTooltip(new Tooltip("取消"));
        boolean running = task.getState() == Worker.State.RUNNING;
        cancelBtn.setVisible(running);
        cancelBtn.setManaged(running);
        task.stateProperty().addListener((obs, oldState, newState) -> {
            boolean show = newState == Worker.State.RUNNING;
            cancelBtn.setVisible(show);
            cancelBtn.setManaged(show);
        });
        cancelBtn.setOnAction(e -> taskManager.cancel(task));

        // 重试按钮：终态且支持重试时显示
        Button retryBtn = new Button();
        retryBtn.setGraphic(new FontIcon(Material2OutlinedMZ.REFRESH));
        retryBtn.getStyleClass().add("task-action-btn");
        retryBtn.setTooltip(new Tooltip("重试"));
        boolean terminalRetryable = task.isRetryable()
                && (task.getState() == Worker.State.FAILED || task.getState() == Worker.State.CANCELLED);
        retryBtn.setVisible(terminalRetryable);
        retryBtn.setManaged(terminalRetryable);
        task.stateProperty().addListener((obs, oldState, newState) -> {
            boolean show = task.isRetryable()
                    && (newState == Worker.State.FAILED || newState == Worker.State.CANCELLED);
            retryBtn.setVisible(show);
            retryBtn.setManaged(show);
        });
        retryBtn.setOnAction(e -> taskManager.retry(task));

        actionsBox.getChildren().addAll(pauseBtn, cancelBtn, retryBtn);

        // 额外操作（如"打开日志"）
        for (TaskAction action : task.getExtraActions()) {
            Button actionBtn = new Button();
            actionBtn.setGraphic(new FontIcon(action.icon()));
            actionBtn.getStyleClass().add("task-action-btn");
            actionBtn.setTooltip(new Tooltip(action.name()));
            actionBtn.setOnAction(e -> action.handler().run());
            actionsBox.getChildren().add(actionBtn);
        }

        HBox row = new HBox(8, icon, infoBox, actionsBox);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 8, 6, 8));
        row.getStyleClass().add("task-cell");
        row.setMaxWidth(Double.MAX_VALUE);
        row.setMinWidth(0);

        // 进度条贴顶部边缘，圆角与 cell 一致
        ProgressBar progressBar = new ProgressBar();
        progressBar.progressProperty().bind(task.progressProperty());
        progressBar.getStyleClass().add("task-cell-progress");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setMinHeight(12);
        progressBar.setPrefHeight(12);
        StackPane.setAlignment(progressBar, Pos.TOP_CENTER);

        StackPane stack = new StackPane(row, progressBar);
        stack.getStyleClass().add("task-cell-stack");
        stack.setMaxWidth(Double.MAX_VALUE);
        stack.setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        setPrefWidth(USE_COMPUTED_SIZE);

        setGraphic(stack);
        setText(null);
    }

    /**
     * 根据任务当前暂停状态刷新按钮图标与 tooltip。
     */
    private void updatePauseButtonState(Button pauseBtn, BackgroundTask<?> task) {
        if (task.isPaused()) {
            pauseBtn.setGraphic(new FontIcon(Material2OutlinedMZ.PLAY_ARROW));
            pauseBtn.setTooltip(new Tooltip("恢复"));
        } else {
            pauseBtn.setGraphic(new FontIcon(Material2OutlinedMZ.PAUSE));
            pauseBtn.setTooltip(new Tooltip("暂停"));
        }
    }

    /**
     * 根据任务状态切换图标伪类，颜色由 CSS 控制。
     */
    private void applyStatePseudoClass(FontIcon icon, Worker.State state) {
        icon.pseudoClassStateChanged(RUNNING,   state == Worker.State.RUNNING);
        icon.pseudoClassStateChanged(SUCCEEDED, state == Worker.State.SUCCEEDED);
        icon.pseudoClassStateChanged(FAILED,    state == Worker.State.FAILED);
        icon.pseudoClassStateChanged(CANCELLED,  state == Worker.State.CANCELLED);
    }
}
