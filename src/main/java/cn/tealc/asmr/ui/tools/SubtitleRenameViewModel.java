package cn.tealc.asmr.ui.tools;

import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.SupportedAudioFormat;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.mvvm.ViewModel;
import cn.tealc.asmr.service.BackgroundService;
import cn.tealc.asmr.source.MediaSource;
import cn.tealc.asmr.source.MediaSourceFactory;
import cn.tealc.asmr.source.StorageType;
import cn.tealc.asmr.source.local.LocalMediaSource;
import cn.tealc.asmr.ui.dialog.Alerts;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@link SubtitleRenameView} 的视图模型，扫描作品目录并按扩展名归类音声与字幕文件。
 * <p>
 * 列表通过 {@link MediaSource} 读取，本地与 OpenList 来源共用同一套配对与重命名逻辑；
 * 字幕优先取 {@code .lrc}，为空则依次回退 {@code .vtt}、{@code .srt}。
 *
 * @author Leck
 */
public class SubtitleRenameViewModel implements ViewModel {

    private final MediaSource source;
    private final AsmrWork work;
    private final String directoryPath;
    private final ObservableList<AsmrFile> audios = FXCollections.observableArrayList();
    private final ObservableList<AsmrFile> lrcs = FXCollections.observableArrayList();
    private final SimpleBooleanProperty subtitleNameFirst = new SimpleBooleanProperty(true);
    private final SimpleBooleanProperty loading = new SimpleBooleanProperty(true);
    private final SimpleBooleanProperty busy = new SimpleBooleanProperty(false);

