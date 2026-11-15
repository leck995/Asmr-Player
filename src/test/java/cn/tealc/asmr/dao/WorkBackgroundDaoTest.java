package cn.tealc.asmr.dao;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.model.WorkBackgroundRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WorkBackgroundDao} 测试。
 * <p>
 * 每部作品一行，记录当前背景的来源与路径；{@code source_path} 为空表示
 * 已扫描但没有可用背景。
 *
 * @author Leck
 */
@DisplayName("WorkBackgroundDao 测试")
class WorkBackgroundDaoTest extends BaseDatabaseTest {

    private WorkBackgroundDao dao;

    @BeforeEach
    void setUp() {
        clearTable("asmr_work_background");
        dao = new WorkBackgroundDao();
    }

    @Test
    @DisplayName("save 后 findByWorkId 能读到完整字段")
    void save_thenFind() {
        int workId = insertWork();
        WorkBackgroundRecord record = new WorkBackgroundRecord(workId, 1, 3,
                "/RJ001/background01.jpg", 1000L);

        assertTrue(dao.save(record));

        WorkBackgroundRecord found = dao.findByWorkId(workId);
        assertNotNull(found);
        assertEquals(workId, found.workRecordId());
        assertEquals(1, found.storageType());
        assertEquals(3, found.storageSourceId());
        assertEquals("/RJ001/background01.jpg", found.sourcePath());
        assertEquals(1000L, found.scannedAt());
    }

    @Test
    @DisplayName("save 同一作品覆盖旧记录")
    void save_replacesExistingRow() {
        int workId = insertWork();
        dao.save(new WorkBackgroundRecord(workId, 1, 3, "/RJ001/a.jpg", 1000L));

        assertTrue(dao.save(new WorkBackgroundRecord(workId, 0, 1, "D:/ASMR/RJ001/b.png", 2000L)));

        WorkBackgroundRecord found = dao.findByWorkId(workId);
        assertNotNull(found);
        assertEquals(0, found.storageType());
        assertEquals(1, found.storageSourceId());
        assertEquals("D:/ASMR/RJ001/b.png", found.sourcePath());
        assertEquals(2000L, found.scannedAt());
    }

    @Test
    @DisplayName("source_path 为空表示已扫描但没有背景")
    void save_nullPath() {
        int workId = insertWork();
        assertTrue(dao.save(new WorkBackgroundRecord(workId, 1, 3, null, 3000L)));

        WorkBackgroundRecord found = dao.findByWorkId(workId);
        assertNotNull(found);
        assertNull(found.sourcePath());
        assertEquals(3000L, found.scannedAt());
    }

    @Test
    @DisplayName("deleteByWorkId 删除后查询返回 null")
    void deleteByWorkId() {
        int workId = insertWork();
        dao.save(new WorkBackgroundRecord(workId, 1, 3, "/RJ001/a.jpg", 1000L));

        assertTrue(dao.deleteByWorkId(workId));
        assertNull(dao.findByWorkId(workId));
    }

    @Test
    @DisplayName("删除作品时级联删除背景记录")
    void deleteWork_cascades() {
        int workId = insertWork();
        dao.save(new WorkBackgroundRecord(workId, 1, 3, "/RJ001/a.jpg", 1000L));

        try (Connection conn = JdbcUtils.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM asmr_works WHERE id = " + workId);
        } catch (SQLException e) {
            fail(e);
        }

        assertNull(dao.findByWorkId(workId));
    }

    private int insertWork() {
        try (Connection conn = JdbcUtils.getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO asmr_works(title, storage_source_id) VALUES ('RJ001', 1)");
            try (ResultSet keys = st.executeQuery("SELECT last_insert_rowid()")) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("插入测试作品失败", e);
        }
    }
}
