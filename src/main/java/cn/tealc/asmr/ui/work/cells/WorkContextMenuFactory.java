package cn.tealc.asmr.ui.work.cells;

import cn.tealc.asmr.eventbus.EventBus;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.factory.ViewFactory;
import cn.tealc.asmr.model.Actor;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.album.WorkAlbumSummary;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.DictionaryService;
import cn.tealc.asmr.service.WorkAlbumService;
import cn.tealc.asmr.service.WorkService;
import cn.tealc.asmr.ui.component.OverlayLayer;
import cn.tealc.asmr.ui.dialog.Dialogs;
import cn.tealc.asmr.ui.album.WorkAlbumDialogs;
import cn.tealc.asmr.ui.work.WorkDetailView;
import cn.tealc.asmr.ui.work.WorkDetailViewModel;
import cn.tealc.asmr.mvvm.ViewTuple;
import javafx.application.Platform;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.Pane;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 作品右键菜单工厂。
 * <p>
 * 菜单项回调通过 {@link WorkCellActions} 接口委托给具体 Cell 实现。
 *
 * @author Leck
 */
public final class WorkContextMenuFactory {

    /** 二级菜单默认展示的最近条目数。 */
    private static final int RECENT_LIMIT = 5;

    private WorkContextMenuFactory() {
    }

    /**
     * 创建完整版右键菜单（BigWorkCell / SmallWorkCell 用）。
     * <p>
     * 菜单项：同步、收藏/取消收藏、作品集、修改、标签、复制作品编号、跳转、字幕重命名、移动；
     * 本地作品额外提供打开文件夹；删除操作固定显示在末尾。
     *
     * @param work    作品
     * @param actions 回调实现
     * @return 右键菜单
     */
    public static ContextMenu create(AsmrWork work, WorkCellActions actions) {
        return create(work, actions, "移除");
    }

    public static ContextMenu create(AsmrWork work, WorkCellActions actions, String removeText) {
        ContextMenu menu = new ContextMenu();

        MenuItem updateMI = new MenuItem("同步", new FontIcon(Material2OutlinedAL.CACHED));
        MenuItem alertMI = new MenuItem("修改", new FontIcon(Material2OutlinedAL.EDIT));
        Menu albumMenu = createAlbumMenu(work);
        Menu tagMenu = createTagMenu(work);
        MenuItem deleteMI = new MenuItem(removeText, new FontIcon(Material2OutlinedAL.DELETE_OUTLINE));
        deleteMI.getStyleClass().add("work-delete-menu-item");
        MenuItem copyWorkIdMI = new MenuItem("复制作品编号", new FontIcon(Material2OutlinedAL.CONTENT_COPY));
        MenuItem toWebMI = new MenuItem("跳转", new FontIcon(Material2OutlinedMZ.WEB_ASSET));
        MenuItem starMI = new MenuItem(
                work.getFavorites() ? "取消收藏" : "收藏",
                new FontIcon(Material2OutlinedMZ.STAR_OUTLINE));

        updateMI.setOnAction(e -> actions.onSync(work));
        alertMI.setOnAction(e -> actions.onEditInfo(work));
        deleteMI.setOnAction(e -> actions.onDelete(work));
        copyWorkIdMI.setOnAction(e -> actions.onCopyWorkId(work));
        toWebMI.setOnAction(e -> actions.onOpenWeb(work));
        starMI.setOnAction(e -> {
            actions.onToggleFavorite(work);
            starMI.setText(work.getFavorites() ? "取消收藏" : "收藏");
        });

        // 子菜单数据在右键菜单展开时统一预加载，避免悬停展开时内容为空
        menu.setOnShowing(event -> {
            loadAlbumItems(albumMenu, work);
            loadTagItems(tagMenu, work);
        });

        menu.getItems().addAll(updateMI, starMI, albumMenu, alertMI, tagMenu, copyWorkIdMI, toWebMI);

        MenuItem renameLrcMI = new MenuItem("字幕重命名", new FontIcon(Material2OutlinedMZ.PALETTE));
        renameLrcMI.setOnAction(e -> actions.onSubtitleRename(work));
        menu.getItems().add(renameLrcMI);
        if (work.getStorageType() == 0) {
            MenuItem openMI = new MenuItem("打开文件夹", new FontIcon(Material2OutlinedAL.FOLDER_OPEN));
            openMI.setOnAction(e -> actions.onOpenFolder(work));
            menu.getItems().add(openMI);
        }

        MenuItem moveMI = new MenuItem("移动", new FontIcon(Material2OutlinedMZ.MOVE_TO_INBOX));
        moveMI.setOnAction(e -> actions.onMove(work));
        menu.getItems().add(moveMI);
        menu.getItems().addAll(new SeparatorMenuItem(), deleteMI);

        return menu;
    }