    /**
     * 扫描 {@code directoryPath} 目录，异步填充音声与字幕列表。
     *
     * @param work          所属作品（用于获取存储来源）
     * @param directoryPath 作品所在目录
     */
    public SubtitleRenameViewModel(AsmrWork work, String directoryPath) {
        this.work = work;
        this.directoryPath = directoryPath;
        MediaSource resolved = null;
        RuntimeException failure = null;
        try {
            resolved = resolveSource(work);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        this.source = resolved;
        if (source == null) {
            loading.set(false);
            publishWarning("无法访问存储源：" + errorMessage(failure));
            return;
        }
        loadFiles();
    }

    private static MediaSource resolveSource(AsmrWork work) {
        if (work == null) {
            throw new IllegalArgumentException("作品记录无效");
        }
        if (work.getStorageSourceId() == null
                && (work.getStorageType() == null || work.getStorageType() == StorageType.LOCAL.getCode())) {
            return LocalMediaSource.INSTANCE;
        }
        return MediaSourceFactory.forWork(work);
    }

    /**
     * 读取目录并归类音声、字幕；读取期间 {@link #loadingProperty()} 为 true。
     */
    private void loadFiles() {
        loading.set(true);
        String path = directoryPath;
        Thread.startVirtualThread(() -> {
            try {
                List<AsmrFile> entries = source.listFiles(path).stream()
                        .filter(item -> item != null && !item.isDir())
                        .toList();
                List<AsmrFile> audioFiles = entries.stream()
                        .filter(item -> SupportedAudioFormat.contains(item.getType()))
                        .sorted(Comparator.comparing(AsmrFile::getFilename, String.CASE_INSENSITIVE_ORDER))
                        .toList();
                List<AsmrFile> subtitleFiles = subtitleFiles(entries, "lrc");
                if (subtitleFiles.isEmpty()) {
                    subtitleFiles = subtitleFiles(entries, "vtt");
                }
                if (subtitleFiles.isEmpty()) {
                    subtitleFiles = subtitleFiles(entries, "srt");
                }
                List<AsmrFile> finalSubtitles = subtitleFiles;
                Platform.runLater(() -> {
                    audios.setAll(audioFiles);
                    lrcs.setAll(finalSubtitles);
                    loading.set(false);
                });
            } catch (RuntimeException exception) {
                Platform.runLater(() -> {
                    loading.set(false);
                    publishWarning("读取目录失败：" + errorMessage(exception));
                });
            }
        });
    }

    private static List<AsmrFile> subtitleFiles(List<AsmrFile> entries, String extension) {
        return entries.stream()
                .filter(item -> extension.equalsIgnoreCase(item.getType()))
                .sorted(Comparator.comparing(AsmrFile::getFilename, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * 按选定方向批量重命名；音声与字幕数量不匹配时仅弹警告、不执行。
     * <p>
     * 重命名在虚拟线程执行，完成后刷新目录列表，失败项通过消息提示具体原因。
     */
    public void rename() {
        if (source == null || loading.get() || busy.get()) {
            return;
        }
        if (audios.isEmpty() || audios.size() != lrcs.size()) {
            Alerts.warning().title("提醒").message("音声与歌词的文件数量不匹配").cancel("确定").show();
            return;
        }

        List<RenamePair> pairs = new ArrayList<>();
        if (subtitleNameFirst.get()) {
            for (int i = 0; i < audios.size(); i++) {
                AsmrFile audio = audios.get(i);
                AsmrFile subtitle = lrcs.get(i);
                pairs.add(new RenamePair(audio, baseName(subtitle.getFilename()) + extension(audio.getFilename())));
            }
        } else {
            for (int i = 0; i < audios.size(); i++) {
                AsmrFile audio = audios.get(i);
                AsmrFile subtitle = lrcs.get(i);
                pairs.add(new RenamePair(subtitle, baseName(audio.getFilename()) + extension(subtitle.getFilename())));
            }
        }

        busy.set(true);
        Thread.startVirtualThread(() -> {
            int renamedCount = 0;
            int unchangedCount = 0;
            List<String> failures = new ArrayList<>();
            for (RenamePair pair : pairs) {
                if (pair.file().getFilename().equals(pair.newName())) {
                    unchangedCount++;
                    continue;
                }
                try {
                    if (source.rename(pair.file().getFilepath(), pair.newName())) {
                        renamedCount++;
                    } else {
                        failures.add(pair.file().getFilename() + "：重命名失败");
                    }
                } catch (RuntimeException exception) {
                    failures.add(pair.file().getFilename() + "：" + errorMessage(exception));
                }
            }
            if (renamedCount > 0 && work != null) {
                DI.get(BackgroundService.class).invalidate(work);
            }
            int renamed = renamedCount;
            int unchanged = unchangedCount;
            Platform.runLater(() -> {
                busy.set(false);
                if (failures.isEmpty()) {
                    publishInfo(renamed == 0
                            ? "所有文件已是目标名称，无需重命名"
                            : "重命名完成，共更新 " + renamed + " 个文件"
                            + (unchanged > 0 ? "，跳过 " + unchanged + " 个已符合目标名称的文件" : ""));
                } else {
                    int detailCount = Math.min(3, failures.size());
                    publishWarning("重命名完成 " + renamed + " 个，失败 " + failures.size()
                            + " 个：" + String.join("；", failures.subList(0, detailCount)));
                }
                loadFiles();
            });
        });
    }

    private static String baseName(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private static String errorMessage(Throwable exception) {
        return exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "操作失败" : exception.getMessage();
    }

    private static void publishWarning(String message) {
        EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(MessageType.WARNING, message));
    }

    private static void publishInfo(String message) {
        EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(MessageType.SUCCESS, message));
    }

    private record RenamePair(AsmrFile file, String newName) {
    }

    public SimpleBooleanProperty subtitleNameFirstProperty() {
        return subtitleNameFirst;
    }

    public boolean isLoading() {
        return loading.get();
    }

    public SimpleBooleanProperty loadingProperty() {
        return loading;
    }

    public boolean isBusy() {
        return busy.get();
    }

    public SimpleBooleanProperty busyProperty() {
        return busy;
    }

    public ObservableList<AsmrFile> getAudios() {
        return audios;
    }

    public ObservableList<AsmrFile> getLrcs() {
        return lrcs;
    }
}
