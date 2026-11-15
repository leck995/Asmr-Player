package cn.tealc.asmr.service;

import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.config.Config;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.task.background.TaskManager;
import cn.tealc.asmr.task.dlsite.DLSiteMetadataMultipleService;
import cn.tealc.asmr.task.dlsite.DLSiteMetadataSingleService;
import javafx.application.Platform;
import javafx.concurrent.WorkerStateEvent;

import java.util.List;

/**
 * 元数据同步服务。
 * <p>
 * 封装 DLsite 元数据同步任务的创建、启动与结果回调，
 * 使 View/ViewModel 不再直接实例化 {@link DLSiteMetadataSingleService} /
 * {@link DLSiteMetadataMultipleService}。单个同步成功后自动发布
 * {@link NotificationKey#MAIN_GRID_WORK_UPDATE} 通知。
 * <p>
 * 批量同步通过 {@link TaskManager} 统一提交，由 TaskManager 管理状态与完成通知。
 * 单作品同步为短时操作，不走 TaskManager，保持原有的守护线程方式。
 *
 * @author Leck
 */
public class MetadataSyncService {

    private final TaskManager taskManager = DI.get(TaskManager.class);

    /**
     * 同步单个作品的 DLsite 元数据。
     * <p>
     * 在后台守护线程中执行，成功时发布 SUCCESS 消息与 WORK_UPDATE 通知，
     * 失败时发布 ERROR 消息。不走 TaskManager，因为是单作品短时操作。
     *
     * @param work 要同步的作品
     */
    public void syncSingle(AsmrWork work) {
        if (!isSyncUrlConfigured()) {
            publishInvalidSyncUrlMessage();
            return;
        }
        DLSiteMetadataSingleService task = new DLSiteMetadataSingleService(work);
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        task.valueProperty().addListener((observableValue, s, t1) -> {
            if (t1 != null) {
                Platform.runLater(() -> {
                    EventBus.publish(NotificationKey.MESSAGE,
                            new MessageInfo(MessageType.SUCCESS, String.format("成功同步:%s", work.getTitle())));
                    EventBus.publish(NotificationKey.MAIN_GRID_WORK_UPDATE, t1);
                });
            }
        });
        task.messageProperty().addListener((observableValue, s, t1) ->
                EventBus.publish(NotificationKey.MESSAGE,
                        new MessageInfo(MessageType.ERROR, "同步失败:" + t1)));
        thread.start();
    }

    /**
     * 批量同步作品元数据。
     * <p>
     * 通过 TaskManager 提交 {@link DLSiteMetadataMultipleService}，
     * 每个作品同步完成后发布 WORK_UPDATE 通知。
     * 任务进度和状态由 TaskManager 统一管理，在 HeaderBar 任务列表中展示。
     *
     * @param works        要同步的作品列表
     * @param libraryPath  媒体库路径，用于去重
     */
    public void syncBatch(List<AsmrWork> works, String libraryPath) {
        syncBatch(works, libraryPath, null);
    }

    /**
     * 批量同步作品元数据，并在任务成功完成后执行回调。
     *
     * @param works 要同步的作品
     * @param libraryPath 媒体库路径，用于去重
     * @param onSucceeded 任务成功完成后的回调，可为空
     */
    public void syncBatch(List<AsmrWork> works, String libraryPath, Runnable onSucceeded) {
        if (!isSyncUrlConfigured()) {
            publishInvalidSyncUrlMessage();
            return;
        }
        if (works == null || works.isEmpty()) {
            EventBus.publish(NotificationKey.MESSAGE,
                    new MessageInfo(MessageType.INFO, "该媒体库没有可同步的作品"));
            return;
        }
        DLSiteMetadataMultipleService task = new DLSiteMetadataMultipleService(works, libraryPath);
        task.valueProperty().addListener((observableValue, asmrWork, t1) -> {
            if (t1 != null) {
                Platform.runLater(() ->
                    EventBus.publish(NotificationKey.MAIN_GRID_WORK_UPDATE, t1));
            }
        });
        if (onSucceeded != null) {
            task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, event -> onSucceeded.run());
        }
        taskManager.submit(task);
    }

    static boolean isSyncUrlConfigured() {
        String url = Config.setting.sourcesUrl.get();
        return url != null && url.startsWith("https://");
    }

    private void publishInvalidSyncUrlMessage() {
        EventBus.publish(NotificationKey.MESSAGE,
                new MessageInfo(MessageType.WARNING, "请先正确设置同步网址"));
    }
}
