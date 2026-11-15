package cn.tealc.asmr.task;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.model.StorageSource;
import cn.tealc.asmr.service.MediaLibraryService;
import cn.tealc.asmr.service.WorkImportService;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.source.StorageType;
import cn.tealc.asmr.task.background.BackgroundTask;
import cn.tealc.asmr.task.background.TaskAction;
import cn.tealc.asmr.task.background.TaskType;
import cn.tealc.asmr.utils.AppPaths;
import cn.tealc.asmr.utils.WorkCodeUtils;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量扫描媒体库目录的任务。
 * <p>
 * 通过 {@link MediaSource} 递归到目标层级，列举所有子目录，
 * 调用 {@link WorkService} 逐个导入，并记录日志和进度。
 * 继承 {@link BackgroundTask}，支持暂停/恢复、取消、去重和打开日志。
 * 统一由 {@code TaskManager} 管理，进度与状态通过 {@code messageProperty} 驱动 UI。
 *
 * @author Leck
 */
public class WorkScanTask extends BackgroundTask<String> {
    private static final Logger log = LoggerFactory.getLogger(WorkScanTask.class);

    private final String rootPath;
    private final int level;
    private final boolean titleCN;
    private final boolean forceUpdate;
    private final StorageType storageType;
    private final StorageSource storageSource;
    private final int mediaLibraryId;

    private final WorkService workService;
    private String logFilepath;

    /**
     * 创建扫描任务。
     *
     * @param rootPath    媒体库根目录路径
     * @param level       目录层级（0 起，0 表示根目录的直接子目录）
     * @param titleCN     是否设置文件夹名称为中文标题
     * @param forceUpdate 是否强制更新已存在的作品
     * @param storageType 存储类型
     * @param workService 作品服务
     */
    public WorkScanTask(String rootPath, int level, boolean titleCN, boolean forceUpdate, StorageSource storageSource,
                        int mediaLibraryId, WorkService workService) {
        super("扫描媒体库", TaskType.SCAN_LIBRARY, true, null);
        this.rootPath = rootPath;
        this.level = level;
        this.titleCN = titleCN;
        this.forceUpdate = forceUpdate;
        this.storageSource = storageSource;
        this.storageType = storageSource.getType();
        this.mediaLibraryId = mediaLibraryId;
        this.workService = workService;
        addExtraAction(new TaskAction("打开日志", Material2OutlinedAL.DESCRIPTION, this::openLogFile));
    }

    /**
     * 去重键：同一存储类型 + 同一根路径视为重复扫描。
     */
    @Override
    public String deduplicationKey() {
        return "SCAN:" + storageSource.getId() + ":" + normalize(rootPath);
    }

    private String normalize(String p) {
        return p == null ? "" : p.replace('\\', '/').replaceAll("/+$", "");
    }

