package cn.tealc.asmr.service;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.WorkDao;
import cn.tealc.asmr.dao.collection.CollectionItemDao;
import cn.tealc.asmr.dao.collection.CollectionSheetDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.collection.ItemData;
import cn.tealc.asmr.model.collection.ItemDataEx;
import cn.tealc.asmr.model.collection.SheetData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CollectionService} 单元测试。
 * <p>
 * 覆盖歌单 CRUD、收藏项 CRUD、syncItems 差量同步逻辑。
 * 收藏项表有 FK work_id→asmr_works.id，测试需先插入 AsmrWork 记录。
 *
 * @author Leck
 */
class CollectionServiceTest extends BaseDatabaseTest {

    private CollectionService service() {
        DI.bind(CollectionSheetDao.class, CollectionSheetDao::new);
        DI.bind(CollectionItemDao.class, CollectionItemDao::new);
        DI.bind(WorkDao.class, AsmrWorkDao::new);
        return DI.get(CollectionService.class);
    }

    /** 插入一个作品记录，返回其真实 ID（收藏项 FK 依赖；addWork 不回填自增键） */
    private int insertWork() {
        AsmrWork work = new AsmrWork();
        work.setStorageSourceId(1);
        work.setTitle("收藏测试作品");
        work.setWorkId("RJ-COLL-001");
        work.setFilepath("/audio/lib/RJ-COLL-001");
        work.setStorageType(0);
        new AsmrWorkDao().addWork(work);
        // addWork 不回填自增 ID，按 filepath 唯一键查回
        return new AsmrWorkDao().getWorkByFilepath(work.getFilepath()).getId();
    }

    /** 构造一个收藏项 */
    private ItemData newItem(int workId, int sheetId, String filepath) {
        ItemData item = new ItemData();
        item.setName("track01.mp3");
        item.setFilepath(filepath);
        item.setWorkId(workId);
        item.setSheetId(sheetId);
        return item;
    }

    // ==================== 歌单 CRUD ====================

    @Test
    @DisplayName("createSheet 后 getAllSheets 能查回")
    void createSheet_andGetAllSheets() {
        CollectionService svc = service();
        assertTrue(svc.createSheet("我的歌单"));
        assertTrue(svc.createSheet("另一个歌单"));

        List<SheetData> sheets = svc.getAllSheets();
        assertEquals(2, sheets.size());
        assertEquals("我的歌单", sheets.get(0).getSheetName());
    }

    @Test
    @DisplayName("createSheet 保存描述")
    void createSheet_withDescription() {
        CollectionService svc = service();
        assertTrue(svc.createSheet("带描述歌单", "描述内容"));

        SheetData sheet = svc.getAllSheets().get(0);
        assertEquals("带描述歌单", sheet.getSheetName());
        assertEquals("描述内容", sheet.getDescription());
        assertFalse(sheet.getUpdateDateTime().isBlank());
    }

    @Test
    @DisplayName("updateSheetDetails 更新名称与描述后查回")
    void updateSheetDetails() {
        CollectionService svc = service();
        svc.createSheet("旧名称", "旧描述");
        int sheetId = svc.getAllSheets().get(0).getId();

        boolean ok = svc.updateSheetDetails(sheetId, "新名称", "新描述");
        assertTrue(ok);

        SheetData sheet = svc.getSheetById(sheetId);
        assertEquals("新名称", sheet.getSheetName());
        assertEquals("新描述", sheet.getDescription());
        assertFalse(sheet.getUpdateDateTime().isBlank());
    }

    @Test
    @DisplayName("deleteSheet 级联删除其下收藏项")
    void deleteSheet_cascadeDeleteItems() {
        CollectionService svc = service();
        int workId = insertWork();

        svc.createSheet("待删歌单");
        int sheetId = svc.getAllSheets().get(0).getId();

        // 插入收藏项到该歌单
        svc.syncItems(List.of(newItem(workId, sheetId, "rel/path1.mp3")), workId, "rel/path1.mp3");
        assertEquals(1, svc.getItemsByWorkAndFilepath(workId, "rel/path1.mp3").size());

        // 删除歌单，收藏项应被级联删除
        boolean ok = svc.deleteSheet(sheetId);
        assertTrue(ok);

        assertEquals(0, svc.getItemsByWorkAndFilepath(workId, "rel/path1.mp3").size(),
                "删歌单后收藏项应被级联删除");
    }

    // ==================== 收藏项 CRUD ====================

