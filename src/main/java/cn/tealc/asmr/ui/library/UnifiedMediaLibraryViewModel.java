package cn.tealc.asmr.ui.library;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.model.StorageSource;
import cn.tealc.asmr.mvvm.ViewModel;
import cn.tealc.asmr.service.MediaLibraryService;
import cn.tealc.asmr.service.MetadataSyncService;
import cn.tealc.asmr.service.WorkImportService;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.source.StorageType;
import cn.tealc.asmr.task.background.TaskManager;
import cn.tealc.asmr.task.WorkScanTask;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.concurrent.WorkerStateEvent;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 统一媒体库管理状态。 */
public class UnifiedMediaLibraryViewModel implements ViewModel {
    private final MediaLibraryService mediaLibraryService = DI.get(MediaLibraryService.class);
    private final MetadataSyncService metadataSyncService = DI.get(MetadataSyncService.class);
    private final WorkImportService workImportService = DI.get(WorkImportService.class);
    private final WorkService workService = DI.get(WorkService.class);
    private final TaskManager taskManager = DI.get(TaskManager.class);
    private final ObservableList<MediaLibrary> libraries = FXCollections.observableArrayList();
    private final ObservableMap<Integer, Integer> workCounts = FXCollections.observableHashMap();

    public UnifiedMediaLibraryViewModel() { refresh(); }
    public ObservableList<MediaLibrary> getLibraries() { return libraries; }
    public void refresh() {
        Map<Integer, Integer> counts = workService.getWorkCountsByMediaLibrary();
        workCounts.clear();
        workCounts.putAll(counts);
        libraries.setAll(mediaLibraryService.getAll());
    }

    public ObservableMap<Integer, Integer> getWorkCounts() { return workCounts; }

    public int getWorkCount(MediaLibrary library) {
        return library == null || library.getId() == null ? 0 : workCounts.getOrDefault(library.getId(), 0);
    }

    public void add(StorageSource source, String name, String path, int level, boolean titleCN, boolean forceUpdate) {
        if (source == null || source.getId() == null) throw new IllegalArgumentException("请先选择存储源");
        if (path == null || path.isBlank()) throw new IllegalArgumentException("请选择要导入的目录");
        if (level < 0 || level > 3) throw new IllegalArgumentException("请选择有效的目录层级");
        validatePath(source, path);
        String libraryName = name == null || name.isBlank() ? path : name.trim();
        MediaLibrary library = new MediaLibrary(libraryName, path, level + 1, source.getType(), source.getId());
        if (!mediaLibraryService.add(library)) throw new IllegalStateException("保存媒体库失败");
        libraries.add(library);
        submitScan(library, workImportService.scanLibrary(path, level, titleCN, forceUpdate, source, library.getId()));
    }

    public void rescan(MediaLibrary library, StorageSource source, boolean titleCN, boolean forceUpdate) {
        if (library == null) throw new IllegalArgumentException("请先选择媒体库");
        if (source == null) throw new IllegalStateException("媒体库关联的存储源不可用");
        submitScan(library, workImportService.scanLibrary(library.getPath(), library.getLevel() - 1,
                titleCN, forceUpdate, source, library.getId()));
    }

    public void scrape(MediaLibrary library) {
        if (library == null || library.getId() == null) throw new IllegalArgumentException("请选择媒体库");
        metadataSyncService.syncBatch(workService.getAllByMediaLibrary(library.getId()), library.getPath(), () -> {
            LocalDateTime completedAt = LocalDateTime.now();
            mediaLibraryService.recordScrapeCompleted(library.getId(), completedAt);
            library.setLastScrapedAt(completedAt);
            refresh();
        });
    }

    public void delete(MediaLibrary library, boolean clearWorks) {
        if (library == null) throw new IllegalArgumentException("请先选择媒体库");
        if (clearWorks) mediaLibraryService.deleteLibraryWithWorks(library);
        else mediaLibraryService.deleteLibraryKeepWorks(library);
        libraries.remove(library);
    }

    private void validatePath(StorageSource source, String path) {
        boolean ignoreCase = source.getType() == StorageType.LOCAL;
        String normalized = normalizePath(path);
        for (MediaLibrary item : libraries) {
            if (!Objects.equals(item.getStorageSourceId(), source.getId())) continue;
            String existing = normalizePath(item.getPath());
            boolean same = ignoreCase ? normalized.equalsIgnoreCase(existing) : normalized.equals(existing);
            if (same) throw new IllegalArgumentException("该目录已导入媒体库");
            if (isChildPath(normalized, existing, ignoreCase) || isChildPath(existing, normalized, ignoreCase)) {
                throw new IllegalArgumentException("该目录与媒体库「" + item.getName()
                        + "」的目录重叠（父/子目录），请选择其他目录");
            }
        }
    }

    private static String normalizePath(String path) {
        String normalized = path.trim().replace('\\', '/');
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isChildPath(String child, String parent, boolean ignoreCase) {
        if (ignoreCase) {
            child = child.toLowerCase(Locale.ROOT);
            parent = parent.toLowerCase(Locale.ROOT);
        }
        if (parent.equals("/")) return true;
        return child.startsWith(parent + "/");
    }

    private void submitScan(MediaLibrary library, WorkScanTask task) {
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, event -> {
            LocalDateTime completedAt = LocalDateTime.now();
            mediaLibraryService.recordImportCompleted(library.getId(), completedAt);
            library.setLastImportedAt(completedAt);
            refresh();
        });
        taskManager.submit(task);
    }
}
