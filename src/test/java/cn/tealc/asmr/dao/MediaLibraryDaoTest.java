package cn.tealc.asmr.dao;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.source.StorageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MediaLibraryDao} 全量测试。
 * <p>
 * 基类 {@link BaseDatabaseTest} 不清 asmr_media_library 表，
 * 本类在 {@link #setUp} 中额外清空。
 *
 * @author Leck
 */
@DisplayName("MediaLibraryDao 测试")
class MediaLibraryDaoTest extends BaseDatabaseTest {

    private MediaLibraryDao dao;

    @BeforeEach
    void setUp() {
        clearTable("asmr_media_library");
        dao = new MediaLibraryDao();
    }

    /**
     * 构造测试媒体库对象。
     */
    private MediaLibrary createLibrary(String name, String path, int level, StorageType type, Integer accountId) {
        return new MediaLibrary(name, path, level, type, accountId == null ? 1 : accountId);
    }

    @Test
    @DisplayName("add 正常添加并回填 id")
    void add_normalInsert_backfillsId() {
        MediaLibrary library = createLibrary("库A", "/audio", 1, StorageType.LOCAL, null);
        assertTrue(dao.add(library));
        assertNotNull(library.getId());
        assertTrue(library.getId() > 0);
        MediaLibrary saved = dao.getById(library.getId());
        assertNull(saved.getLastImportedAt());
        assertNull(saved.getLastScrapedAt());
    }

    @Test
    @DisplayName("getAll 空表返回空列表")
    void getAll_emptyTable_returnsEmptyList() {
        assertTrue(dao.getAll().isEmpty());
    }

    @Test
    @DisplayName("getAll 有数据返回全部")
    void getAll_hasData_returnsAll() {
        dao.add(createLibrary("库A", "/audio", 1, StorageType.LOCAL, null));
        dao.add(createLibrary("库B", "/video", 1, StorageType.LOCAL, null));
        assertEquals(2, dao.getAll().size());
    }

    @Test
    @DisplayName("getById 存在返回媒体库")
    void getById_exists() {
        MediaLibrary library = createLibrary("库A", "/audio", 1, StorageType.LOCAL, null);
        dao.add(library);
        MediaLibrary result = dao.getById(library.getId());
        assertNotNull(result);
        assertEquals("库A", result.getName());
    }

    @Test
    @DisplayName("getById 不存在返回 null")
    void getById_notExists() {
        assertNull(dao.getById(99999));
    }

    @Test
    @DisplayName("getByStorageType 返回对应类型")
    void getByStorageType_returnsCorrectType() {
        dao.add(createLibrary("本地", "/audio", 1, StorageType.LOCAL, null));
        dao.add(createLibrary("远程", "/remote", 1, StorageType.OPENLIST, 1));
        List<MediaLibrary> local = dao.getByStorageType(StorageType.LOCAL);
        assertEquals(1, local.size());
        assertEquals("本地", local.get(0).getName());
    }

    @Test
    @DisplayName("getByPath 存在返回媒体库")
    void getByPath_exists() {
        dao.add(createLibrary("库A", "/audio", 1, StorageType.LOCAL, null));
        MediaLibrary result = dao.getByPath("/audio");
        assertNotNull(result);
        assertEquals("库A", result.getName());
    }

    @Test
    @DisplayName("getByPath 不存在返回 null")
    void getByPath_notExists() {
        assertNull(dao.getByPath("/nonexistent"));
    }

    @Test
    @DisplayName("update 更新名称和层级")
    void update_nameAndLevel() {
        MediaLibrary library = createLibrary("库A", "/audio", 1, StorageType.LOCAL, null);
        dao.add(library);
        library.setName("新名称");
        library.setLevel(3);
        assertTrue(dao.update(library));
        MediaLibrary result = dao.getById(library.getId());
        assertEquals("新名称", result.getName());
        assertEquals(3, result.getLevel());
    }

    @Test
    @DisplayName("记录导入和刮削时间后可读取")
    void updateActivityTimes_persistsAndMaps() {
        MediaLibrary library = createLibrary("库A", "/audio", 1, StorageType.LOCAL, null);
        dao.add(library);
        LocalDateTime importedAt = LocalDateTime.of(2026, 10, 19, 10, 30);
        LocalDateTime scrapedAt = LocalDateTime.of(2026, 10, 19, 11, 45);

        assertTrue(dao.updateLastImportedAt(library.getId(), importedAt));
        assertTrue(dao.updateLastScrapedAt(library.getId(), scrapedAt));

        MediaLibrary result = dao.getById(library.getId());
        assertEquals(importedAt, result.getLastImportedAt());
        assertEquals(scrapedAt, result.getLastScrapedAt());
    }

    @Test
    @DisplayName("编辑媒体库配置时保留独立更新的活动时间")
    void update_preservesActivityTimes() {
        MediaLibrary library = createLibrary("库A", "/audio", 1, StorageType.LOCAL, null);
        dao.add(library);
        LocalDateTime importedAt = LocalDateTime.of(2026, 10, 19, 10, 30, 15);
        LocalDateTime scrapedAt = importedAt.plusHours(1);
        dao.updateLastImportedAt(library.getId(), importedAt);
        dao.updateLastScrapedAt(library.getId(), scrapedAt);

        library.setName("新名称");
        assertTrue(dao.update(library));

        MediaLibrary saved = dao.getById(library.getId());
        assertEquals("新名称", saved.getName());
        assertEquals(importedAt, saved.getLastImportedAt());
        assertEquals(scrapedAt, saved.getLastScrapedAt());
        assertEquals(importedAt, dao.getAll().getFirst().getLastImportedAt());
    }

    @Test
    @DisplayName("活动时间更新不会重新创建已删除的媒体库")
    void updateActivityTimes_deletedLibraryReturnsFalse() {
        MediaLibrary library = createLibrary("库A", "/audio", 1, StorageType.LOCAL, null);
        dao.add(library);
        dao.deleteById(library.getId());
        assertFalse(dao.updateLastImportedAt(library.getId(), LocalDateTime.now()));
        assertFalse(dao.updateLastScrapedAt(library.getId(), LocalDateTime.now()));
        assertNull(dao.getById(library.getId()));
    }

    @Test
    @DisplayName("deleteById 正常删除")
    void deleteById_normalDelete() {
        MediaLibrary library = createLibrary("库A", "/audio", 1, StorageType.LOCAL, null);
        dao.add(library);
        assertTrue(dao.deleteById(library.getId()));
        assertNull(dao.getById(library.getId()));
    }

    @Test
    @DisplayName("deleteById 事务重载方法可用")
    void deleteById_connectionOverload() throws Exception {
        MediaLibrary library = createLibrary("库A", "/audio", 1, StorageType.LOCAL, null);
        dao.add(library);
        Connection conn = JdbcUtils.getConnection();
        try {
            assertTrue(dao.deleteById(conn, library.getId()));
        } finally {
            conn.setAutoCommit(true);
            JdbcUtils.close(conn);
        }
        assertNull(dao.getById(library.getId()));
    }


}
