package cn.tealc.asmr.service;

import cn.tealc.asmr.dao.collection.CollectionItemDao;
import cn.tealc.asmr.dao.collection.CollectionSheetDao;
import cn.tealc.asmr.di.Inject;
import cn.tealc.asmr.model.collection.ItemData;
import cn.tealc.asmr.model.collection.ItemDataEx;
import cn.tealc.asmr.model.collection.CollectionSheetSummary;
import cn.tealc.asmr.model.collection.SheetData;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 收藏夹服务。
 * <p>
 * 封装歌单与收藏项的 CRUD，以及收藏项的差量同步逻辑，
 * 使收藏夹模块的 ViewModel 不再直接实例化
 * {@link CollectionSheetDao} / {@link CollectionItemDao}。
 *
 * @author Leck
 */
public class CollectionService {

    @Inject
    private CollectionSheetDao sheetDao;

    @Inject
    private CollectionItemDao itemDao;

    /*===============歌单管理======================*/

    /**
     * 获取所有歌单。
     */
    public List<SheetData> getAllSheets() {
        return sheetDao.getAllSheets();
    }

    /**
     * 获取歌单卡片所需的摘要。
     */
    public List<CollectionSheetSummary> getSheetSummaries() {
        return sheetDao.getSheetSummaries();
    }

    /**
     * 按 ID 获取歌单。
     */
    public SheetData getSheetById(int sheetId) {
        return sheetDao.getSheetById(sheetId);
    }

    /**
     * 创建歌单。
     */
    public boolean createSheet(String sheetName) {
        return sheetDao.createSheet(sheetName);
    }

    /**
     * 创建带描述的歌单。
     */
    public boolean createSheet(String sheetName, String description) {
        return sheetDao.createSheet(sheetName, description);
    }

    /**
     * 更新歌单名称与描述。
     */
    public boolean updateSheetDetails(int sheetId, String newName, String newDescription) {
        return sheetDao.updateSheetDetails(sheetId, newName, newDescription);
    }

    /**
     * 删除歌单（先清空其下收藏项，再删歌单本身）。
     */
    public boolean deleteSheet(int sheetId) {
        itemDao.deleteItemBySheetId(sheetId);
        return sheetDao.deleteSheet(sheetId);
    }

    /*===============收藏项管理======================*/

    /**
     * 按歌单加载收藏项（带作品信息）。
     */
    public List<ItemDataEx> getItemsWithWorkInfoBySheet(int sheetId) {
        return itemDao.getItemsWithWorkInfoBySheet(sheetId);
    }

    /**
     * 删除单个收藏项。
     */
    public boolean deleteItem(int itemId) {
        return itemDao.deleteItem(itemId);
    }

    /**
     * 按作品 ID 和文件相对路径查已加入的歌单项。
     */
    public List<ItemData> getItemsByWorkAndFilepath(int workId, String filepath) {
        return itemDao.getItemsByWorkAndFilepath(workId, filepath);
    }

    /**
     * 差量同步收藏项：对比目标列表与数据库现有项，删除多余、插入新增。
     * <p>
     * 原逻辑散落在 AddToSheetViewModel.save() 中，现收口到 Service。
     *
     * @param newItems 目标收藏项列表（用户勾选后的完整集合）
     * @param workId   作品 ID
     * @param filepath 文件相对路径
     */
    public void syncItems(List<ItemData> newItems, int workId, String filepath) {
        List<ItemData> existingItems = itemDao.getItemsByWorkAndFilepath(workId, filepath);

        Set<Integer> newSheetIds = newItems.stream()
                .map(ItemData::getSheetId)
                .collect(Collectors.toSet());
        Set<Integer> existingSheetIds = existingItems.stream()
                .map(ItemData::getSheetId)
                .collect(Collectors.toSet());

        // 删除数据库中多余的数据
        existingItems.stream()
                .filter(item -> !newSheetIds.contains(item.getSheetId()))
                .forEach(item -> itemDao.deleteItem(item.getId()));

        // 插入新增的数据
        newItems.stream()
                .filter(item -> !existingSheetIds.contains(item.getSheetId()))
                .forEach(itemDao::addItem);
    }
}
