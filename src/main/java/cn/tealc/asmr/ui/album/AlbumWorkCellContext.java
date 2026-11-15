package cn.tealc.asmr.ui.album;

import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.WorkAlbumService;
import cn.tealc.asmr.ui.work.list.WorkCellContext;
import cn.tealc.asmr.ui.navigation.NavigationService;
import cn.tealc.asmr.ui.navigation.WorkSource;
import cn.tealc.asmr.ui.navigation.WorksRoute;

import java.util.function.IntSupplier;

/**
 * 作品集成员卡片的操作上下文。
 * <p>
 * 删除语义是“移出本集”，不会删除作品本身；标签跳转交给页面路由。
 *
 * @author Leck
 */
final class AlbumWorkCellContext implements WorkCellContext {

    private final IntSupplier albumId;
    private final Runnable refresh;

    AlbumWorkCellContext(IntSupplier albumId, Runnable refresh) {
        this.albumId = albumId;
        this.refresh = refresh;
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
        int id = albumId.getAsInt();
        if (id <= 0) {
            return;
        }
        WorkAlbumDialogs.confirmRemove(work, () ->
                AlbumTasks.run(() -> DI.get(WorkAlbumService.class).removeWork(id, work.getId()), removed -> {
                    if (!removed) {
                        refresh.run();
                    }
                    EventBus.message(new MessageInfo(MessageType.SUCCESS, "已移出本集"));
                }, WorkAlbumDialogs::error));
    }
}
