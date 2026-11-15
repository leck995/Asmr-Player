package cn.tealc.asmr.dao.collection;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.collection.ItemData;
import cn.tealc.asmr.model.collection.ItemDataEx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CollectionItemDao} 全量测试。
 * <p>
 * 依赖 asmr_collect_sheet 和 asmr_works 表，基类已清空。
 * 测试前需先创建 sheet 和 work 以满足外键约束。
 *
 * @author Leck
 */
@DisplayName("CollectionItemDao 测试")
class CollectionItemDaoTest extends BaseDatabaseTest {

    private CollectionItemDao itemDao;
    private CollectionSheetDao sheetDao;
    private AsmrWorkDao workDao;

    @BeforeEach
    void setUp() {
        itemDao = new CollectionItemDao();
        sheetDao = new CollectionSheetDao();
        workDao = new AsmrWorkDao();
    }

    /**
     * 创建歌单并返回 id。
     */
    private int createSheet(String name) {
        sheetDao.createSheet(name);
        return sheetDao.getAllSheets().get(0).getId();
    }

    /**
     * 创建作品并返回 id（addWork 不回填 id，需重查）。
     */
    private int createWork(String workId, String title, String filepath) {
        AsmrWork work = new AsmrWork();
        work.setStorageSourceId(1);
        work.setWorkId(workId);
        work.setTitle(title);
        work.setFilepath(filepath);
        work.setParentPath("/audio");
        work.setStorageType(0);
        workDao.addWork(work);
        return workDao.getWorkByFilepath(filepath).getId();
    }

    /**
     * 构造测试 ItemData。
     */
    private ItemData createItem(String name, String filepath, int workId, int sheetId) {
        ItemData item = new ItemData();
        item.setName(name);
        item.setFilepath(filepath);
        item.setWorkId(workId);
        item.setSheetId(sheetId);
        return item;
    }

    @Test
    @DisplayName("addItem 正常添加项目")
    void addItem_normalAdd() {
        int sheetId = createSheet("歌单A");
        int workId = createWork("RJ1", "作品A", "/audio/RJ1");
        assertTrue(itemDao.addItem(createItem("曲目1", "/audio/RJ1/track1.mp3", workId, sheetId)));
    }

    @Test
    @DisplayName("getItemById 存在返回项目")
    void getItemById_exists() {
        int sheetId = createSheet("歌单A");
        int workId = createWork("RJ1", "作品A", "/audio/RJ1");
        itemDao.addItem(createItem("曲目1", "/audio/RJ1/track1.mp3", workId, sheetId));
        int itemId = itemDao.getItemsBySheet(sheetId).get(0).getId();
        ItemData result = itemDao.getItemById(itemId);
        assertNotNull(result);
        assertEquals("曲目1", result.getName());
    }

    @Test
    @DisplayName("getItemById 不存在返回 null")
    void getItemById_notExists() {
        assertNull(itemDao.getItemById(99999));
    }

