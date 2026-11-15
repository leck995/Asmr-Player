package cn.tealc.asmr.task.background;

import javafx.concurrent.Task;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 后台任务统一基类，继承 {@link Task}，扩展以下能力：
 * <ul>
 *   <li><b>任务元信息</b>：名称、类型、唯一 ID、起始时间</li>
 *   <li><b>去重身份</b>：{@link #deduplicationKey()} 返回非 null 时，
 *       TaskManager 据此拒绝同 key 的并发提交</li>
 *   <li><b>暂停 / 恢复</b>：{@code volatile} 标志 + 子类在循环中调用 {@link #checkPause()}</li>
 *   <li><b>重试</b>：{@code retryFactory} 重建同参数实例，绕过 Task 不可 reset 的限制</li>
 *   <li><b>额外操作</b>：如「打开日志」，通过 {@link #addExtraAction(TaskAction)} 注册</li>
 * </ul>
 * <p>
 * 子类应在 {@code call()} 的主循环中调用 {@link #checkPause()} 以支持暂停；
 * 支持暂停的任务构造时传入 {@code pausable=true}。
 *
 * @param <T> 任务最终返回值类型
 * @author Leck
 */
public abstract class BackgroundTask<T> extends Task<T> {
    private final String taskName;
    private final TaskType type;
    private final String id = UUID.randomUUID().toString();
    private final LocalDateTime startTime = LocalDateTime.now();

    /* —— 暂停支持 —— */
    private final boolean pausable;
    private volatile boolean pauseRequested;

    /* —— 重试支持：Task 终态不可逆，由工厂重建实例 —— */
    private final Supplier<BackgroundTask<T>> retryFactory;

    /* —— 额外操作 —— */
    private final List<TaskAction> extraActions = new ArrayList<>();

    /**
     * 创建后台任务。
     *
     * @param taskName    任务名称（用于 UI 展示与通知）
     * @param type        任务类型
     * @param pausable    是否支持暂停
     * @param retryFactory 重试工厂；null 表示不支持重试
     */
    protected BackgroundTask(String taskName, TaskType type, boolean pausable,
                             Supplier<BackgroundTask<T>> retryFactory) {
        this.taskName = taskName;
        this.type = type;
        this.pausable = pausable;
        this.retryFactory = retryFactory;
        updateTitle(taskName);
    }

    /**
     * 去重键。返回非 null 时，TaskManager 会拒绝具有相同 key 的并发提交。
     * 默认返回 null（不参与去重），子类按需覆写。
     * <p>
     * 例如扫描任务按 {@code "SCAN:" + storageType + ":" + rootPath} 去重。
     *
     * @return 去重键，null 表示不参与去重
     */
    public String deduplicationKey() {
        return null;
    }

    // ==================== 暂停 / 恢复 ====================

    /**
     * 子类在 {@code call()} 的循环体中调用此方法。
     * 若处于暂停状态则自旋等待，直到被恢复或取消。
     *
     * @throws InterruptedException 任务被取消时抛出，子类应让异常传播以终止 call()
     */
    protected void checkPause() throws InterruptedException {
        if (!pausable) return;
        while (pauseRequested && !isCancelled()) {
            Thread.sleep(50);
        }
        if (isCancelled()) {
            throw new InterruptedException("任务已取消");
        }
    }

    /** 请求暂停。仅 {@code pausable=true} 的任务生效。 */
    public final void pause() {
        if (pausable) {
            pauseRequested = true;
        }
    }

    /** 恢复执行。 */
    public final void resume() {
        pauseRequested = false;
    }

    /** 当前是否处于暂停等待状态。 */
    public boolean isPaused() {
        return pausable && pauseRequested;
    }

    /** 是否支持暂停操作。 */
    public boolean isPausable() {
        return pausable;
    }

    // ==================== 重试 ====================

    /** 是否支持重试。 */
    public boolean isRetryable() {
        return retryFactory != null;
    }

    /**
     * 创建一个同参数的新实例用于重试。
     * 若不支持重试则返回 null。
     */
    public BackgroundTask<T> createRetryInstance() {
        return retryFactory != null ? retryFactory.get() : null;
    }

    // ==================== 额外操作 ====================

    /** 注册一个额外操作（如「打开日志」）。 */
    protected void addExtraAction(TaskAction action) {
        extraActions.add(action);
    }

    /** 获取所有额外操作。UI 据此动态渲染按钮。 */
    public List<TaskAction> getExtraActions() {
        return List.copyOf(extraActions);
    }

    /**
     * 任务日志文件路径，供「打开日志」操作使用。
     * 默认返回 null，子类按需覆写。
     */
    public String getLogFilePath() {
        return null;
    }

    /**
     * 获取任务完成后的摘要文案。
     * 子类可覆写以返回比任务值更适合展示的业务结果。
     */
    public String getCompletionSummary() {
        return String.valueOf(getValue());
    }

    // ==================== 元信息 ====================

    public String getTaskName() {
        return taskName;
    }

    public TaskType getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }
}
