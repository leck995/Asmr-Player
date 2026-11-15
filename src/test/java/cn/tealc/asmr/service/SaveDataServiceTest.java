package cn.tealc.asmr.service;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.SaveDataDao;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.GalgameSaveData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SaveDataService} 单元测试。
 *
 * @author Leck
 */
class SaveDataServiceTest extends BaseDatabaseTest {

    private SaveDataService service() {
        DI.bind(SaveDataDao.class, SaveDataDao::new);
        return DI.get(SaveDataService.class);
    }

    private GalgameSaveData newSave(String title) {
        GalgameSaveData save = new GalgameSaveData();
        save.setTitle(title);
        save.setChapter("chapter1.mp3");
        save.setFilepath("/audio/test/chapter1.mp3");
        save.setThumb("/save/123.png");
        save.setBackground("/bg/bg1.png");
        save.setTime(120.5);
        save.setText("这里是存档文本");
        save.setSaveDate("2026/08/31");
        save.setAsmrId(0);
        return save;
    }

    @Test
    @DisplayName("addSave 插入存档后 getSaves 能查回并验证字段")
    void addSave_andGetSaves() {
        SaveDataService svc = service();
        GalgameSaveData save = newSave("测试存档1");

        boolean ok = svc.addSave(save);
        assertTrue(ok, "addSave 应返回 true");

        List<GalgameSaveData> result = svc.getSaves(1, 10);
        assertNotNull(result);
        assertEquals(1, result.size());

        GalgameSaveData loaded = result.get(0);
        assertEquals("测试存档1", loaded.getTitle());
        assertEquals("chapter1.mp3", loaded.getChapter());
        assertEquals(120.5, loaded.getTime());
        assertEquals("2026/08/31", loaded.getSaveDate());
    }

    @Test
    @DisplayName("getSaveCount 返回正确的存档总数")
    void getSaveCount() {
        SaveDataService svc = service();
        assertEquals(0, svc.getSaveCount());

        svc.addSave(newSave("存档1"));
        svc.addSave(newSave("存档2"));
        svc.addSave(newSave("存档3"));

        assertEquals(3, svc.getSaveCount());
    }

    @Test
    @DisplayName("deleteSaveById 删除后存档不存在")
    void deleteSaveById() {
        SaveDataService svc = service();
        GalgameSaveData save = newSave("待删除");
        svc.addSave(save);

        List<GalgameSaveData> list = svc.getSaves(1, 10);
        assertFalse(list.isEmpty());
        int id = list.get(0).getId();

        boolean deleted = svc.deleteSaveById(id);
        assertTrue(deleted);

        List<GalgameSaveData> after = svc.getSaves(1, 10);
        assertTrue(after == null || after.isEmpty(), "删除后应无记录");
    }

    @Test
    @DisplayName("getSaves 分页：插入 5 条，每页 2 条，第 2 页返回 2 条")
    void getSaves_pagination() {
        SaveDataService svc = service();
        for (int i = 0; i < 5; i++) {
            svc.addSave(newSave("存档" + i));
        }

        // 第 1 页（index=1, size=2）
        List<GalgameSaveData> page1 = svc.getSaves(1, 2);
        assertEquals(2, page1.size());

        // 第 2 页
        List<GalgameSaveData> page2 = svc.getSaves(2, 2);
        assertEquals(2, page2.size());

        // 第 3 页（只剩 1 条）
        List<GalgameSaveData> page3 = svc.getSaves(3, 2);
        assertEquals(1, page3.size());
    }
}