    @Override
    protected String call() {
        MediaSource source = MediaSourceFactory.forStorageSource(storageSource);
        Map<Integer, String> libraryNames = new HashMap<>();
        for (MediaLibrary library : DI.get(MediaLibraryService.class).getAll()) {
            if (library.getId() != null) {
                libraryNames.put(library.getId(), library.getName());
            }
        }

        // 递归列举目标层级的所有子目录
        List<String> asmrDirs = new ArrayList<>();
        collectDirsAtDepth(source, rootPath, 0, level + 1, asmrDirs);

        int index = 1;
        int size = asmrDirs.size();
        updateMessage(String.format("共找到%d个媒体，请耐心等待完成", size));
        updateProgress(size == 0 ? 1 : 0, size == 0 ? 1 : size);
        List<String> successList = new ArrayList<>();
        List<String> skipList = new ArrayList<>();
        List<String> failList = new ArrayList<>();

        for (String dirPath : asmrDirs) {
            if (isCancelled()) {
                log.debug("手动取消添加");
                saveLogs(createLogRow(successList, skipList, failList, rootPath, level, forceUpdate));
                return null;
            }

            // 暂停检查：若处于暂停状态则阻塞等待
            try {
                checkPause();
            } catch (InterruptedException e) {
                log.debug("任务在暂停中被取消");
                saveLogs(createLogRow(successList, skipList, failList, rootPath, level, forceUpdate));
                return null;
            }

            String dirName = extractDirName(dirPath);
            String workCode = WorkCodeUtils.extract(dirPath);
            AsmrWork workInDB = workService.getByFilepath(dirPath, storageType.getCode(), storageSource.getId());
            boolean status;
            boolean skipped = false;
            String resultMessage;
            String logReason = null;
            String failureReason = null;

            if (workInDB != null) {
                if (workInDB.getMediaLibraryId() != null && workInDB.getMediaLibraryId() != mediaLibraryId) {
                    status = true;
                    skipped = true;
                    resultMessage = "跳过（已属于其他媒体库）";
                    logReason = "跳过：路径已由媒体库「" + libraryName(libraryNames, workInDB.getMediaLibraryId())
                            + "」中的作品「" + describeWork(workInDB) + "」占用";
                } else if (forceUpdate) {
                    AsmrWork asmrWork = buildAsmrWork(dirPath, dirName, workCode, source);
                    asmrWork.setId(workInDB.getId());
                    status = workService.updateWork(asmrWork);
                    resultMessage = status ? "已更新" : "更新失败";
                    if (!status) {
                        failureReason = "更新失败：作品记录不存在或写入被拒绝";
                    }
                } else if (workInDB.getMediaLibraryId() == null) {
                    status = workService.updateWorkMediaLibrary(workInDB.getId(), mediaLibraryId);
                    resultMessage = status ? "已补充媒体库归属" : "补充归属失败";
                    if (!status) {
                        failureReason = "补充归属失败：作品记录不存在";
                    }
                } else {
                    status = true;
                    skipped = true;
                    resultMessage = "跳过已存在";
                    logReason = "跳过：媒体库「" + libraryName(libraryNames, mediaLibraryId)
                            + "」中已存在作品「" + describeWork(workInDB) + "」";
                }
            } else if (workCode != null) {
                AsmrWork codeMatch = workService.getByWorkId(workCode, storageType.getCode(), storageSource.getId());
                if (codeMatch != null && (codeMatch.getMediaLibraryId() == null
                        || codeMatch.getMediaLibraryId() == mediaLibraryId)) {
                    AsmrWork relocated = new AsmrWork();
                    relocated.setId(codeMatch.getId());
                    relocated.setFilepath(dirPath);
                    relocated.setParentPath(extractParentPath(dirPath));
                    relocated.setMediaLibraryId(mediaLibraryId);
                    relocated.setStorageSourceId(storageSource.getId());
                    status = workService.updateWorkLocationAndLibrary(relocated);
                    resultMessage = status ? "已按作品编号恢复归属" : "恢复归属失败";
                    if (status) {
                        logReason = "已按作品编号恢复归属：原路径「" + codeMatch.getFilepath() + "」";
                    } else {
                        failureReason = "恢复归属失败：作品记录不存在或路径冲突";
                    }
                } else if (codeMatch != null) {
                    status = true;
                    skipped = true;
                    resultMessage = "跳过（作品编号已属于其他媒体库）";
                    logReason = "跳过：作品编号 " + workCode + " 已由媒体库「"
                            + libraryName(libraryNames, codeMatch.getMediaLibraryId()) + "」中的作品「"
                            + describeWork(codeMatch) + "」占用（原路径：" + codeMatch.getFilepath() + "）";
                } else {
                    status = workService.addWork(buildAsmrWork(dirPath, dirName, workCode, source));
                    resultMessage = status ? "已导入" : "导入失败";
                    if (!status) {
                        failureReason = "导入失败：作品编号或路径与已有记录冲突";
                    }
                }
            } else {
                status = workService.addWork(buildAsmrWork(dirPath, dirName, null, source));
                resultMessage = status ? "已导入" : "导入失败";
                if (!status) {
                    failureReason = "导入失败：路径与已有记录冲突";
                }
            }
            if (status) {
                String logEntry = formatEntry(workCode, dirName, dirPath) + " | "
                        + (logReason != null ? logReason : resultMessage);
                if (skipped) {
                    skipList.add(logEntry);
                } else {
                    successList.add(logEntry);
                }
                updateMessage(String.format("%s：%s (%d/%d)", resultMessage, dirPath, index, size));
            } else {
                failList.add(formatEntry(workCode, dirName, dirPath) + " | " + failureReason);
                updateMessage(String.format("导入失败：%s，%s (%d/%d)", dirPath, failureReason, index, size));
            }
            updateProgress(index, size);
            index++;
        }

        saveLogs(createLogRow(successList, skipList, failList, rootPath, level, forceUpdate));
        String result = String.format("添加完成，成功%d个，跳过%d个，失败%d个",
                successList.size(), skipList.size(), failList.size());
        updateMessage(result);
        return result;
    }