    /**
     * 作品集二级菜单。
     * <p>
     * 创建时先放入占位项：JavaFX 只有在 Menu 拥有子项时才会把它渲染成可展开的子菜单，
     * 空 Menu 会被当成普通菜单项，既不能悬停展开也无法点击。
     * 真正的数据在右键菜单展开时由 {@link #loadAlbumItems} 填充。
     */
    private static Menu createAlbumMenu(AsmrWork work) {
        Menu menu = new Menu("加入作品集", null, placeholder("加载中…"));
        menu.setGraphic(new FontIcon(Material2OutlinedAL.COLLECTIONS_BOOKMARK));
        return menu;
    }

    private static void loadAlbumItems(Menu menu, AsmrWork work) {
        menu.getItems().setAll(placeholder("加载中…"));
        Thread.startVirtualThread(() -> {
            try {
                WorkAlbumService service = DI.get(WorkAlbumService.class);
                List<WorkAlbumSummary> recent = service.recentSummaries(RECENT_LIMIT);
                Set<Integer> joined = work.getId() > 0 ? service.albumsForWork(work.getId()) : Set.of();
                Platform.runLater(() -> fillAlbumItems(menu, work, recent, joined));
            } catch (RuntimeException e) {
                Platform.runLater(() -> menu.getItems().setAll(placeholder("加载失败")));
            }
        });
    }

    private static void fillAlbumItems(Menu menu, AsmrWork work, List<WorkAlbumSummary> recent, Set<Integer> joined) {
        List<MenuItem> items = new ArrayList<>();
        for (WorkAlbumSummary summary : recent) {
            int albumId = summary.album().id();
            boolean already = joined.contains(albumId);
            CheckMenuItem item = new CheckMenuItem(summary.album().name());
            item.setSelected(already);
            // 已在集合中的作品不再重复加入
            item.setDisable(already);
            item.setOnAction(event -> addToAlbum(work, albumId, item));
            items.add(item);
        }
        if (items.isEmpty()) {
            items.add(placeholder("暂无作品集"));
        }
        items.add(new SeparatorMenuItem());
        MenuItem more = new MenuItem("更多…");
        more.setOnAction(event -> WorkAlbumDialogs.chooseAlbums(work));
        items.add(more);
        menu.getItems().setAll(items);
    }