    @Test
    @DisplayName("getItemsBySheet 按歌单查全部项目")
    void getItemsBySheet() {
        int sheetId = createSheet("歌单A");
        int workId = createWork("RJ1", "作品A", "/audio/RJ1");
        itemDao.addItem(createItem("曲目1", "/a/1.mp3", workId, sheetId));
        itemDao.addItem(createItem("曲目2", "/a/2.mp3", workId, sheetId));
        List<ItemData> result = itemDao.getItemsBySheet(sheetId);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("getItemsByFilePath 按文件路径查")
    void getItemsByFilePath() {
        int sheetId = createSheet("歌单A");
        int workId = createWork("RJ1", "作品A", "/audio/RJ1");
        itemDao.addItem(createItem("曲目1", "/audio/RJ1/track1.mp3", workId, sheetId));
        List<ItemData> result = itemDao.getItemsByFilePath("/audio/RJ1/track1.mp3");
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("isItemExist 存在返回 true")
    void isItemExist_exists() {
        int sheetId = createSheet("歌单A");
        int workId = createWork("RJ1", "作品A", "/audio/RJ1");
        itemDao.addItem(createItem("曲目1", "/audio/RJ1/track1.mp3", workId, sheetId));
        assertTrue(itemDao.isItemExist("/audio/RJ1/track1.mp3"));
    }

    @Test
    @DisplayName("isItemExist 不存在返回 false")
    void isItemExist_notExists() {
        assertFalse(itemDao.isItemExist("/nonexistent"));
    }

    @Test
    @DisplayName("updateItem 更新名称和路径")
    void updateItem() {
        int sheetId = createSheet("歌单A");
        int workId = createWork("RJ1", "作品A", "/audio/RJ1");
        itemDao.addItem(createItem("原名", "/a/1.mp3", workId, sheetId));
        ItemData item = itemDao.getItemsBySheet(sheetId).get(0);
        item.setName("新名");
        item.setFilepath("/a/new.mp3");
        assertTrue(itemDao.updateItem(item));
        ItemData result = itemDao.getItemById(item.getId());
        assertEquals("新名", result.getName());
        assertEquals("/a/new.mp3", result.getFilepath());
    }

    @Test
    @DisplayName("moveItemToSheet 移动到其他歌单")
    void moveItemToSheet() {
        int sheet1 = createSheet("歌单A");
        // 创建第二个歌单
        sheetDao.createSheet("歌单B");
        int sheet2 = sheetDao.getAllSheets().get(1).getId();
        int workId = createWork("RJ1", "作品A", "/audio/RJ1");
        itemDao.addItem(createItem("曲目1", "/a/1.mp3", workId, sheet1));
        int itemId = itemDao.getItemsBySheet(sheet1).get(0).getId();
        assertTrue(itemDao.moveItemToSheet(itemId, sheet2));
        assertTrue(itemDao.getItemsBySheet(sheet1).isEmpty());
        assertEquals(1, itemDao.getItemsBySheet(sheet2).size());
    }

    @Test
    @DisplayName("deleteItem 按ID删除")
    void deleteItem() {
        int sheetId = createSheet("歌单A");
        int workId = createWork("RJ1", "作品A", "/audio/RJ1");
        itemDao.addItem(createItem("曲目1", "/a/1.mp3", workId, sheetId));
        int itemId = itemDao.getItemsBySheet(sheetId).get(0).getId();
        assertTrue(itemDao.deleteItem(itemId));
        assertTrue(itemDao.getItemsBySheet(sheetId).isEmpty());
    }

    @Test
    @DisplayName("deleteItemBySheetId 按歌单删除全部")
    void deleteItemBySheetId() {
        int sheetId = createSheet("歌单A");
        int workId = createWork("RJ1", "作品A", "/audio/RJ1");
        itemDao.addItem(createItem("曲目1", "/a/1.mp3", workId, sheetId));
        itemDao.addItem(createItem("曲目2", "/a/2.mp3", workId, sheetId));
        assertTrue(itemDao.deleteItemBySheetId(sheetId));
        assertTrue(itemDao.getItemsBySheet(sheetId).isEmpty());
    }

    @Test
    @DisplayName("getItemsWithWorkInfoBySheet JOIN 返回作品信息")
    void getItemsWithWorkInfoBySheet() {
        int sheetId = createSheet("歌单A");
        int workId = createWork("RJ1", "作品A", "/audio/RJ1");
        itemDao.addItem(createItem("曲目1", "/a/1.mp3", workId, sheetId));
        List<ItemDataEx> result = itemDao.getItemsWithWorkInfoBySheet(sheetId);
        assertEquals(1, result.size());
        assertNotNull(result.get(0).getWork());
        assertEquals("RJ1", result.get(0).getWork().getWorkId());
        assertEquals(1, result.get(0).getWork().getStorageSourceId());
    }

    @Test
    @DisplayName("getItemsByWork 按作品ID查关联项目")
    void getItemsByWork() {
        int sheetId = createSheet("歌单A");
        int workId = createWork("RJ1", "作品A", "/audio/RJ1");
        itemDao.addItem(createItem("曲目1", "/a/1.mp3", workId, sheetId));
        List<ItemData> result = itemDao.getItemsByWork(String.valueOf(workId));
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getItemsByWorkAndFilepath 按作品ID+路径查")
    void getItemsByWorkAndFilepath() {
        int sheetId = createSheet("歌单A");
        int workId = createWork("RJ1", "作品A", "/audio/RJ1");
        itemDao.addItem(createItem("曲目1", "/a/1.mp3", workId, sheetId));
        List<ItemData> result = itemDao.getItemsByWorkAndFilepath(workId, "/a/1.mp3");
        assertEquals(1, result.size());
    }
}
