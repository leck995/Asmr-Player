package cn.tealc.asmr.dao.collection;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.JdbcUtils;
import cn.tealc.asmr.dao.collection.CollectionSheetDao;
import cn.tealc.asmr.model.collection.SheetData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CollectionSheetDao} 全量测试。
 *
 * @author Leck
 */
@DisplayName("CollectionSheetDao 测试")
class CollectionSheetDaoTest extends BaseDatabaseTest {

    private CollectionSheetDao dao;

    @BeforeEach
    void setUp() {
        dao = new CollectionSheetDao();
    }

    @Test
    @DisplayName("createSheet 正常创建歌单")
    void createSheet_normalCreate() {
        assertTrue(dao.createSheet("我的歌单"));
    }

    @Test
    @DisplayName("getSheetById 存在返回歌单")
    void getSheetById_exists() {
        dao.createSheet("歌单A");
        List<SheetData> sheets = dao.getAllSheets();
        int id = sheets.get(0).getId();
        SheetData result = dao.getSheetById(id);
        assertNotNull(result);
        assertEquals("歌单A", result.getSheetName());
    }

    @Test
    @DisplayName("getSheetById 不存在返回 null")
    void getSheetById_notExists() {
        assertNull(dao.getSheetById(99999));
    }

    @Test
    @DisplayName("getAllSheets 空表返回空列表")
    void getAllSheets_emptyTable() {
        assertTrue(dao.getAllSheets().isEmpty());
    }

    @Test
    @DisplayName("getAllSheets 有数据返回全部")
    void getAllSheets_hasData() {
        dao.createSheet("歌单A");
        dao.createSheet("歌单B");
        assertEquals(2, dao.getAllSheets().size());
    }

    @Test
    @DisplayName("getAllSheets 置顶排在前面")
    void getAllSheets_topSortsFirst() {
        dao.createSheet("普通歌单");
        dao.createSheet("置顶歌单");
        List<SheetData> sheets = dao.getAllSheets();
        int topId = sheets.stream().filter(SheetData::isTop).findFirst().orElseThrow().getId();
        dao.setTop(sheets.get(0).getId(), false); // 第一个取消置顶
        // setTop 后重新查
        dao.setTop(topId, true);
        List<SheetData> result = dao.getAllSheets();
        assertTrue(result.get(0).isTop());
    }

    @Test
    @DisplayName("updateSheet 更新名称和置顶")
    void updateSheet_nameAndTop() {
        dao.createSheet("原名");
        SheetData sheet = dao.getAllSheets().get(0);
        sheet.setSheetName("新名");
        sheet.setTop(true);
        assertTrue(dao.updateSheet(sheet.getId(), sheet));
        SheetData result = dao.getSheetById(sheet.getId());
        assertEquals("新名", result.getSheetName());
        assertTrue(result.isTop());
    }

    @Test
    @DisplayName("createSheet 保存描述与创建/修改时间")
    void createSheet_withDescription() {
        assertTrue(dao.createSheet("带描述歌单", "用于测试的描述"));
        SheetData sheet = dao.getAllSheets().get(0);
        assertEquals("带描述歌单", sheet.getSheetName());
        assertEquals("用于测试的描述", sheet.getDescription());
        assertFalse(sheet.getCreateDateTime().isBlank());
        assertFalse(sheet.getUpdateDateTime().isBlank());
    }

    @Test
    @DisplayName("updateSheetDetails 更新名称、描述与修改时间")
    void updateSheetDetails_updatesDescriptionAndTime() throws Exception {
        dao.createSheet("原名", "旧描述");
        SheetData sheet = dao.getAllSheets().get(0);
        try (Connection connection = JdbcUtils.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("UPDATE asmr_collect_sheet SET update_date_time = '2000-01-01 00:00:00' WHERE id = " + sheet.getId());
        }

        assertTrue(dao.updateSheetDetails(sheet.getId(), "新名", "新描述"));

        SheetData updated = dao.getSheetById(sheet.getId());
        assertEquals("新名", updated.getSheetName());
        assertEquals("新描述", updated.getDescription());
        assertEquals(sheet.getCreateDateTime(), updated.getCreateDateTime());
        assertNotEquals("2000-01-01 00:00:00", updated.getUpdateDateTime());
    }

    @Test
    @DisplayName("getSheetSummaries 返回描述与时间")
    void getSheetSummaries_includesMetadata() {
        dao.createSheet("摘要歌单", "摘要描述");
        SheetData sheet = dao.getSheetSummaries().get(0).getSheet();
        assertEquals("摘要描述", sheet.getDescription());
        assertFalse(sheet.getUpdateDateTime().isBlank());
    }

    @Test
    @DisplayName("setTop 设置置顶")
    void setTop() {
        dao.createSheet("歌单A");
        int id = dao.getAllSheets().get(0).getId();
        assertTrue(dao.setTop(id, true));
        assertTrue(dao.getSheetById(id).isTop());
        assertTrue(dao.setTop(id, false));
        assertFalse(dao.getSheetById(id).isTop());
    }

    @Test
    @DisplayName("deleteSheet 正常删除")
    void deleteSheet_normalDelete() {
        dao.createSheet("歌单A");
        int id = dao.getAllSheets().get(0).getId();
        assertTrue(dao.deleteSheet(id));
        assertNull(dao.getSheetById(id));
    }

    @Test
    @DisplayName("isSheetNameExist 存在返回 true")
    void isSheetNameExist_exists() {
        dao.createSheet("歌单A");
        assertTrue(dao.isSheetNameExist("歌单A"));
    }

    @Test
    @DisplayName("isSheetNameExist 不存在返回 false")
    void isSheetNameExist_notExists() {
        assertFalse(dao.isSheetNameExist("不存在的歌单"));
    }
}
