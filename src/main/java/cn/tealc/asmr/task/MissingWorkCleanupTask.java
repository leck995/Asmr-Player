package cn.tealc.asmr.task;

import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.task.background.BackgroundTask;
import cn.tealc.asmr.task.background.TaskType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台扫描并清理数据库中已不存在的作品记录。
 * <p>
 * 该任务只删除数据库记录，不删除本地或远程存储中的文件。
 */
public class MissingWorkCleanupTask extends BackgroundTask<String> {
    private final List<AsmrWork> works;
    private final WorkService workService;
    private int missingCount;
    private int deletedCount;

    public MissingWorkCleanupTask(List<AsmrWork> works, WorkService workService) {
        super("清理失效作品", TaskType.GENERIC, true, null);
        this.works = List.copyOf(works);
        this.workService = workService;
    }

    @Override
    public String deduplicationKey() {
        return "CLEANUP:MISSING:ALL";
    }

    @Override
    protected String call() throws Exception {
        if (works.isEmpty()) {
            updateProgress(1, 1);
            return "没有可检查的作品";
        }

        List<Integer> missingIds = new ArrayList<>();
        Map<Integer, MediaSource> sources = new HashMap<>();
        for (int i = 0; i < works.size(); i++) {
            checkPause();
            if (isCancelled()) return "已取消";

            AsmrWork work = works.get(i);
            boolean exists;
            try {
                if (work.getStorageSourceId() == null) throw new IllegalStateException("作品未关联存储源");
                var source = sources.computeIfAbsent(work.getStorageSourceId(), MediaSourceFactory::forStorageSourceId);
                exists = work.getFilepath() != null && source.exists(work.getFilepath());
            } catch (RuntimeException exception) {
                exists = true;
                updateMessage(String.format("无法确认路径，已跳过：%s", work.getFilepath()));
            }
            if (!exists && work.getId() > 0) {
                missingIds.add(work.getId());
            }
            updateProgress(i + 1, works.size());
            updateMessage(String.format("检查失效作品 (%d/%d)", i + 1, works.size()));
        }

        missingCount = missingIds.size();
        deletedCount = workService.deleteByIds(missingIds);
        return String.format("发现%d个失效记录，已清理%d个", missingCount, deletedCount);
    }

    @Override
    public String getCompletionSummary() {
        return String.format("发现%d个失效记录，已清理%d个", missingCount, deletedCount);
    }
}
