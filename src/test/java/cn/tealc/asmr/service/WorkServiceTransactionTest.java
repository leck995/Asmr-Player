package cn.tealc.asmr.service;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.dao.AsmrWorkDao;
import cn.tealc.asmr.dao.JdbcUtils;
import cn.tealc.asmr.di.DI;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.source.StorageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WorkService} 和 {@link MediaLibraryService} 的事务测试。
 * <p>
 * 验证 {@link MediaLibraryService#deleteLibraryWithWorks} 的手动事务
 * 和 {@link WorkService#deleteByParentFolder(Connection, String)} 重载。
 *
 * @author Leck
 */
@DisplayName("WorkService 事务测试")
class WorkServiceTransactionTest extends BaseDatabaseTest {

    private WorkService workService;
    private MediaLibraryService mediaLibraryService;
    private AsmrWorkDao workDao;

    @BeforeEach
    void setUp() {
        clearTable("asmr_media_library");
        DI.bind(cn.tealc.asmr.dao.WorkDao.class, AsmrWorkDao::new);
        DI.bind(cn.tealc.asmr.dao.MediaLibraryDao.class, cn.tealc.asmr.dao.MediaLibraryDao::new);
        workService = DI.get(WorkService.class);
        mediaLibraryService = DI.get(MediaLibraryService.class);
        workDao = new AsmrWorkDao();
    }

    /**
     * 构造并插入测试作品。
     */
    private void insertWork(String workId, String filepath, String parentPath) {
        AsmrWork work = new AsmrWork();
        work.setStorageSourceId(1);
        work.setWorkId(workId);
        work.setTitle("测试" + workId);
        work.setFilepath(filepath);
        work.setParentPath(parentPath);
        work.setStorageType(0);
        workDao.addWork(work);
    }

    @Test
    @DisplayName("deleteByParentFolder Connection 重载正常删除")
    void deleteByParentFolder_connectionOverload() throws Exception {
        insertWork("RJ1", "/audio/RJ1", "/audio");
        insertWork("RJ2", "/audio/RJ2", "/audio");
        insertWork("RJ3", "/video/RJ3", "/video");
        Connection conn = JdbcUtils.getConnection();
        try {
            conn.setAutoCommit(false);
            workService.deleteByParentFolder(conn, "/audio");
            conn.commit();
        } finally {
            conn.setAutoCommit(true);
            JdbcUtils.close(conn);
        }
        assertEquals(1, workDao.getAllWorks().size());
    }

    @Test
    @DisplayName("deleteLibraryWithWorks 清空数据 - 媒体库和作品都删除")
    void deleteLibraryWithWorks_clearData() {
        // 插入媒体库和关联作品
        MediaLibrary library = new MediaLibrary("库A", "/audio", 1, StorageType.LOCAL, 1);
        mediaLibraryService.add(library);
        insertWork("RJ1", "/audio/RJ1", "/audio");
        insertWork("RJ2", "/audio/RJ2", "/audio");
        try (Connection connection = JdbcUtils.getConnection(); java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE asmr_works SET media_library_id = " + library.getId() + " WHERE parent_path = '/audio'");
        } catch (java.sql.SQLException e) { throw new RuntimeException(e); }
        // 不属于该媒体库的作品不应被删除
        insertWork("RJ3", "/video/RJ3", "/video");
        mediaLibraryService.deleteLibraryWithWorks(library);
        // 媒体库记录删除
        assertNull(mediaLibraryService.getById(library.getId()));
        // 关联作品删除
        assertEquals(1, workDao.getAllWorks().size());
        assertEquals("RJ3", workDao.getAllWorks().get(0).getWorkId());
    }

    @Test
    @DisplayName("delete 不清空数据 - 只删媒体库记录，作品保留")
    void delete_libraryOnly_worksRetained() {
        MediaLibrary library = new MediaLibrary("库A", "/audio", 1, StorageType.LOCAL, 1);
        mediaLibraryService.add(library);
        insertWork("RJ1", "/audio/RJ1", "/audio");
        mediaLibraryService.delete(library.getId());
        assertNull(mediaLibraryService.getById(library.getId()));
        assertEquals(1, workDao.getAllWorks().size());
    }

    @Test
    @DisplayName("删除媒体库时保留同路径但未关联的作品")
    void deleteLibrary_doesNotDeleteUnassociatedWorks() {
        MediaLibrary library = new MediaLibrary("库A", "/audio", 1, StorageType.LOCAL, 1);
        mediaLibraryService.add(library);
        insertWork("RJ1", "/audio/RJ1", "/audio");

        assertTrue(mediaLibraryService.deleteLibraryWithWorks(library));
        assertEquals(1, workDao.getAllWorks().size());
    }

    @Test
    @DisplayName("deleteByParentFolder 路径归一化 - 反斜杠")
    void deleteByParentFolder_normalizeBackslash() throws Exception {
        insertWork("RJ1", "/audio/RJ1", "/audio");
        Connection conn = JdbcUtils.getConnection();
        try {
            conn.setAutoCommit(false);
            workService.deleteByParentFolder(conn, "\\audio");
            conn.commit();
        } finally {
            conn.setAutoCommit(true);
            JdbcUtils.close(conn);
        }
        assertTrue(workDao.getAllWorks().isEmpty());
    }
}
