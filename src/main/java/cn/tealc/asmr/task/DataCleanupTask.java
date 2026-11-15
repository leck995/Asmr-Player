package cn.tealc.asmr.task;

import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.service.MediaLibraryService;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.task.background.BackgroundTask;
import cn.tealc.asmr.task.background.TaskType;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * 工具箱数据清理任务。任务只处理数据库记录或应用封面缓存，不删除作品目录中的实际文件。
 */
public class DataCleanupTask extends BackgroundTask<String> {
    public enum Operation {
        UNBOUND_WORKS,
        UNUSED_COVERS,
        EMPTY_LIBRARIES
    }

    private final Operation operation;
    private final List<AsmrWork> works;
    private final List<File> coverFiles;
    private final List<MediaLibrary> libraries;
    private final WorkService workService;
    private final MediaLibraryService mediaLibraryService;
    private int affectedCount;
    private int failedCount;

    public DataCleanupTask(Operation operation, List<AsmrWork> works, List<File> coverFiles,
                           List<MediaLibrary> libraries, WorkService workService,
                           MediaLibraryService mediaLibraryService) {
        super(taskName(operation), TaskType.GENERIC, true, null);
        this.operation = operation;
        this.works = works == null ? List.of() : List.copyOf(works);
        this.coverFiles = coverFiles == null ? List.of() : List.copyOf(coverFiles);
        this.libraries = libraries == null ? List.of() : List.copyOf(libraries);
        this.workService = workService;
        this.mediaLibraryService = mediaLibraryService;
    }

    @Override
    public String deduplicationKey() {
        return "CLEANUP:" + operation.name();
    }

    @Override
    protected String call() throws Exception {
        return switch (operation) {
            case UNBOUND_WORKS -> cleanupUnboundWorks();
            case UNUSED_COVERS -> cleanupUnusedCovers();
            case EMPTY_LIBRARIES -> cleanupEmptyLibraries();
        };
    }

    private String cleanupUnboundWorks() {
        List<Integer> ids = works.stream().map(AsmrWork::getId).filter(id -> id > 0).toList();
        int total = ids.size();
        if (total == 0) {
            updateProgress(1, 1);
            return "没有可清理的未绑定作品";
        }
        for (int start = 0; start < total; start += 500) {
            if (isCancelled()) return "已取消";
            int end = Math.min(total, start + 500);
            affectedCount += workService.deleteByIds(ids.subList(start, end));
            updateProgress(end, total);
            updateMessage(String.format("清理未绑定作品 (%d/%d)", end, total));
        }
        return String.format("已清理%d个未绑定作品", affectedCount);
    }

    private String cleanupUnusedCovers() {
        int total = coverFiles.size();
        if (total == 0) {
            updateProgress(1, 1);
            return "没有发现多余封面";
        }
        for (int i = 0; i < total; i++) {
            if (isCancelled()) return "已取消";
            try {
                if (Files.deleteIfExists(coverFiles.get(i).toPath())) {
                    affectedCount++;
                }
            } catch (Exception exception) {
                failedCount++;
            }
            updateProgress(i + 1, total);
            updateMessage(String.format("清理多余封面 (%d/%d)", i + 1, total));
        }
        return String.format("已清理%d个多余封面，失败%d个", affectedCount, failedCount);
    }

    private String cleanupEmptyLibraries() {
        int total = libraries.size();
        if (total == 0) {
            updateProgress(1, 1);
            return "没有空媒体库";
        }
        for (int i = 0; i < total; i++) {
            if (isCancelled()) return "已取消";
            MediaLibrary library = libraries.get(i);
            boolean deleted = false;
            try {
                deleted = library.getId() != null && mediaLibraryService.delete(library.getId());
            } catch (RuntimeException ignored) {
                // 单个媒体库删除失败时继续处理其余候选项。
            }
            if (!deleted) {
                failedCount++;
            } else {
                affectedCount++;
            }
            updateProgress(i + 1, total);
            updateMessage(String.format("清理空媒体库 (%d/%d)", i + 1, total));
        }
        return String.format("已清理%d个空媒体库，失败%d个", affectedCount, failedCount);
    }

    @Override
    public String getCompletionSummary() {
        return switch (operation) {
            case UNBOUND_WORKS -> String.format("已清理%d个未绑定作品", affectedCount);
            case UNUSED_COVERS -> String.format("已清理%d个多余封面，失败%d个", affectedCount, failedCount);
            case EMPTY_LIBRARIES -> String.format("已清理%d个空媒体库，失败%d个", affectedCount, failedCount);
        };
    }

    private static String taskName(Operation operation) {
        return switch (operation) {
            case UNBOUND_WORKS -> "清理未绑定作品";
            case UNUSED_COVERS -> "清理多余封面";
            case EMPTY_LIBRARIES -> "清理空媒体库";
        };
    }
}
