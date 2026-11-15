package cn.tealc.asmr.service;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.source.StorageType;
import cn.tealc.asmr.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件操作服务。
 * <p>
 * 封装需要屏蔽来源差异（本地/远程）的文件操作：删除、重命名、存在性检查、系统打开、
 * 设为封面、设为背景图等。通过 {@link MediaSource} 完成实际文件操作，
 * 同时协调 {@link WorkService} 同步数据库。
 *
 * @author Leck
 */
public class FileOperationService {

    private static final Logger log = LoggerFactory.getLogger(FileOperationService.class);

    /** 封面支持的图片扩展名，按 CoverUtils 查找顺序排列 */
    private static final String[] COVER_EXTENSIONS = {"png", "jpg", "jpeg"};
    private static final Set<String> COVER_FILE_NAMES = Set.of(
            "cover-main.png", "cover-main.jpg", "cover-main.jpeg",
            "cover-thumb.png", "cover-thumb.jpg", "cover-thumb.jpeg");

    /**
     * 删除作品。
     * <p>
     * 未选择删除存储时，仅移除数据库记录；选择删除存储时，先删除实际存储中的作品，
     * 再移除数据库记录，避免文件删除失败后丢失作品记录。
     *
     * @param work          要删除的作品
     * @param deleteStorage 是否同时彻底删除存储中的作品
     * @return 操作结果；失败时 message 为可展示的原因
     */
    public OperationResult deleteWork(AsmrWork work, boolean deleteStorage) {
        if (work == null || work.getId() <= 0) throw new IllegalArgumentException("作品记录无效");
        WorkService workService = DI.get(WorkService.class);
        if (!deleteStorage) {
            workService.deleteWork(work);
            return OperationResult.ok();
        }
        try {
            MediaSource source = MediaSourceFactory.forWork(work);
            if (!source.delete(work.getFilepath())) {
                log.warn("作品文件删除失败，保留数据库记录: {}", work.getFilepath());
                return OperationResult.failure("存储中的作品删除失败");
            }
        } catch (RuntimeException exception) {
            log.warn("作品文件删除失败，保留数据库记录: {}", work.getFilepath(), exception);
            return OperationResult.failure("删除存储中的作品失败：" + errorMessage(exception));
        }
        workService.deleteWork(work);
        return OperationResult.ok();
    }

    /**
     * 删除单个文件。
     *
     * @param work      所属作品（用于判断来源类型）
     * @param file      要删除的文件
     * @return 操作结果；失败时 message 为可展示的原因
     */
    public OperationResult deleteFile(AsmrWork work, AsmrFile file) {
        try {
            MediaSource source = MediaSourceFactory.forWork(work);
            if (!source.delete(file.getFilepath())) {
                return OperationResult.failure("删除失败");
            }
        } catch (RuntimeException exception) {
            log.warn("删除文件失败: {}", file.getFilepath(), exception);
            return OperationResult.failure("删除失败：" + errorMessage(exception));
        }
        invalidateBackgrounds(work);
        return OperationResult.ok();
    }