    @Test
    @DisplayName("deleteItem 删除单个收藏项")
    void deleteItem() {
        CollectionService svc = service();
        int workId = insertWork();
        svc.createSheet("歌单A");
        int sheetId = svc.getAllSheets().get(0).getId();

        svc.syncItems(List.of(newItem(workId, sheetId, "rel/path.mp3")), workId, "rel/path.mp3");
        List<ItemData> items = svc.getItemsByWorkAndFilepath(workId, "rel/path.mp3");
        assertEquals(1, items.size());

        boolean ok = svc.deleteItem(items.get(0).getId());
        assertTrue(ok);
        assertEquals(0, svc.getItemsByWorkAndFilepath(workId, "rel/path.mp3").size());
    }

    // ==================== syncItems 差量同步 ====================

    @Test
    @DisplayName("syncItems 新增：目标列表有 2 个歌单的项，全部插入")
    void syncItems_insertNew() {
        CollectionService svc = service();
        int workId = insertWork();
        svc.createSheet("歌单1");
        svc.createSheet("歌单2");
        List<SheetData> sheets = svc.getAllSheets();
        int s1 = sheets.get(0).getId();
        int s2 = sheets.get(1).getId();

        List<ItemData> newItems = List.of(
                newItem(workId, s1, "rel/track.mp3"),
                newItem(workId, s2, "rel/track.mp3")
        );
        svc.syncItems(newItems, workId, "rel/track.mp3");

        List<ItemData> result = svc.getItemsByWorkAndFilepath(workId, "rel/track.mp3");
        assertEquals(2, result.size(), "应有 2 条收藏项");
    }

    @Test
    @DisplayName("syncItems 删除多余：原有 3 个歌单的项，目标只保留 1 个，删掉 2 个")
    void syncItems_deleteExcess() {
        CollectionService svc = service();
        int workId = insertWork();
        svc.createSheet("歌单1");
        svc.createSheet("歌单2");
        svc.createSheet("歌单3");
        List<SheetData> sheets = svc.getAllSheets();
        int s1 = sheets.get(0).getId();
        int s2 = sheets.get(1).getId();
        int s3 = sheets.get(2).getId();

        // 先插入 3 个歌单的收藏项
        List<ItemData> initial = List.of(
                newItem(workId, s1, "rel/track.mp3"),
                newItem(workId, s2, "rel/track.mp3"),
                newItem(workId, s3, "rel/track.mp3")
        );
        svc.syncItems(initial, workId, "rel/track.mp3");
        assertEquals(3, svc.getItemsByWorkAndFilepath(workId, "rel/track.mp3").size());

        // 目标只保留 s1
        svc.syncItems(List.of(newItem(workId, s1, "rel/track.mp3")), workId, "rel/track.mp3");

        List<ItemData> result = svc.getItemsByWorkAndFilepath(workId, "rel/track.mp3");
        assertEquals(1, result.size(), "应只保留 1 条");
        assertEquals(s1, result.get(0).getSheetId());
    }

    @Test
    @DisplayName("syncItems 混合：原有 [s1,s2]，目标 [s2,s3]，s1 被删、s3 被增、s2 保留")
    void syncItems_mixed() {
        CollectionService svc = service();
        int workId = insertWork();
        svc.createSheet("歌单1");
        svc.createSheet("歌单2");
        svc.createSheet("歌单3");
        List<SheetData> sheets = svc.getAllSheets();
        int s1 = sheets.get(0).getId();
        int s2 = sheets.get(1).getId();
        int s3 = sheets.get(2).getId();

        // 原有 [s1, s2]
        svc.syncItems(List.of(
                newItem(workId, s1, "rel/track.mp3"),
                newItem(workId, s2, "rel/track.mp3")
        ), workId, "rel/track.mp3");

        // 目标 [s2, s3]
        svc.syncItems(List.of(
                newItem(workId, s2, "rel/track.mp3"),
                newItem(workId, s3, "rel/track.mp3")
        ), workId, "rel/track.mp3");

        List<ItemData> result = svc.getItemsByWorkAndFilepath(workId, "rel/track.mp3");
        assertEquals(2, result.size(), "应有 2 条（s2 保留 + s3 新增，s1 被删）");
        // 验证 s1 不在结果中
        assertFalse(result.stream().anyMatch(i -> i.getSheetId() == s1), "s1 应被删除");
        // 验证 s2 和 s3 都在
        assertTrue(result.stream().anyMatch(i -> i.getSheetId() == s2), "s2 应保留");
        assertTrue(result.stream().anyMatch(i -> i.getSheetId() == s3), "s3 应新增");
    }
}
