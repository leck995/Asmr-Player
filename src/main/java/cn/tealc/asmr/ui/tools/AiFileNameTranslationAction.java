package cn.tealc.asmr.ui.tools;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.model.translation.FileNameTranslationPreviewItem;
import cn.tealc.asmr.config.AiTranslationSettings;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.ai.AiTranslationService;
import cn.tealc.asmr.utils.FileNameUtils;
import javafx.animation.Animation;
import javafx.animation.RotateTransition;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件和目录名称的 AI 翻译预览动作，不会重命名或写入数据库。
 */
public final class AiFileNameTranslationAction {
    private final AiTranslationService translationService = DI.get(AiTranslationService.class);

    private AiFileNameTranslationAction() {
    }

    public static FontIcon createIdleIcon() {
        return new FontIcon(Material2OutlinedAL.G_TRANSLATE);
    }

    public static FontIcon createReadyIcon() {
        return new FontIcon(Material2OutlinedAL.CHECK);
    }

    public static void translate(List<AsmrFile> files, Button button, Runnable onSuccess) {
        new AiFileNameTranslationAction().run(files, button, onSuccess);
    }

    private void run(List<AsmrFile> files, Button button, Runnable onSuccess) {
        AiTranslationSettings settings = Config.setting.aiTranslation;
        if (!isConfigured(settings)) {
            notify(MessageType.WARNING, "请先在 设置 → AI 翻译 中配置接口、模型和 API Key");
            return;
        }
        Map<String, List<AsmrFile>> filesByBaseName = groupFilesByBaseName(files, false);
        Map<String, List<AsmrFile>> directoriesByName = groupFilesByBaseName(files, true);
        if (filesByBaseName.isEmpty() && directoriesByName.isEmpty()) {
            notify(MessageType.WARNING, "当前目录没有可翻译的文件或目录名称");
            return;
        }

        FontIcon loadingIcon = new FontIcon(Material2OutlinedMZ.REFRESH);
        RotateTransition rotation = new RotateTransition(Duration.millis(850), loadingIcon);
        rotation.setByAngle(360);
        rotation.setCycleCount(Animation.INDEFINITE);
        button.setGraphic(loadingIcon);
        button.setDisable(true);
        rotation.play();

        Task<List<FileNameTranslationPreviewItem>> task = new Task<>() {
            @Override
            protected List<FileNameTranslationPreviewItem> call() {
                List<FileNameTranslationPreviewItem> result = new java.util.ArrayList<>();
                result.addAll(translationService.previewFileNames(List.copyOf(filesByBaseName.keySet()), settings, settings.getApiKey()));
                result.addAll(translationService.previewFileNames(List.copyOf(directoriesByName.keySet()), settings, settings.getApiKey()));
                return result;
            }
        };
        task.setOnSucceeded(event -> {
            rotation.stop();
            button.setGraphic(createIdleIcon());
            button.setDisable(false);
            Map<String, FileNameTranslationPreviewItem> previews = new LinkedHashMap<>();
            for (FileNameTranslationPreviewItem item : task.getValue()) {
                previews.put(item.sourceBaseName(), item);
            }
            int translatedCount = applyPreviews(filesByBaseName, previews) + applyPreviews(directoriesByName, previews);
            if (translatedCount == 0) {
                notify(MessageType.WARNING, "AI 未返回可用的文件或目录名称翻译");
                return;
            }
            onSuccess.run();
            long prohibitedCount = task.getValue().stream().filter(AiFileNameTranslationAction::isProhibitedContent).count();
            if (prohibitedCount > 0) {
                notify(MessageType.WARNING, prohibitedCount
                        + " 个文件或目录名称被当前 AI 服务的内容安全策略拒绝，未显示其翻译预览；可在 设置 → AI 翻译 更换支持该内容的服务或模型");
            }
            long invalidCount = task.getValue().stream()
                    .filter(item -> !item.valid() && !isProhibitedContent(item))
                    .count();
            if (invalidCount > 0) {
                notify(MessageType.WARNING, invalidCount + " 个文件或目录名称未通过本地校验，未显示其翻译预览");
            }
        });
        task.setOnFailed(event -> {
            rotation.stop();
            button.setGraphic(createIdleIcon());
            button.setDisable(false);
            Throwable error = task.getException();
            notify(MessageType.ERROR, "AI 翻译失败：" + (error == null ? "未知错误" : error.getMessage()));
        });
        Thread.startVirtualThread(task);
    }

    private static Map<String, List<AsmrFile>> groupFilesByBaseName(List<AsmrFile> files, boolean directories) {
        Map<String, List<AsmrFile>> result = new LinkedHashMap<>();
        if (files == null) return result;
        for (AsmrFile file : files) {
            if (file == null || file.isDir() != directories || file.getFilename() == null || file.getFilename().isBlank()) continue;
            String displayName = file.getHideName();
            String sourceName = displayName == null || displayName.isBlank() ? file.getFilename() : displayName;
            String baseName = file.isDir() ? sourceName : FileNameUtils.getNameWithSuffix(sourceName);
            result.computeIfAbsent(baseName, ignored -> new java.util.ArrayList<>()).add(file);
        }
        return result;
    }

    private static int applyPreviews(Map<String, List<AsmrFile>> filesByBaseName,
                                     Map<String, FileNameTranslationPreviewItem> previews) {
        int count = 0;
        for (Map.Entry<String, List<AsmrFile>> entry : filesByBaseName.entrySet()) {
            FileNameTranslationPreviewItem preview = previews.get(entry.getKey());
            if (preview == null || !preview.valid()) continue;
            for (AsmrFile file : entry.getValue()) {
                String previewName = file.isDir()
                        ? preview.translatedBaseName()
                        : preview.translatedBaseName() + suffixWithDot(file.getFilename());
                file.setPreviewName(previewName);
                count++;
            }
        }
        return count;
    }

    private static String suffixWithDot(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(index) : "";
    }

    private static boolean isProhibitedContent(FileNameTranslationPreviewItem item) {
        return item != null && item.warnings().stream()
                .anyMatch(warning -> warning.contains("内容安全策略") || warning.contains("prohibited_content"));
    }

    private static boolean isConfigured(AiTranslationSettings settings) {
        return settings != null
                && settings.getEndpoint() != null && !settings.getEndpoint().isBlank()
                && settings.getModel() != null && !settings.getModel().isBlank()
                && settings.getApiKey() != null && !settings.getApiKey().isBlank();
    }

    private static void notify(MessageType type, String message) {
        EventBus.publish(NotificationKey.MESSAGE, new MessageInfo(type, message));
    }
}
