package cn.tealc.asmr.ui.work.list;

import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.FileOperationService;
import cn.tealc.asmr.ui.navigation.NavigationService;
import cn.tealc.asmr.ui.navigation.WorkSource;
import cn.tealc.asmr.ui.navigation.WorksRoute;
import cn.tealc.asmr.ui.dialog.Alerts;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * 默认卡片上下文：跳转到作品列表，删除时弹出确认框。
 *
 * @author Leck
 */
public final class DefaultWorkCellContext implements WorkCellContext {

    public static final DefaultWorkCellContext INSTANCE = new DefaultWorkCellContext();

    private DefaultWorkCellContext() {
    }

    @Override
    public void navigate(AsmrKeyType type, String key) {
        DI.get(NavigationService.class).push(new WorksRoute(WorkSource.of(type, key)));
    }

    @Override
    public void favoriteChanged(AsmrWork work) {
        EventBus.publish(NotificationKey.MAIN_GRID_WORK_UPDATE, work);
    }

    @Override
    public void delete(AsmrWork work) {
        FileOperationService fileOperationService = DI.get(FileOperationService.class);
        CheckBox deleteStorage = new CheckBox("同时彻底删除存储中的作品");
        deleteStorage.getStyleClass().add("delete-work-storage-checkbox");
        Label databaseHint = new Label("默认仅移除数据库记录，作品文件将保留。");
        databaseHint.getStyleClass().add("delete-work-default-hint");
        Label storageHint = new Label("勾选后将永久删除当前来源中的作品，且无法撤销。");
        storageHint.getStyleClass().add("delete-work-storage-hint");
        VBox storageOption = new VBox(4, deleteStorage, storageHint);
        storageOption.getStyleClass().add("delete-work-storage-option");
        VBox options = new VBox(10, databaseHint, storageOption);
        options.getStyleClass().add("delete-work-options");
        Alerts.danger()
                .title("移除作品")
                .message("将从数据库中移除该作品记录。")
                .content(options)
                .ok("移除", handle -> {
                    boolean deleteFromStorage = deleteStorage.isSelected();
                    if (!deleteFromStorage) {
                        // 仅删除数据库记录，保持原有同步行为
                        FileOperationService.OperationResult result =
                                fileOperationService.deleteWork(work, false);
                        if (!result.success()) {
                            EventBus.publish(NotificationKey.MESSAGE,
                                    new MessageInfo(MessageType.WARNING, result.message()));
                            return;
                        }
                        EventBus.publish(NotificationKey.MAIN_GRID_WORK_REMOVE, work, false);
                        return;
                    }
                    Thread.startVirtualThread(() -> {
                        FileOperationService.OperationResult result =
                                fileOperationService.deleteWork(work, true);
                        Platform.runLater(() -> {
                            if (!result.success()) {
                                EventBus.publish(NotificationKey.MESSAGE,
                                        new MessageInfo(MessageType.WARNING, result.message()));
                                return;
                            }
                            EventBus.publish(NotificationKey.MAIN_GRID_WORK_REMOVE, work, false);
                        });
                    });
                })
                .cancel()
                .show();
    }
}