    /**
     * 删除作品目录中的主封面与缩略封面，不会处理其他文件。
     *
     * @param work 所属作品
     * @return 成功删除的封面文件数量
     */
    public int deleteCoverFiles(AsmrWork work) {
        if (work == null || work.getFilepath() == null || work.getFilepath().isBlank()) {
            return 0;
        }
        try {
            MediaSource source = MediaSourceFactory.forWork(work);
            int deletedCount = 0;
            for (AsmrFile file : source.listFiles(work.getFilepath())) {
                if (file == null || file.isDir() || file.getFilename() == null
                        || !COVER_FILE_NAMES.contains(file.getFilename().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                try {
                    if (source.delete(file.getFilepath())) {
                        deletedCount++;
                    } else {
                        log.warn("删除作品封面失败: {}", file.getFilepath());
                    }
                } catch (RuntimeException exception) {
                    log.warn("删除作品封面失败: {}", file.getFilepath(), exception);
                }
            }
            return deletedCount;
        } catch (RuntimeException exception) {
            log.warn("读取作品封面列表失败: {}", work.getFilepath(), exception);
            return 0;
        }
    }

    /**
     * 检查作品文件是否存在。
     *
     * @param work 要检查的作品
     * @return 文件存在返回 true
     */
    public boolean exists(AsmrWork work) {
        MediaSource source = MediaSourceFactory.forWork(work);
        try {
            return source.exists(work.getFilepath());
        } catch (RuntimeException exception) {
            log.warn("检查作品路径失败，暂按存在处理: {}", work.getFilepath(), exception);
            return true;
        }
    }

    /**
     * 将作品目录移动到指定目录，并同步更新作品路径。
     *
     * @param work               要移动的作品
     * @param destinationDirPath 目标目录
     * @return 移动结果
     */
    public MoveWorkResult moveWork(AsmrWork work, String destinationDirPath) {
        return moveWork(work, destinationDirPath, null, null);
    }

    /**
     * 将作品目录移动到指定目录，并同步更新作品路径与媒体库归属。
     *
     * @param work                 要移动的作品
     * @param destinationDirPath   目标目录
     * @param targetMediaLibraryId 目标媒体库 ID；为 null 时保持原归属
     * @param targetStorageSourceId 目标媒体库所属存储源 ID；为 null 时保持原存储源
     * @return 移动结果
     */
    public MoveWorkResult moveWork(AsmrWork work, String destinationDirPath,
                                   Integer targetMediaLibraryId, Integer targetStorageSourceId) {
        if (work == null || work.getFilepath() == null || work.getFilepath().isBlank()) {
            return MoveWorkResult.failure("作品路径无效");
        }
        if (destinationDirPath == null || destinationDirPath.isBlank()) {
            return MoveWorkResult.failure("请选择目标目录");
        }

        MediaSource source;
        try {
            source = MediaSourceFactory.forWork(work);
        } catch (RuntimeException exception) {
            return MoveWorkResult.failure("存储源不可用：" + errorMessage(exception));
        }
        String sourcePath = work.getFilepath();
        boolean ignoreCase = source.getType() == StorageType.LOCAL;
        try {
            if (!source.isDirectory(sourcePath)) {
                return MoveWorkResult.failure("作品目录不存在或不可移动");
            }
            String sourceParentPath = storageParentPath(source, sourcePath);
            String normalizedSourcePath = normalizePath(sourcePath, ignoreCase);
            String normalizedDestinationPath = normalizePath(destinationDirPath, ignoreCase);
            if (normalizePath(sourceParentPath, ignoreCase).equals(normalizedDestinationPath)) {
                return MoveWorkResult.failure("目标目录与当前目录相同");
            }
            if (normalizedDestinationPath.equals(normalizedSourcePath)
                    || normalizedDestinationPath.startsWith(normalizedSourcePath + "/")) {
                return MoveWorkResult.failure("不能将作品移动到自身或其子目录");
            }

            String targetPath = appendPath(destinationDirPath, fileName(sourcePath));
            if (source.exists(targetPath)) {
                return MoveWorkResult.failure("目标目录已存在同名作品");
            }
            if (!source.move(sourcePath, destinationDirPath)) {
                return MoveWorkResult.failure("文件移动失败");
            }

            String previousParentPath = work.getParentPath();
            Integer previousMediaLibraryId = work.getMediaLibraryId();
            Integer previousStorageSourceId = work.getStorageSourceId();
            work.setFilepath(targetPath);
            work.setParentPath(destinationDirPath);
            if (targetMediaLibraryId != null) {
                work.setMediaLibraryId(targetMediaLibraryId);
            }
            if (targetStorageSourceId != null) {
                work.setStorageSourceId(targetStorageSourceId);
            }
            boolean updated = targetMediaLibraryId == null
                    ? DI.get(WorkService.class).updateWorkFilePathAndParent(work)
                    : DI.get(WorkService.class).updateWorkLocationAndLibrary(work);
            if (updated) {
                invalidateBackgrounds(work);
                return MoveWorkResult.success(targetPath);
            }

            log.error("作品移动后数据库路径更新失败，尝试回滚: {} -> {}", sourcePath, targetPath);
            work.setFilepath(sourcePath);
            work.setParentPath(previousParentPath);
            work.setMediaLibraryId(previousMediaLibraryId);
            work.setStorageSourceId(previousStorageSourceId);
            boolean rolledBack = source.move(targetPath, sourceParentPath);
            if (!rolledBack) {
                return MoveWorkResult.failure("文件已移动，但数据库路径更新失败且自动回滚失败");
            }
            return MoveWorkResult.failure("数据库路径更新失败，已恢复原位置");
        } catch (RuntimeException exception) {
            log.warn("移动作品失败: {} -> {}", sourcePath, destinationDirPath, exception);
            return MoveWorkResult.failure("移动失败：" + errorMessage(exception));
        }
    }

    /**
     * 批量应用列表中的名称预览。先使用临时名称腾出目标名称，再写入最终名称，
     * 从而支持同级文件或目录之间的名称互换。
     *
     * @param work  所属作品
     * @param files 当前目录的完整列表
     * @return 批量重命名结果
     */
    public RenameBatchResult renamePreviewNames(AsmrWork work, List<AsmrFile> files) {
        if (work == null || files == null || files.isEmpty()) {
            return new RenameBatchResult(0, List.of());
        }
        MediaSource source;
        try {
            source = MediaSourceFactory.forWork(work);
        } catch (RuntimeException exception) {
            return new RenameBatchResult(0, List.of("存储源不可用：" + errorMessage(exception)));
        }
        List<RenameEntry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> currentNames = new HashSet<>();
        for (AsmrFile file : files) {
            if (file != null && file.getFilename() != null) {
                currentNames.add(normalizeName(file.getFilename()));
            }
        }

        Map<String, RenameEntry> targetOwners = new HashMap<>();
        for (AsmrFile file : files) {
            if (file == null || file.getPreviewName() == null
                    || file.getPreviewName().isBlank() || file.getFilename().equals(file.getPreviewName())) {
                continue;
            }
            String targetName = file.getPreviewName().trim();
            if (!isValidName(targetName)) {
                errors.add(file.getFilename() + "：目标名称无效");
                continue;
            }
            RenameEntry entry = new RenameEntry(file, targetName);
            RenameEntry existing = targetOwners.putIfAbsent(normalizeName(targetName), entry);
            if (existing != null) {
                errors.add(file.getFilename() + "：与 " + existing.file().getFilename() + " 重名");
                continue;
            }
            entries.add(entry);
        }

        Set<String> sourceNames = new HashSet<>();
        for (RenameEntry entry : entries) {
            sourceNames.add(normalizeName(entry.file().getFilename()));
        }
        List<RenameEntry> executable = new ArrayList<>();
        for (RenameEntry entry : entries) {
            String target = normalizeName(entry.targetName());
            if (currentNames.contains(target) && !sourceNames.contains(target)) {
                errors.add(entry.file().getFilename() + "：目标名称已存在");
            } else {
                executable.add(entry);
            }
        }
        if (executable.isEmpty()) {
            return new RenameBatchResult(0, errors);
        }

        Set<String> occupiedNames = new HashSet<>(currentNames);
        List<RenameEntry> staged = new ArrayList<>();
        for (RenameEntry entry : executable) {
            String temporaryName = nextTemporaryName(occupiedNames);
            try {
                if (!source.rename(entry.file().getFilepath(), temporaryName)) {
                    errors.add(entry.file().getFilename() + "：无法写入临时名称");
                    rollbackTemporaryNames(source, staged, errors);
                    return new RenameBatchResult(0, errors);
                }
            } catch (RuntimeException exception) {
                errors.add(entry.file().getFilename() + "：写入临时名称失败，" + errorMessage(exception));
                rollbackTemporaryNames(source, staged, errors);
                return new RenameBatchResult(0, errors);
            }
            occupiedNames.add(normalizeName(temporaryName));
            entry.temporaryName = temporaryName;
            staged.add(entry);
        }

        int renamedCount = 0;
        for (RenameEntry entry : staged) {
            String temporaryPath = siblingPath(source, entry.file().getFilepath(), entry.temporaryName);
            try {
                if (!source.rename(temporaryPath, entry.targetName())) {
                    errors.add(entry.file().getFilename() + "：无法写入目标名称 " + entry.targetName());
                    log.warn("AI 批量重命名失败: {} -> {}", temporaryPath, entry.targetName());
                    if (!source.rename(temporaryPath, entry.file().getFilename())) {
                        errors.add(entry.file().getFilename() + "：无法恢复原名称，当前临时名称为 " + entry.temporaryName);
                        log.error("AI 批量重命名回滚失败: {} -> {}", temporaryPath, entry.file().getFilename());
                    }
                    continue;
                }
            } catch (RuntimeException exception) {
                errors.add(entry.file().getFilename() + "：重命名失败，" + errorMessage(exception));
                log.warn("AI 批量重命名失败: {} -> {}", temporaryPath, entry.targetName(), exception);
                try {
                    source.rename(temporaryPath, entry.file().getFilename());
                } catch (RuntimeException rollbackException) {
                    errors.add(entry.file().getFilename() + "：无法恢复原名称，当前临时名称为 " + entry.temporaryName);
                    log.error("AI 批量重命名回滚失败: {} -> {}", temporaryPath, entry.file().getFilename(), rollbackException);
                }
                continue;
            }
            renamedCount++;
        }
        if (renamedCount > 0) {
            invalidateBackgrounds(work);
        }
        return new RenameBatchResult(renamedCount, errors);
    }

    /**
     * 用系统默认程序打开文件或文件夹（仅本地来源有效，远程来源返回 false）。
     *
     * @param work 所属作品（用于判断来源类型）
     * @param path 要打开的路径
     * @return 打开成功返回 true
     */
    public boolean openInSystem(AsmrWork work, String path) {
        MediaSource source = MediaSourceFactory.forWork(work);
        return source.openInSystem(path);
    }

    /**
     * 将图片设为作品封面：先删除同目录下三种扩展名的旧封面，再将图片重命名为
     * {@code coverName.<ext>}（如 cover-main.jpg / cover-thumb.png）。
     *
     * @param work      所属作品（用于判断来源类型）
     * @param file      目标图片文件
     * @param coverName 封面名称前缀，cover-main（大封面）或 cover-thumb（小封面）
     * @return 成功返回新文件名，失败返回 null
     */
    public String setAsCover(AsmrWork work, AsmrFile file, String coverName) {
        try {
            MediaSource source = MediaSourceFactory.forWork(work);
            String ext = file.getType().toLowerCase();
            // 删除三种扩展名的旧封面，避免换扩展名后旧图仍被 CoverUtils 命中
            for (String e : COVER_EXTENSIONS) {
                String oldPath = siblingPath(source, file.getFilepath(), coverName + "." + e);
                if (source.exists(oldPath)) {
                    source.delete(oldPath);
                }
            }
            String newName = coverName + "." + ext;
            if (source.rename(file.getFilepath(), newName)) {
                invalidateBackgrounds(work);
                return newName;
            }
            log.warn("设为封面失败: {} -> {}", file.getFilepath(), newName);
            return null;
        } catch (RuntimeException exception) {
            log.warn("设为封面失败: {}", file.getFilepath(), exception);
            return null;
        }
    }

    /**
     * 将图片设为作品背景图：统计同目录下 background 开头的文件数，算出下一个编号，
     * 将图片重命名为 {@code backgroundNN.<ext>}。
     *
     * @param work 所属作品（用于判断来源类型）
     * @param file 目标图片文件
     * @return 成功返回新文件名，失败返回 null
     */
    public String setAsBackground(AsmrWork work, AsmrFile file) {
        try {
            MediaSource source = MediaSourceFactory.forWork(work);
            String dir = storageParentPath(source, file.getFilepath());
            long count = source.listFiles(dir).stream()
                    .map(AsmrFile::getFilename)
                    .filter(n -> n.startsWith("background"))
                    .count();
            String newName = String.format("background%02d." + file.getType().toLowerCase(), count + 1);
            if (source.rename(file.getFilepath(), newName)) {
                invalidateBackgrounds(work);
                return newName;
            }
            log.warn("设为背景失败: {} -> {}", file.getFilepath(), newName);
            return null;
        } catch (RuntimeException exception) {
            log.warn("设为背景失败: {}", file.getFilepath(), exception);
            return null;
        }
    }

    /**
     * 文件变动后清除作品的背景记录，下次进入重新扫描目录。
     */
    private void invalidateBackgrounds(AsmrWork work) {
        DI.get(BackgroundService.class).invalidate(work);
    }

    private static String fileName(String path) {
        int separatorIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return separatorIndex >= 0 ? path.substring(separatorIndex + 1) : path;
    }

    /**
     * 取作品目录的父路径。远程路径按 {@code /} 截取，根级条目的父目录是 {@code /}；
     * 本地路径继续使用 {@link URLUtils}，以保留 Windows 盘符处理。
     */
    private static String storageParentPath(MediaSource source, String path) {
        if (source.getType() == StorageType.LOCAL) {
            return URLUtils.getParent(path, null);
        }
        int separator = path.lastIndexOf('/');
        return separator <= 0 ? "/" : path.substring(0, separator);
    }

    private static String appendPath(String parentPath, String name) {
        String separator = parentPath.contains("/") ? "/" : "\\";
        return parentPath.endsWith(separator) ? parentPath + name : parentPath + separator + name;
    }

    private static String normalizePath(String path, boolean ignoreCase) {
        String normalized = path.replace('\\', '/').replaceAll("/+$", "");
        return ignoreCase ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    private static String errorMessage(Throwable exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "操作失败";
        }
        return exception.getMessage();
    }

    private static boolean isValidName(String name) {
        return !name.isBlank()
                && name.length() <= 255
                && !name.endsWith(".")
                && !name.endsWith(" ")
                && !".".equals(name)
                && !"..".equals(name)
                && name.chars().noneMatch(ch -> "\\/:*?\"<>|".indexOf(ch) >= 0);
    }

    private static String normalizeName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String nextTemporaryName(Set<String> occupiedNames) {
        String candidate;
        do {
            candidate = "__asmr_rename_" + UUID.randomUUID();
        } while (occupiedNames.contains(normalizeName(candidate)));
        return candidate;
    }

    private static String parentPath(String path) {
        return URLUtils.getParent(path, null);
    }

    private static String pathSeparator(String path) {
        return path.contains("/") ? "/" : "\\";
    }

    /**
     * 生成与给定文件同目录的目标路径；远程根级文件的父目录是 {@code /}。
     */
    private static String siblingPath(MediaSource source, String path, String name) {
        if (source.getType() != StorageType.LOCAL) {
            int separator = path.lastIndexOf('/');
            return separator <= 0 ? "/" + name : path.substring(0, separator + 1) + name;
        }
        return parentPath(path) + pathSeparator(path) + name;
    }

    private static void rollbackTemporaryNames(MediaSource source, List<RenameEntry> staged, List<String> errors) {
        for (int index = staged.size() - 1; index >= 0; index--) {
            RenameEntry entry = staged.get(index);
            String temporaryPath = siblingPath(source, entry.file().getFilepath(), entry.temporaryName);
            try {
                if (!source.rename(temporaryPath, entry.file().getFilename())) {
                    errors.add(entry.file().getFilename() + "：临时名称回滚失败");
                }
            } catch (RuntimeException exception) {
                errors.add(entry.file().getFilename() + "：临时名称回滚失败，" + errorMessage(exception));
            }
        }
    }

    private static final class RenameEntry {
        private final AsmrFile file;
        private final String targetName;
        private String temporaryName;

        private RenameEntry(AsmrFile file, String targetName) {
            this.file = file;
            this.targetName = targetName;
        }

        private AsmrFile file() {
            return file;
        }

        private String targetName() {
            return targetName;
        }
    }

    public record RenameBatchResult(int renamedCount, List<String> errors) {
    }

    /**
     * 文件操作结果：成功与否，以及可直接展示给用户的失败原因。
     */
    public record OperationResult(boolean success, String message) {
        public static OperationResult ok() {
            return new OperationResult(true, null);
        }

        public static OperationResult failure(String message) {
            return new OperationResult(false, message);
        }
    }

    /**
     * 作品移动结果。
     */
    public record MoveWorkResult(boolean success, String message, String targetPath) {
        public static MoveWorkResult success(String targetPath) {
            return new MoveWorkResult(true, "移动成功", targetPath);
        }

        public static MoveWorkResult failure(String message) {
            return new MoveWorkResult(false, message, null);
        }
    }
}
