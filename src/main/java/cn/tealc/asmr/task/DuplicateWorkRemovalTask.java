package cn.tealc.asmr.task;

import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.task.background.BackgroundTask;
import cn.tealc.asmr.task.background.TaskType;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** 按主键移除选中的作品，存储删除失败时保留对应的数据库记录。 */
public class DuplicateWorkRemovalTask extends BackgroundTask<String> {
    private static final Logger log = LoggerFactory.getLogger(DuplicateWorkRemovalTask.class);
    private final List<AsmrWork> works;
    private final boolean deleteStorage;
    private final WorkService workService;
    private final Consumer<Set<Integer>> removed;
    private int succeededCount;
    private int failedCount;

    public DuplicateWorkRemovalTask(List<AsmrWork> works, boolean deleteStorage, WorkService workService,
                                    Consumer<Set<Integer>> removed) {
        super("批量移除重复作品", TaskType.GENERIC, false, null);
        this.works = List.copyOf(works);
        this.deleteStorage = deleteStorage;
        this.workService = workService;
        this.removed = removed;
    }

    @Override
    public String deduplicationKey() {
        return "REMOVE_DUPLICATE_WORKS";
    }

    @Override
    protected String call() {
        Set<Integer> removedIds = new HashSet<>();
        for (int i = 0; i < works.size(); i++) {
            if (isCancelled()) break;
            AsmrWork work = works.get(i);
            try {
                if (removeOne(work, deleteStorage, workService,
                        target -> MediaSourceFactory.forWork(target).delete(target.getFilepath()))) {
                    succeededCount++;
                    removedIds.add(work.getId());
                } else {
                    failedCount++;
                    log.warn("移除重复作品失败，保留记录: {}，{}", work.getId(), work.getFilepath());
                }
            } catch (RuntimeException exception) {
                failedCount++;
                log.warn("移除重复作品失败: {}，{}", work.getId(), work.getFilepath(), exception);
            }
            updateProgress(i + 1, works.size());
            updateMessage(String.format("已处理 %d/%d，失败 %d", i + 1, works.size(), failedCount));
            if (removedIds.size() >= 50) {
                publishRemoved(removedIds);
            }
        }
        publishRemoved(removedIds);
        return getCompletionSummary();
    }

    private void publishRemoved(Set<Integer> ids) {
        if (ids.isEmpty()) return;
        Set<Integer> snapshot = Set.copyOf(ids);
        ids.clear();
        Platform.runLater(() -> removed.accept(snapshot));
    }

    static boolean removeOne(AsmrWork work, boolean deleteStorage, WorkService workService,
                             Predicate<AsmrWork> storageRemover) {
        if (work.getId() <= 0) return false;
        if (deleteStorage && !storageRemover.test(work)) return false;
        return workService.deleteByIds(List.of(work.getId())) == 1;
    }

    @Override
    public String getCompletionSummary() {
        return String.format("成功移除 %d 部，失败 %d 部", succeededCount, failedCount);
    }
}
