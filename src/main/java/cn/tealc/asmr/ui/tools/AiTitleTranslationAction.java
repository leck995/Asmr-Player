package cn.tealc.asmr.ui.tools;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.model.translation.TranslationPreviewItem;
import cn.tealc.asmr.config.AiTranslationSettings;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.ai.AiTranslationService;
import javafx.animation.Animation;
import javafx.animation.RotateTransition;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.List;
import java.util.function.Consumer;

/**
 * 作品标题 AI 翻译预览与应用动作。
 */
public final class AiTitleTranslationAction {
    private final AiTranslationService translationService = DI.get(AiTranslationService.class);

    private AiTitleTranslationAction() {
    }

    public static void preview(AsmrWork work, Button button, Consumer<TranslationPreviewItem> onPreviewReady) {
        new AiTitleTranslationAction().runPreview(work, button, onPreviewReady);
    }

    public static FontIcon createIdleIcon() {
        return new FontIcon(Material2OutlinedAL.G_TRANSLATE);
    }

    public static FontIcon createReadyIcon() {
        return new FontIcon(Material2OutlinedAL.CHECK);
    }

    /**
     * 将已通过校验的候选译名写入 titleCN。
     */
    public static boolean apply(TranslationPreviewItem item, AsmrWork work) {
        if (item == null || !item.valid()) {
            notify(MessageType.WARNING, "没有可应用的作品标题译名");
            return false;
        }
        try {
            boolean saved = new AiTitleTranslationAction().translationService.applyTitle(item, item.translatedText());
            if (!saved) {
                notify(MessageType.WARNING, "中文标题未保存");
                return false;
            }
            EventBus.publish(NotificationKey.MAIN_GRID_WORK_UPDATE, work);
            return true;
        } catch (Exception e) {
            notify(MessageType.ERROR, "中文标题保存失败：" + e.getMessage());
            return false;
        }
    }

    private void runPreview(AsmrWork work, Button button, Consumer<TranslationPreviewItem> onPreviewReady) {
        AiTranslationSettings settings = Config.setting.aiTranslation;
        if (!isConfigured(settings)) {
            notify(MessageType.WARNING, "请先在 设置 → AI 翻译 中配置接口、模型和 API Key");
            return;
        }

        FontIcon loadingIcon = new FontIcon(Material2OutlinedMZ.REFRESH);
        RotateTransition rotation = new RotateTransition(Duration.millis(850), loadingIcon);
        rotation.setByAngle(360);
        rotation.setCycleCount(Animation.INDEFINITE);
        button.setGraphic(loadingIcon);
        button.setDisable(true);
        rotation.play();

        Task<List<TranslationPreviewItem>> task = new Task<>() {
            @Override
            protected List<TranslationPreviewItem> call() {
                return translationService.previewWorks(List.of(work), settings, settings.getApiKey());
            }
        };
        task.setOnSucceeded(event -> {
            rotation.stop();
            button.setGraphic(createIdleIcon());
            button.setDisable(false);
            if (task.getValue().isEmpty()) {
                notify(MessageType.WARNING, "AI 未返回作品标题译名");
                return;
            }
            TranslationPreviewItem item = task.getValue().getFirst();
            if (!item.valid()) {
                notify(MessageType.WARNING, "AI 返回结果未通过校验：" + String.join("；", item.warnings()));
                return;
            }
            onPreviewReady.accept(item);
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
