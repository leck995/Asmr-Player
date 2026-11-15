package cn.tealc.asmr.dao;

import cn.tealc.asmr.BaseDatabaseTest;
import cn.tealc.asmr.model.CacheEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CacheDao} 全量测试。
 * <p>
 * 操作表 {@code asmr_cache_entry}，返回 {@link CacheEntry}。基类
 * {@link BaseDatabaseTest} 不清该表，故本类在 {@code setUp} 中额外清空。
 *
 * @author Leck
 */
@DisplayName("CacheDao 测试")
class CacheDaoTest extends BaseDatabaseTest {

    private CacheDao dao;

    @BeforeEach
    void setUp() {
        clearTable("asmr_cache_entry");
        dao = new CacheDao();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试缓存条目，填充全部字段。
     */
    private CacheEntry createEntry(String cacheKey, String sourcePath,
                                   long size, long lastAccessTime) {
        CacheEntry entry = new CacheEntry();
        entry.setCacheKey(cacheKey);
        entry.setSourcePath(sourcePath);
        entry.setFileType("text");
        entry.setLocalPath("resource/cache/text/" + cacheKey + ".txt");
        entry.setSize(size);
        entry.setLastAccessTime(lastAccessTime);
        entry.setCreateTime(System.currentTimeMillis());
        return entry;
    }

    @Test
    @DisplayName("save 新增缓存条目，findByKey 能查到")
    void save_insert() {
        CacheEntry entry = createEntry("key1", "/remote/file1.lrc", 100L, 1000L);
        assertTrue(dao.save(entry));

        CacheEntry found = dao.findByKey("key1");
        assertNotNull(found);
        assertEquals("key1", found.getCacheKey());
        assertEquals("/remote/file1.lrc", found.getSourcePath());
        assertEquals(100L, found.getSize());
    }

    @Test
    @DisplayName("save 替换已有条目，验证新 size")
    void save_replace() {
        CacheEntry entry = createEntry("key1", "/remote/file1.lrc", 100L, 1000L);
        dao.save(entry);

        // 改 size 后再 save（INSERT OR REPLACE）
        entry.setSize(999L);
        assertTrue(dao.save(entry));

        CacheEntry found = dao.findByKey("key1");
        assertNotNull(found);
        assertEquals(999L, found.getSize());
    }

    @Test
    @DisplayName("findBySourcePathPrefix 匹配目录本身与子项")
    void findBySourcePathPrefix_matchesExactAndChildren() {
        dao.save(createEntry("key1", "/remote/dir", 100L, 1000L));
        dao.save(createEntry("key2", "/remote/dir/file1.lrc", 100L, 1000L));
        dao.save(createEntry("key3", "/remote/dir2/file2.lrc", 100L, 1000L));

        List<CacheEntry> found = dao.findBySourcePathPrefix("/remote/dir");
        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(entry -> "key1".equals(entry.getCacheKey())));
        assertTrue(found.stream().anyMatch(entry -> "key2".equals(entry.getCacheKey())));
    }

    @Test
    @DisplayName("findBySourcePathPrefix 不存在返回空列表")
    void findBySourcePathPrefix_notExists_returnsEmpty() {
        assertTrue(dao.findBySourcePathPrefix("/nonexistent/path").isEmpty());
    }

    @Test
    @DisplayName("findByKey 存在返回条目")
    void findByKey_exists() {
        CacheEntry entry = createEntry("key1", "/remote/file1.lrc", 100L, 1000L);
        dao.save(entry);

        CacheEntry found = dao.findByKey("key1");
        assertNotNull(found);
        assertEquals("/remote/file1.lrc", found.getSourcePath());
    }

    @Test
    @DisplayName("findByKey 不存在返回 null")
    void findByKey_notExists_returnsNull() {
        assertNull(dao.findByKey("nonexistent_key"));
    }

    @Test
    @DisplayName("updateAccessTime 更新访问时间")
    void updateAccessTime_changesLastAccessTime() {
        CacheEntry entry = createEntry("key1", "/remote/file1.lrc", 100L, 1000L);
        dao.save(entry);

        long newTime = 9999L;
        assertTrue(dao.updateAccessTime("key1", newTime));

        CacheEntry found = dao.findByKey("key1");
        assertNotNull(found);
        assertEquals(newTime, found.getLastAccessTime());
        assertNotEquals(1000L, found.getLastAccessTime());
    }

    @Test
    @DisplayName("deleteByKey 正常删除，findByKey 返回 null")
    void deleteByKey_normalDelete() {
        CacheEntry entry = createEntry("key1", "/remote/file1.lrc", 100L, 1000L);
        dao.save(entry);

        assertTrue(dao.deleteByKey("key1"));
        assertNull(dao.findByKey("key1"));
    }

    @Test
    @DisplayName("deleteAll 清空全部，getEntryCount 返回 0")
    void deleteAll_clearsAll() {
        dao.save(createEntry("key1", "/remote/f1.lrc", 100L, 1000L));
        dao.save(createEntry("key2", "/remote/f2.lrc", 200L, 2000L));
        dao.save(createEntry("key3", "/remote/f3.lrc", 300L, 3000L));

        assertTrue(dao.deleteAll());
        assertEquals(0, dao.getEntryCount());
    }

    @Test
    @DisplayName("getTotalSize 返回缓存总大小")
    void getTotalSize_returnsSum() {
        dao.save(createEntry("key1", "/remote/f1.lrc", 100L, 1000L));
        dao.save(createEntry("key2", "/remote/f2.lrc", 200L, 2000L));
        dao.save(createEntry("key3", "/remote/f3.lrc", 300L, 3000L));

        assertEquals(600L, dao.getTotalSize());
    }

    @Test
    @DisplayName("getEntryCount 返回条目数")
    void getEntryCount_returnsCount() {
        dao.save(createEntry("key1", "/remote/f1.lrc", 100L, 1000L));
        dao.save(createEntry("key2", "/remote/f2.lrc", 200L, 2000L));
        dao.save(createEntry("key3", "/remote/f3.lrc", 300L, 3000L));

        assertEquals(3, dao.getEntryCount());
    }

    @Test
    @DisplayName("findLruEntries 按访问时间升序返回最旧的 N 条")
    void findLruEntries_sortedByAccessTime() {
        // 3 条不同 lastAccessTime
        dao.save(createEntry("key1", "/remote/f1.lrc", 100L, 3000L));
        dao.save(createEntry("key2", "/remote/f2.lrc", 200L, 1000L));
        dao.save(createEntry("key3", "/remote/f3.lrc", 300L, 2000L));

        List<CacheEntry> lru = dao.findLruEntries(2);
        assertEquals(2, lru.size());
        // 升序：最早 1000L 在前
        assertEquals("key2", lru.get(0).getCacheKey());
        assertEquals(1000L, lru.get(0).getLastAccessTime());
        // 次早 2000L
        assertEquals("key3", lru.get(1).getCacheKey());
        assertEquals(2000L, lru.get(1).getLastAccessTime());
    }
}
