package cn.tealc.asmr.ui.album;

import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** 数据库任务在虚拟线程执行，结果统一回到 JavaFX 线程。 */
final class AlbumTasks {
    private static final Logger LOG = LoggerFactory.getLogger(AlbumTasks.class);

    private AlbumTasks() { }

    static <T> Task<T> run(Supplier<T> action, Consumer<T> success, Consumer<Throwable> failure) {
        Task<T> task = new Task<>() {
            @Override protected T call() { return action.get(); }
        };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> {
            LOG.error("集册操作失败", task.getException());
            failure.accept(task.getException());
        });
        Thread.startVirtualThread(task);
        return task;
    }
}
