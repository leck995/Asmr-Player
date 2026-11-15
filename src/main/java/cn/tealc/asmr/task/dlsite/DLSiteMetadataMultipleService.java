package cn.tealc.asmr.task.dlsite;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.service.DictionaryService;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.model.ResponseBody;
import cn.tealc.asmr.task.background.BackgroundTask;
import cn.tealc.asmr.task.background.TaskAction;
import cn.tealc.asmr.task.background.TaskType;
import cn.tealc.asmr.utils.AppPaths;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * 批量 DLSite 元数据同步任务。
 * <p>
 * 内部使用 5 线程池并发刮削，继承 {@link BackgroundTask} 统一由 TaskManager 管理。
 * 支持取消（覆写 cancel 时关闭线程池），不支持暂停和重试。
 * 按媒体库路径去重，防止同一媒体库并发同步。
 *
 * @author Leck
 */
public class DLSiteMetadataMultipleService extends BackgroundTask<AsmrWork> {
    private static final Logger logger = LoggerFactory.getLogger(DLSiteMetadataMultipleService.class);

    private final List<AsmrWork> list;
    private final String libraryPath;
    private final HttpClient client;
    private final Set<String> categories = new HashSet<>();
    private final Set<String> vas = new HashSet<>();
    private final Set<String> circles = new HashSet<>();

    private final ExecutorService threadPool = Executors.newFixedThreadPool(5);
    private Map<Future<ResponseBody<AsmrWork>>, AsmrWork> futures;
    private String logFilepath;
    private String completionSummary = "尚未完成";

    /**
     * 创建批量同步任务。
     *
     * @param list        要同步的作品列表
     * @param libraryPath 媒体库路径，用于去重
     */
    public DLSiteMetadataMultipleService(List<AsmrWork> list, String libraryPath) {
        super("批量刮削元数据", TaskType.SCRAPE_META, false, null);
        this.list = list;
        this.libraryPath = libraryPath;
        String host = Config.setting.proxyHost.get();
        String port = Config.setting.proxyPort.get();
        if (Config.setting.proxyModel.get() && host != null && port != null) {
            client = HttpClient.newBuilder().proxy(ProxySelector.of(new InetSocketAddress(host, Integer.parseInt(port)))).build();
        } else {
            client = HttpClient.newHttpClient();
        }
        addExtraAction(new TaskAction("打开日志", Material2OutlinedAL.DESCRIPTION, this::openLogFile));
    }

    /**
     * 去重键：按媒体库路径，防止同一媒体库并发同步。
     */
    @Override
    public String deduplicationKey() {
        return "SCRAPE:" + normalize(libraryPath);
    }

    private String normalize(String p) {
        return p == null ? "" : p.replace('\\', '/').replaceAll("/+$", "");
    }