    private static void addToAlbum(AsmrWork work, int albumId, MenuItem source) {
        source.setDisable(true);
        Thread.startVirtualThread(() -> {
            try {
                DI.get(WorkAlbumService.class).addWorks(List.of(albumId), List.of(work.getId()));
                Platform.runLater(() -> EventBus.publish(
                        NotificationKey.MESSAGE, new MessageInfo(MessageType.SUCCESS, "已加入作品集")));
            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    source.setDisable(false);
                    EventBus.publish(NotificationKey.MESSAGE,
                            new MessageInfo(MessageType.WARNING, "加入作品集失败"));
                });
            }
        });
    }

    /**
     * 标签二级菜单。
     * <p>
     * 同样在创建时先放入占位项，使 Menu 具备可展开形态；
     * 数据在右键菜单展开时由 {@link #loadTagItems} 填充。
     */
    private static Menu createTagMenu(AsmrWork work) {
        Menu menu = new Menu("标签", null, placeholder("加载中…"));
        menu.setGraphic(new FontIcon(Material2OutlinedAL.BOOKMARK_BORDER));
        return menu;
    }

    private static void loadTagItems(Menu menu, AsmrWork work) {
        menu.getItems().setAll(placeholder("加载中…"));
        Thread.startVirtualThread(() -> {
            try {
                List<Actor> recent = DI.get(DictionaryService.class).getRecentTags(RECENT_LIMIT);
                Platform.runLater(() -> fillTagItems(menu, work, recent));
            } catch (RuntimeException e) {
                Platform.runLater(() -> menu.getItems().setAll(placeholder("加载失败")));
            }
        });
    }

    private static void fillTagItems(Menu menu, AsmrWork work, List<Actor> recent) {
        Set<String> current = splitTags(work.getTags());
        List<MenuItem> items = new ArrayList<>();
        for (Actor tag : recent) {
            String name = tag.getName();
            CheckMenuItem item = new CheckMenuItem(name);
            item.setSelected(current.contains(name));
            item.setOnAction(event -> toggleTag(work, name, item));
            items.add(item);
        }
        if (items.isEmpty()) {
            items.add(placeholder("暂无标签"));
        }
        items.add(new SeparatorMenuItem());
        MenuItem more = new MenuItem("更多…");
        more.setOnAction(event -> {
            var view = ViewFactory.updateTagView(work);
            Dialogs.show(view.getCodeBehind());
        });
        items.add(more);
        menu.getItems().setAll(items);
    }

    /**
     * 切换作品上的某个标签：已存在则移除，不存在则追加。
     */
    private static void toggleTag(AsmrWork work, String tagName, CheckMenuItem source) {
        boolean adding = source.isSelected();
        Set<String> tags = splitTags(work.getTags());
        if (adding) {
            tags.add(tagName);
        } else {
            tags.remove(tagName);
        }
        String joined = String.join(",", tags);
        Thread.startVirtualThread(() -> {
            try {
                // 先确保标签存在于字典表；已存在时 addTag 返回 false，属正常情况
                if (adding) {
                    DI.get(DictionaryService.class).addTag(tagName);
                }
                // updateWorkTag 返回受影响行数，值未变化时为 0，不能据此判定失败
                DI.get(WorkService.class).updateWorkTag(work.getId(), joined.isEmpty() ? null : joined);
                Platform.runLater(() -> {
                    work.setTags(joined.isEmpty() ? null : joined);
                    // 通知卡片与详情页刷新
                    EventBus.publish(NotificationKey.MAIN_GRID_WORK_UPDATE, work);
                });
            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    // 失败时回滚勾选状态，保持与数据一致
                    source.setSelected(!adding);
                    EventBus.publish(NotificationKey.MESSAGE,
                            new MessageInfo(MessageType.WARNING, "更新标签失败"));
                });
            }
        });
    }

    /**
     * 拆分作品的标签字符串，保持原有顺序并去重。
     */
    private static Set<String> splitTags(String csv) {
        if (csv == null || csv.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(csv.split(","))
                .map(String::strip)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static MenuItem placeholder(String text) {
        MenuItem item = new MenuItem(text);
        item.setDisable(true);
        return item;
    }

    /**
     * 创建精简版右键菜单（DuplicateWorkCell 用）。
     * <p>
     * 菜单项：查看、复制作品编号；本地作品追加：打开文件夹；删除操作固定显示在末尾。
     *
     * @param work    作品
     * @param actions 回调实现
     * @return 右键菜单
     */
    public static ContextMenu createLite(AsmrWork work, WorkCellActions actions) {
        ContextMenu menu = new ContextMenu();

        MenuItem deleteMI = new MenuItem("移除", new FontIcon(Material2OutlinedAL.DELETE_OUTLINE));
        deleteMI.getStyleClass().add("work-delete-menu-item");
        MenuItem copyWorkIdMI = new MenuItem("复制作品编号", new FontIcon(Material2OutlinedAL.CONTENT_COPY));
        MenuItem viewMI = new MenuItem("查看", new FontIcon(Material2OutlinedMZ.VISIBILITY));

        deleteMI.setOnAction(e -> actions.onDelete(work));
        copyWorkIdMI.setOnAction(e -> actions.onCopyWorkId(work));
        viewMI.setOnAction(e -> {
            ViewTuple<WorkDetailView, WorkDetailViewModel> viewTuple = ViewFactory.workDetailView(work);
            OverlayLayer.show((Pane) viewTuple.getView());
        });

        menu.getItems().addAll(viewMI, copyWorkIdMI);

        if (work.getStorageType() == 0) {
            MenuItem openMI = new MenuItem("打开文件夹", new FontIcon(Material2OutlinedAL.FOLDER_OPEN));
            openMI.setOnAction(e -> actions.onOpenFolder(work));
            menu.getItems().add(openMI);
        }
        menu.getItems().addAll(new SeparatorMenuItem(), deleteMI);

        return menu;
    }
}