    /**
     * 构造新增或强制更新所需的 AsmrWork 对象，仅在需要写入作品内容时调用。
     */
    private AsmrWork buildAsmrWork(String dirPath, String dirName, String workCode, MediaSource source) {
        AsmrWork asmrWork = new AsmrWork();
        asmrWork.setFilepath(dirPath);
        asmrWork.setParentPath(extractParentPath(dirPath));
        asmrWork.setTitle(dirName);
        asmrWork.setStorageType(storageType.getCode());
        asmrWork.setStorageSourceId(storageSource.getId());
        asmrWork.setMediaLibraryId(mediaLibraryId);
        if (titleCN) {
            asmrWork.setTitleCN(dirName);
        }
        if (dirName.contains("字幕") || hasSubtitleFile(dirPath, source)) {
            asmrWork.setSubtitle(true);
        }
        if (workCode != null) {
            asmrWork.setWorkId(workCode);
        }
        return asmrWork;
    }

    /**
     * 检查目录下是否存在字幕文件（.lrc 或 .vtt），通过 MediaSource 查找。
     */
    private boolean hasSubtitleFile(String dirPath, MediaSource source) {
        List<AsmrFile> files = source.listFilesRecursive(dirPath, new String[]{"lrc", "vtt"});
        return !files.isEmpty();
    }

    /**
     * 从路径中提取目录名。目录路径由父路径和名称拼接而来，取最后一段即可，无需再次列目录。
     */
    private String extractDirName(String dirPath) {
        String normalized = dirPath.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : dirPath;
    }

