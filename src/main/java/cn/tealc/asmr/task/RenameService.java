package cn.tealc.asmr.task;

import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.source.StorageType;
import cn.tealc.asmr.utils.URLUtils;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 批量重命名作品目录的后台任务。
 */
public class RenameService extends Service<RenameService.RenameBatchResult> {
    private final String formatSampleText;
    private final boolean preferChineseTitle;
    private List<AsmrWork> works = List.of();

    public RenameService(String formatSampleText, boolean preferChineseTitle) {
        this.formatSampleText = formatSampleText;
        this.preferChineseTitle = preferChineseTitle;
    }

    public void setList(List<AsmrWork> works) {
        this.works = works == null ? List.of() : List.copyOf(works);
    }

    public record RenamePlan(String name, String message, boolean skipped) {
        public boolean executable() {
            return name != null;
        }
    }

    public record RenamedWork(AsmrWork work, String originalPath, String renamedPath) {
    }

    public record RenameBatchResult(List<RenamedWork> renamedWorks, List<String> failures, int skippedCount) {
        public RenameBatchResult {
            renamedWorks = Collections.unmodifiableList(new ArrayList<>(renamedWorks));
            failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }
    }

    @Override
    protected Task<RenameBatchResult> createTask() {
        return new Task<>() {
            @Override
            protected RenameBatchResult call() {
                List<RenamedWork> renamedWorks = new ArrayList<>();
                List<String> failures = new ArrayList<>();
                Set<String> targetPaths = new HashSet<>();
                int skipped = 0;
                int size = works.size();
                int index = 0;

                for (AsmrWork work : works) {
                    if (isCancelled()) {
                        break;
                    }

                    String originalPath = work.getFilepath();
                    RenamePlan plan = buildRenamePlan(work, formatSampleText, preferChineseTitle);
                    if (!plan.executable()) {
                        if (plan.skipped()) {
                            skipped++;
                        } else {
                            failures.add(displayName(work) + "：" + plan.message());
                        }
                        updateProgress(++index, size);
                        continue;
                    }
                    if (originalPath == null) {
                        failures.add(displayName(work) + "：作品目录不存在或无法访问");
                        updateProgress(++index, size);
                        continue;
                    }
                    try {
                        MediaSource source = MediaSourceFactory.forWork(work);
                        if (!source.exists(originalPath) || !source.isDirectory(originalPath)) {
                            failures.add(displayName(work) + "：作品目录不存在或无法访问");
                            updateProgress(++index, size);
                            continue;
                        }

                        String oldName = fileName(originalPath);
                        if (plan.name().equals(oldName)) {
                            skipped++;
                            updateProgress(++index, size);
                            continue;
                        }

                        String renamedPath = parentPath(source, originalPath) + pathSeparator(originalPath) + plan.name();
                        String normalizedTargetPath = normalizePath(renamedPath);
                        if (!targetPaths.add(normalizedTargetPath)) {
                            failures.add(displayName(work) + "：与其他作品生成了相同的目标名称");
                            updateProgress(++index, size);
                            continue;
                        }
                        if (source.exists(renamedPath)) {
                            failures.add(displayName(work) + "：目标目录已存在");
                            updateProgress(++index, size);
                            continue;
                        }
                        if (!source.rename(originalPath, plan.name())) {
                            failures.add(displayName(work) + "：目录重命名失败");
                            updateProgress(++index, size);
                            continue;
                        }

                        renamedWorks.add(new RenamedWork(work, originalPath, renamedPath));
                    } catch (RuntimeException exception) {
                        failures.add(displayName(work) + "：" + errorMessage(exception));
                    }
                    updateProgress(++index, size);
                }
                return new RenameBatchResult(renamedWorks, failures, skipped);
            }
        };
    }

    public static RenamePlan buildRenamePlan(AsmrWork work, String format, boolean preferChineseTitle) {
        if (work == null || format == null || format.isBlank()) {
            return new RenamePlan(null, "命名格式不能为空", false);
        }

        String name = format;
        if (name.contains("workId")) {
            if (isBlank(work.getWorkId())) {
                return new RenamePlan(null, "缺少作品编号，已跳过", true);
            }
            name = name.replace("workId", work.getWorkId());
        }
        name = replaceOrRemove(name, "title", preferredTitle(work, preferChineseTitle));
        name = replaceOrRemove(name, "cv", trimTrailingSeparator(work.getVas()));
        name = replaceOrRemove(name, "circle", work.getCircle());
        name = replaceOrRemove(name, "subtitle", Boolean.TRUE.equals(work.getSubtitle()) ? "字幕" : null);
        name = name.replaceAll("[\\s\\\\/:*?<>|{}]+", "").replace("\"", "").trim();
        if (name.isBlank()) {
            return new RenamePlan(null, "生成的目录名称为空", false);
        }
        return new RenamePlan(name, null, false);
    }

    private static String replaceOrRemove(String value, String token, String replacement) {
        if (!value.contains(token)) {
            return value;
        }
        if (!isBlank(replacement)) {
            return value.replace(token, replacement);
        }
        return Pattern.compile("\\{[^{}]*" + Pattern.quote(token) + "[^{}]*[}]").matcher(value).replaceAll("");
    }

    private static String preferredTitle(AsmrWork work, boolean preferChineseTitle) {
        String primary = preferChineseTitle ? work.getTitleCN() : work.getTitle();
        String fallback = preferChineseTitle ? work.getTitle() : work.getTitleCN();
        return isBlank(primary) ? fallback : primary;
    }

    private static String trimTrailingSeparator(String value) {
        return isBlank(value) ? null : value.replaceAll("[,，、;；\\s]+$", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String displayName(AsmrWork work) {
        return !isBlank(work.getWorkId()) ? work.getWorkId() : fileName(work.getFilepath());
    }

    private static String errorMessage(Throwable exception) {
        return exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "重命名失败" : exception.getMessage();
    }

    private static String parentPath(MediaSource source, String path) {
        if (source.getType() == StorageType.LOCAL) {
            return URLUtils.getParent(path, null);
        }
        int separator = path.lastIndexOf('/');
        return separator <= 0 ? "/" : path.substring(0, separator);
    }

    private static String fileName(String path) {
        if (path == null) {
            return "未知作品";
        }
        int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return separator >= 0 ? path.substring(separator + 1) : path;
    }

    private static String pathSeparator(String path) {
        return path != null && path.contains("\\") ? "\\" : "/";
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
