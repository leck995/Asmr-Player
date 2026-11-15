package cn.tealc.asmr.dao;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.model.GalgameSaveData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SaveDataDao} 全量测试。
 * <p>
 * 操作表 {@code asmr_galgame_save}，返回 {@link GalgameSaveData}。该表的清空
 * 已在 {@link BaseDatabaseTest} 的 {@code @BeforeEach} 中处理，故本类无需额外清表。
 *
 * @author Leck
 */
@DisplayName("SaveDataDao 测试")
class SaveDataDaoTest extends BaseDatabaseTest {

    private SaveDataDao dao;

    @BeforeEach
    void setUp() {
        dao = new SaveDataDao();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试存档对象，填充全部字段。
     */
    private GalgameSaveData createSave(String title, String chapter) {
        GalgameSaveData save = new GalgameSaveData();
        save.setTitle(title);
        save.setChapter(chapter);
        save.setFilepath("/audio/" + title + "/01.mp3");
        save.setThumb("/thumb/" + title + ".png");
        save.setBackground("/bg/" + title + ".png");
        save.setTime(120.5);
        save.setText("存档文本");
        save.setSaveDate("2024-01-01 12:00:00");
        save.setAsmrId(1);
        return save;
    }

    @Test
    @DisplayName("addSave 正常添加返回 true")
    void addSave_normalInsert_returnsTrue() {
        GalgameSaveData save = createSave("作品A", "第一章");
        assertTrue(dao.addSave(save));
    }

    @Test
    @DisplayName("addSave 添加多条验证分页")
    void addSave_multiple_verifyPaging() {
        dao.addSave(createSave("作品A", "第一章"));
        dao.addSave(createSave("作品B", "第二章"));
        dao.addSave(createSave("作品C", "第三章"));

        // 第一页（index=1, size=2）返回 2 条
        List<GalgameSaveData> page1 = dao.getAllSaves(1, 2);
        assertEquals(2, page1.size());

        // 第二页（index=2, size=2）返回 1 条
        List<GalgameSaveData> page2 = dao.getAllSaves(2, 2);
        assertEquals(1, page2.size());
    }

    @Test
    @DisplayName("getAllSavesCount 返回总数")
    void getAllSavesCount_returnsTotal() {
        dao.addSave(createSave("作品A", "第一章"));
        dao.addSave(createSave("作品B", "第二章"));
        dao.addSave(createSave("作品C", "第三章"));

        assertEquals(3, dao.getAllSavesCount());
    }

    @Test
    @DisplayName("deleteSaveById 正常删除，count 减少")
    void deleteSaveById_normalDelete() {
        dao.addSave(createSave("作品A", "第一章"));
        // 获取第一条记录的 id
        List<GalgameSaveData> saves = dao.getAllSaves(1, 10);
        assertEquals(1, saves.size());
        int firstId = saves.get(0).getId();

        int before = dao.getAllSavesCount();
        assertTrue(dao.deleteSaveById(firstId));
        int after = dao.getAllSavesCount();
        assertEquals(before - 1, after);
    }
}