    /**
     * 从路径中提取父目录路径。
     */
    private String extractParentPath(String dirPath) {
        String normalized = dirPath.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash > 0) {
            return normalized.substring(0, lastSlash);
        }
        return null;
    }

    /**
     * 递归列举指定层级下的所有子目录。
     *
     * @param source       MediaSource 实例
     * @param currentPath  当前目录路径
     * @param currentDepth 当前递归深度（0 起）
     * @param targetDepth  目标深度
     * @param result       收集结果
     */
    private void collectDirsAtDepth(MediaSource source, String currentPath, int currentDepth, int targetDepth, List<String> result) {
        if (currentDepth == targetDepth) {
            result.add(currentPath);
            return;
        }
        List<AsmrFile> files = source.listFiles(currentPath);
        for (AsmrFile file : files) {
            if (file.isDir()) {
                collectDirsAtDepth(source, file.getFilepath(), currentDepth + 1, targetDepth, result);
            }
        }
    }

    /**
     * 格式化单条日志记录：作品编号 | 标题 | 路径。
     */
    private String formatEntry(String workId, String title, String dirPath) {
        return String.format("%-12s | %-30s | %s",
                workId != null ? workId : "无作品编号",
                title != null ? title : "",
                dirPath);
    }

    private String libraryName(Map<Integer, String> libraryNames, Integer libraryId) {
        if (libraryId == null) {
            return "未归属媒体库";
        }
        return libraryNames.getOrDefault(libraryId, "未知媒体库(#" + libraryId + ")");
    }

    private String describeWork(AsmrWork work) {
        String title = work.getTitle() == null || work.getTitle().isBlank() ? "(无标题)" : work.getTitle();
        return work.getWorkId() == null ? title : work.getWorkId() + " " + title;
    }

    /**
     * 创建日志文本，按成功/跳过/失败分类输出，每条包含作品编号、标题、路径。
     */
    private String createLogRow(List<String> successList, List<String> skipList,
                                List<String> failList, String rootPath, int level, boolean forceUpdate) {
        StringBuilder builder = new StringBuilder();
        builder.append("扫描开始 - 媒体库: ").append(rootPath)
                .append(", 层级: ").append(level)
                .append(", 强制更新: ").append(forceUpdate ? "是" : "否")
                .append(System.lineSeparator());

        builder.append("==============================").append(System.lineSeparator());
        builder.append(String.format("添加完成，成功%d个，跳过%d个，失败%d个",
                successList.size(), skipList.size(), failList.size())).append(System.lineSeparator());

        builder.append("==============================").append(System.lineSeparator());
        builder.append("成功：").append(System.lineSeparator());
        if (successList.isEmpty()) {
            builder.append("(无)").append(System.lineSeparator());
        } else {
            builder.append(String.join(System.lineSeparator(), successList)).append(System.lineSeparator());
        }

        if (!skipList.isEmpty()) {
            builder.append("==============================").append(System.lineSeparator());
            builder.append("跳过的数据：").append(System.lineSeparator());
            builder.append(String.join(System.lineSeparator(), skipList)).append(System.lineSeparator());
        }
        if (!failList.isEmpty()) {
            builder.append("==============================").append(System.lineSeparator());
            builder.append("失败的数据：").append(System.lineSeparator());
            builder.append(String.join(System.lineSeparator(), failList)).append(System.lineSeparator());
        }
        return builder.toString();
    }

    /**
     * 保存日志到文件，文件名带时间戳。
     * 保存后自动清理旧日志，最多保留 5 个。
     */
    private void saveLogs(String row) {
        File logFolder = AppPaths.importLogsDir();
        if (!logFolder.exists() && !logFolder.mkdirs()) {
            log.error("无法创建日志目录: {}", logFolder.getAbsolutePath());
            return;
        }
        String formattedTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
        File logFile = new File(logFolder, formattedTime + ".txt");
        try (FileWriter writer = new FileWriter(logFile, StandardCharsets.UTF_8)) {
            writer.write(row);
            logFilepath = logFile.getAbsolutePath();
            log.info("日志成功写入: {}", logFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("日志导出错误: {}", e.getMessage(), e);
        }
        cleanOldLogs(logFolder);
    }

    /**
     * 清理旧日志文件，保留最新的 5 个，按修改时间排序删除最旧的。
     */
    private void cleanOldLogs(File logFolder) {
        File[] files = logFolder.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null || files.length <= 5) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int toDelete = files.length - 5;
        for (int i = 0; i < toDelete; i++) {
            if (files[i].delete()) {
                log.info("已删除旧日志文件: {}", files[i].getName());
            } else {
                log.warn("无法删除旧日志文件: {}", files[i].getAbsolutePath());
            }
        }
    }

    /**
     * 打开日志文件（额外操作回调）。
     */
    private void openLogFile() {
        if (logFilepath == null) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(new File(logFilepath));
            }
        } catch (IOException e) {
            log.error("打开日志文件失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public String getLogFilePath() {
        return logFilepath;
    }
}
