package cn.tealc.asmr.task.background;

import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.concurrent.Worker;

import java.util.Optional;

/**
 * 后台任务统一管理器（DI 单例）。
 * <p>
 * 持有运行中与已完成的任务列表，提供提交 / 暂停 / 取消 / 重试 / 清空 API，
 * 并通过 JavaFX 属性驱动 UI（运行中数量、是否有任务运行）。
 * <p>
 * 核心设计：
 * <ul>
 *   <li>所有 UI 更新通过属性绑定，无需手动刷新</li>
 *   <li>任务终态回调统一处理：移除去重槽位、重算计数、发完成通知</li>
 *   <li>去重通过 {@code runningKeys} 映射 O(1) 查询，不遍历列表</li>
 * </ul>
 * <p>
 * 调用方应在 JavaFX Application Thread 调用 {@link #submit(BackgroundTask)}，
 * 因为任务列表的 ObservableList 操作需要 FX 线程。
 *
 * @author Leck
 */
public class TaskManager {

    /** 运行中 + 已完成（保留最近的任务），UI 直接绑定 */
    private final ObservableList<BackgroundTask<?>> tasks = FXCollections.observableArrayList();

    /** 去重槽：deduplicationKey -> 运行中的任务 */
    private final ObservableMap<String, BackgroundTask<?>> runningKeys = FXCollections.observableHashMap();

    /** 运行中任务数（绑定 HeaderBar 按钮可见性 & 徽标） */
    private final IntegerProperty runningCount = new SimpleIntegerProperty(0);

    /** 是否有任意任务运行中（绑定旋转动画） */
    private final BooleanProperty anyRunning = new SimpleBooleanProperty(false);

    /**
     * 提交任务。先查重：deduplicationKey 已存在运行中实例则拒绝并提醒。
     *
     * @param task 要提交的后台任务
     * @return true=已启动，false=被去重拒绝
     */
    public boolean submit(BackgroundTask<?> task) {
        String key = task.deduplicationKey();
        if (key != null && runningKeys.containsKey(key)) {
            EventBus.message(MessageInfo.warning("该任务正在执行中，请等待当前任务完成"));
            return false;
        }
        if (key != null) {
            runningKeys.put(key, task);
        }

        // 统一注册终态回调；闭包捕获 dedupKey 用于精确移除
        String dedupKey = key;
        task.setOnSucceeded(e -> onFinished(task, Worker.State.SUCCEEDED, dedupKey));
        task.setOnFailed(e -> onFinished(task, Worker.State.FAILED, dedupKey));
        task.setOnCancelled(e -> onFinished(task, Worker.State.CANCELLED, dedupKey));

        // 监听状态变化：进入 RUNNING 时 +1，离开 RUNNING 时 -1
        task.stateProperty().addListener((obs, oldState, newState) -> {
            if (oldState == Worker.State.RUNNING && newState != Worker.State.RUNNING) {
                decrementRunning();
            } else if (oldState != Worker.State.RUNNING && newState == Worker.State.RUNNING) {
                incrementRunning();
            }
        });

        tasks.add(task);
        Thread.startVirtualThread(task);
        return true;
    }

    /** 运行中计数 +1 */
    private void incrementRunning() {
        int c = runningCount.get() + 1;
        runningCount.set(c);
        anyRunning.set(true);
    }

    /** 运行中计数 -1 */
    private void decrementRunning() {
        int c = Math.max(0, runningCount.get() - 1);
        runningCount.set(c);
        anyRunning.set(c > 0);
    }

    /**
     * 查询某去重 key 是否正在运行（供调用方预判）。
     *
     * @param dedupKey 去重键
     * @return true=有同 key 任务运行中
     */
    public boolean isRunning(String dedupKey) {
        return dedupKey != null && runningKeys.containsKey(dedupKey);
    }

    /** 取消指定任务。 */
    public void cancel(BackgroundTask<?> task) {
        task.cancel();
    }

    /** 暂停指定任务（仅 pausable=true 生效）。 */
    public void pause(BackgroundTask<?> task) {
        task.pause();
    }

    /** 恢复指定任务。 */
    public void resume(BackgroundTask<?> task) {
        task.resume();
    }

    /**
     * 重试指定任务。由 retryFactory 重建同参数实例，再交由 {@link #submit} 提交。
     * 若旧实例仍运行中（去重 key 未释放），重试会被去重拒绝。
     */
    public void retry(BackgroundTask<?> task) {
        BackgroundTask<?> fresh = task.createRetryInstance();
        if (fresh != null) {
            submit(fresh);
        }
    }

    /** 清除所有已完成（非运行中）的任务。 */
    public void clearFinished() {
        tasks.removeIf(t -> t.getState() != Worker.State.READY
                && t.getState() != Worker.State.SCHEDULED
                && t.getState() != Worker.State.RUNNING);
    }

    // ==================== 属性访问 ====================

    /** 获取任务列表（运行中 + 已完成），供 UI 绑定。 */
    public ObservableList<BackgroundTask<?>> getTasks() {
        return tasks;
    }

    /** 运行中任务数属性，绑定 HeaderBar 徽标。 */
    public IntegerProperty runningCountProperty() {
        return runningCount;
    }

    /** 是否有任意任务运行中，绑定 HeaderBar 按钮可见性与旋转动画。 */
    public BooleanProperty anyRunningProperty() {
        return anyRunning;
    }

    // ==================== 内部逻辑 ====================

    /**
     * 任务终态统一处理：移除去重槽位、重算计数、发完成通知。
     */
    private void onFinished(BackgroundTask<?> task, Worker.State state, String dedupKey) {
        Platform.runLater(() -> {
            if (dedupKey != null) {
                runningKeys.remove(dedupKey);
            }

            MessageType type = switch (state) {
                case SUCCEEDED -> MessageType.SUCCESS;
                case FAILED -> MessageType.ERROR;
                case CANCELLED -> MessageType.INFO;
                default -> MessageType.INFO;
            };

            String msg = switch (state) {
                case SUCCEEDED -> task.getTaskName() + " 完成：" + task.getCompletionSummary();
                case FAILED -> task.getTaskName() + " 失败：" +
                        Optional.ofNullable(task.getException())
                                .map(Throwable::getMessage).orElse("未知错误");
                case CANCELLED -> task.getTaskName() + " 已取消";
                default -> "";
            };

            MessageInfo info = switch (type) {
                case SUCCESS -> MessageInfo.success(msg);
                case ERROR -> MessageInfo.error(msg);
                default -> MessageInfo.info(msg);
            };
            EventBus.message(info);
        });
    }
}