    @Override
    protected AsmrWork call() {
        futures = new LinkedHashMap<>();
        WorkService workService = DI.get(WorkService.class);
        int processed = 0;
        int size = list.size();
        updateProgress(size == 0 ? 1 : 0, size == 0 ? 1 : size);
        updateMessage(String.format("共%d个作品待同步", size));
        List<String> successList = new ArrayList<>();
        List<String> skipList = new ArrayList<>();
        List<String> failList = new ArrayList<>();
        try {
            if (size == 0) {
                completionSummary = "没有可同步的作品";
                updateProgress(1, 1);
                updateMessage(completionSummary);
                return null;
            }
            for (AsmrWork asmrWork : list) {
                if (isCancelled()) {
                    break;
                }
                if (asmrWork.getWorkId() == null || asmrWork.getWorkId().isBlank()) {
                    skipList.add(formatEntry(asmrWork, "未提供作品编号"));
                    updateMessage(String.format("跳过无作品编号 (%d/%d)", ++processed, size));
                    updateProgress(processed, size);
                    continue;
                }
                if (Config.setting.syncSkipAlreadyModel.get() && asmrWork.getSources() != null) {
                    skipList.add(formatEntry(asmrWork, "已存在同步来源"));
                    updateMessage(String.format("跳过已同步：%s (%d/%d)", asmrWork.getWorkId(), ++processed, size));
                    updateProgress(processed, size);
                    continue;
                }
                DLSiteThread dlSiteThread = new DLSiteThread(client, asmrWork);
                futures.put(threadPool.submit(dlSiteThread), asmrWork);
            }

            for (Map.Entry<Future<ResponseBody<AsmrWork>>, AsmrWork> entry : futures.entrySet()) {
                if (isCancelled()) {
                    break;
                }
                AsmrWork originalWork = entry.getValue();
                Future<ResponseBody<AsmrWork>> future = entry.getKey();
                try {
                    if (future.isCancelled()) {
                        failList.add(formatEntry(originalWork, "同步任务已取消"));
                    } else {
                        ResponseBody<AsmrWork> responseBody = future.get();
                        if (responseBody != null && Integer.valueOf(ResponseBody.StatusCode.SUCCESS).equals(responseBody.getCode())) {
                            AsmrWork work = responseBody.getData();
                            if (work != null) {
                                if (work.getCategories() != null) {
                                    categories.addAll(Arrays.asList(work.getCategories().split(",")));
                                }
                                if (work.getVas() != null) {
                                    vas.addAll(Arrays.asList(work.getVas().split(",")));
                                }
                                if (work.getCircle() != null) {
                                    circles.add(work.getCircle());
                                }
                                if (workService.updateWork(work)) {
                                    successList.add(formatEntry(work, "同步成功"));
                                    updateValue(work);
                                    updateMessage(String.format("已同步：%s (%d/%d)", work.getWorkId(), processed + 1, size));
                                } else {
                                    failList.add(formatEntry(originalWork, "数据库更新失败"));
                                }
                            } else {
                                failList.add(formatEntry(originalWork, "未返回作品数据"));
                            }
                        } else {
                            failList.add(formatEntry(originalWork, responseBody == null
                                    ? "远程接口未返回结果"
                                    : defaultReason(responseBody.getMsg(), "远程接口返回失败")));
                        }
                    }
                } catch (ExecutionException e) {
                    failList.add(formatEntry(originalWork, defaultReason(messageOf(e.getCause()), "同步执行异常")));
                    logger.error("同步 {} 失败", originalWork.getWorkId(), e);
                } catch (RuntimeException e) {
                    failList.add(formatEntry(originalWork, defaultReason(e.getMessage(), "同步处理异常")));
                    logger.error("处理同步结果 {} 失败", originalWork.getWorkId(), e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failList.add(formatEntry(originalWork, "同步线程被中断"));
                    break;
                }
                processed++;
                updateProgress(processed, size);
            }

            saveTag();
        } finally {
            if (size > 0) {
                completionSummary = String.format("同步完成，成功%d个，跳过%d个，失败%d个",
                        successList.size(), skipList.size(), failList.size());
            }
            saveLogs(createLogRow(successList, skipList, failList));
            updateMessage(completionSummary);
            shutdownThreadPool();
        }
        return null;
    }

    /**
     * 覆写 cancel：在取消 Task 的同时关闭线程池。
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        shutdownThreadPool();
        return super.cancel(mayInterruptIfRunning);
    }

    public void shutdown() {
        cancel(true);
        threadPool.shutdownNow();
    }

    public void shutdownThreadPool() {
        if (threadPool == null || threadPool.isShutdown()) {
            return;
        }
        if (futures != null) {
            futures.keySet().forEach(future -> future.cancel(true));
        }
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("Thread pool did not terminate gracefully");
            }
        } catch (InterruptedException ie) {
            threadPool.shutdownNow();
            logger.warn("Thread pool shutdown interrupted", ie);
            Thread.currentThread().interrupt();
        } finally {
            if (futures != null) {
                futures.clear();
            }
        }
    }

    /**
     * 将标签持久化保存
     */
    private void saveTag() {
        DictionaryService dictService = DI.get(DictionaryService.class);
        for (String category : categories) {
            dictService.addCategory(category);
        }
        for (String circle : circles) {
            dictService.addCircle(circle);
        }
        for (String va : vas) {
            dictService.addVa(va);
        }
    }

    private String formatEntry(AsmrWork work, String result) {
        return String.format("%s | %s | %s | %s",
                safe(work.getWorkId(), "无作品编号"),
                safe(work.getTitle(), ""),
                safe(work.getFilepath(), ""),
                result);
    }

    private String createLogRow(List<String> successList, List<String> skipList, List<String> failList) {
        String lineSeparator = System.lineSeparator();
        StringBuilder builder = new StringBuilder();
        builder.append("元数据同步 - 媒体库: ").append(libraryPath).append(lineSeparator);
        builder.append("==============================").append(lineSeparator);
        builder.append(completionSummary).append(lineSeparator);
        appendSection(builder, "同步成功：", successList, lineSeparator);
        appendSection(builder, "跳过项：", skipList, lineSeparator);
        appendSection(builder, "同步失败：", failList, lineSeparator);
        return builder.toString();
    }

    private void appendSection(StringBuilder builder, String title, List<String> entries, String lineSeparator) {
        builder.append("==============================").append(lineSeparator);
        builder.append(title).append(lineSeparator);
        builder.append(entries.isEmpty() ? "(无)" : String.join(lineSeparator, entries)).append(lineSeparator);
    }

    private void saveLogs(String content) {
        File logFolder = AppPaths.syncLogsDir();
        if (!logFolder.exists() && !logFolder.mkdirs()) {
            logger.error("无法创建同步日志目录: {}", logFolder.getAbsolutePath());
            return;
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
        File logFile = new File(logFolder, timestamp + ".txt");
        try (FileWriter writer = new FileWriter(logFile, StandardCharsets.UTF_8)) {
            writer.write(content);
            logFilepath = logFile.getAbsolutePath();
        } catch (IOException e) {
            logger.error("写入同步日志失败: {}", e.getMessage(), e);
        }
        cleanOldLogs(logFolder);
    }

    private void cleanOldLogs(File logFolder) {
        File[] files = logFolder.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null || files.length <= 5) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - 5; i++) {
            if (!files[i].delete()) {
                logger.warn("无法删除旧同步日志: {}", files[i].getAbsolutePath());
            }
        }
    }

    private void openLogFile() {
        if (logFilepath == null) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(new File(logFilepath));
            }
        } catch (IOException e) {
            logger.error("打开同步日志失败: {}", e.getMessage(), e);
        }
    }

    private String messageOf(Throwable throwable) {
        return throwable == null ? null : throwable.getMessage();
    }

    private String defaultReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    public String getLogFilePath() {
        return logFilepath;
    }

    @Override
    public String getCompletionSummary() {
        return completionSummary;
    }
}
