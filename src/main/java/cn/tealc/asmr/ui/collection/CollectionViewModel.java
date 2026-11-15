package cn.tealc.asmr.ui.collection;

import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.collection.CollectionSheetSummary;
import cn.tealc.asmr.model.collection.SheetData;
import cn.tealc.asmr.service.CollectionService;
import cn.tealc.asmr.mvvm.ViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * 歌单主页的视图模型，持有歌单摘要列表并封装增删改后的刷新。
 * <p>
 * 每个写操作都返回数据库执行结果，仅在成功时刷新列表，失败时保留原数据供界面提示重试。
 *
 * @author Leck
 */
public class CollectionViewModel implements ViewModel {
    private final CollectionService collectionService = DI.get(CollectionService.class);
    private final ObservableList<CollectionSheetSummary> sheetSummaries = FXCollections.observableArrayList();

    public CollectionViewModel() {
        refreshSheetSummaries();
    }

    /**
     * 重新加载全部歌单摘要（歌单信息 + 收录数量）。
     */
    public void refreshSheetSummaries() {
        sheetSummaries.setAll(collectionService.getSheetSummaries());
    }

    /**
     * 创建歌单。
     *
     * @param name        歌单名称
     * @param description 描述，可为 null 或空
     * @return 数据库写入是否成功；成功时已刷新摘要列表
     */
    public boolean createSheet(String name, String description) {
        boolean status = collectionService.createSheet(name, description);
        if (status) {
            refreshSheetSummaries();
        }
        return status;
    }

    /**
     * 修改歌单的名称与描述。
     *
     * @return 更新是否成功；成功时已刷新摘要列表
     */
    public boolean updateSheet(int sheetId, String newName, String newDescription) {
        boolean status = collectionService.updateSheetDetails(sheetId, newName, newDescription);
        if (status) {
            refreshSheetSummaries();
        }
        return status;
    }

    /**
     * 删除歌单，歌单下的收藏项一并移除，作品记录不受影响。
     *
     * @return 删除是否成功；成功时已刷新摘要列表
     */
    public boolean deleteSheet(int sheetId) {
        boolean status = collectionService.deleteSheet(sheetId);
        if (status) {
            refreshSheetSummaries();
        }
        return status;
    }

    public ObservableList<CollectionSheetSummary> getSheetSummaries() {
        return sheetSummaries;
    }
}
