package cn.tealc.asmr.ui.collection;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.eventbus.EventBus;
import cn.tealc.asmr.model.AsmrFile;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.collection.ItemData;
import cn.tealc.asmr.model.collection.SheetData;
import cn.tealc.asmr.model.message.MessageInfo;
import cn.tealc.asmr.model.message.MessageType;
import cn.tealc.asmr.config.NotificationKey;
import cn.tealc.asmr.service.CollectionService;
import cn.tealc.asmr.mvvm.ViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Pair;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * “添加到歌单”弹窗的视图模型：维护 {@code (歌单, 当前文件是否已收录)} 的勾选列表。
 * <p>
 * “是否已收录”按 {@code (作品 ID, 文件相对路径)} 查询已有收藏项得出；相对路径相对作品所在目录计算，
 * 因此同一作品内的同名文件在不同目录下互不影响。
 * <p>
 * 列表刷新都由界面事件同步触发，本类不启动后台线程；构造时会立即执行一次 {@link #refresh()}。
 *
 * @author Leck
 */
public class AddToSheetViewModel implements ViewModel {
    private final CollectionService collectionService = DI.get(CollectionService.class);
    private final ObservableList<Pair<SheetData,Boolean>> sheetList = FXCollections.observableArrayList();
    private final AsmrWork work;
    private final AsmrFile file;

    public AddToSheetViewModel(AsmrWork work, AsmrFile file) {
        this.work = work;
        this.file = file;


        refresh();
    }


    /**
     * 重载歌单勾选列表：全部歌单按“是否已收录当前文件”标记为选中或未选中。
     */
    private void refresh() {
        List<SheetData> allSheets = collectionService.getAllSheets();

        List<ItemData> items = collectionService.getItemsByWorkAndFilepath(work.getId(), getRelativePath());

        Set<Integer> existingSheetIds = items.stream()
                .map(ItemData::getSheetId)
                .collect(Collectors.toSet());
        List<Pair<SheetData, Boolean>> list = allSheets.stream()
                .map(sheet -> new Pair<>(sheet, existingSheetIds.contains(sheet.getId())))
                .toList();
        sheetList.setAll(list);
    }


    /**
     * 新建歌单并刷新勾选列表；数据库写入失败时发送错误消息，列表保持原样。
     *
     * @param sheetName 歌单名称，调用方已保证非空白
     */
    public void createSheet(String sheetName) {
        boolean status = collectionService.createSheet(sheetName);
        if (status) {
            refresh();
        }else {
            EventBus.message(MessageInfo.error("创建歌单失败，数据库操作错误"));
        }
    }


    /**
     * 保存当前文件与所选歌单的关联。
     * <p>
     * 先把每个歌单展开成一条收藏项（名称取文件名、路径取相对路径、记录作品 ID），
     * 再交给 {@link CollectionService#syncItems} 做差量同步：本次未勾选的旧关联会被删除。
     *
     * @param sheets 用户勾选的歌单列表；为空表示把该文件从所有歌单中移出
     * @return 恒为 {@code true}，同步结果不由本方法返回
     */
    public boolean save(List<SheetData> sheets){
        // 生成待保存的 ItemData 列表
        List<ItemData> newItems = sheets.stream()
                .map(sheet -> {
                    ItemData item = new ItemData();
                    item.setName(file.getFilename());
                    item.setFilepath(getRelativePath());
                    item.setWorkId(work.getId());
                    item.setSheetId(sheet.getId());
                    return item;
                }).toList();

        // 差量同步：删除多余、插入新增
        collectionService.syncItems(newItems, work.getId(), getRelativePath());
        return true;
    }


    /**
     * 计算文件相对作品目录的路径，作为收藏项的 {@code filepath} 存储键，分隔符随系统平台。
     *
     * @return 相对路径字符串；文件与作品目录不在同一根路径下时抛出 {@link IllegalArgumentException}
     */
    private String getRelativePath() {
        Path basePath = Paths.get(work.getFilepath());
        Path targetPath = Paths.get(file.getFilepath());
        Path relativePath = basePath.relativize(targetPath);
        return relativePath.toString();
    }

    public ObservableList<Pair<SheetData, Boolean>> getSheetList() {
        return sheetList;
    }
}