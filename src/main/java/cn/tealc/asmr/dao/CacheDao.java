package cn.tealc.asmr.dao;

import cn.tealc.asmr.model.CacheEntry;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.RowProcessor;
import org.apache.commons.dbutils.BasicRowProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 远程资源缓存数据访问层。
 * <p>
 * 操作表 {@code asmr_cache_entry}，记录每个被缓存的远程文件（字幕、图片）的元数据。
 * 使用参数化 SQL（{@code ?} 占位符）+ 手写 {@link ResultSetHandler}，
 * 风格与 {@link MediaLibraryDao} 一致。
 *
 * @author Leck
 * @see CacheEntry
 */
public class CacheDao extends BaseDao {

    private static final Logger LOG = LoggerFactory.getLogger(CacheDao.class);

    /**
     * 单行映射：ResultSet → CacheEntry。
     */
    private static CacheEntry mapRow(ResultSet rs) throws SQLException {
        CacheEntry entry = new CacheEntry();
        entry.setCacheKey(rs.getString("cache_key"));
        entry.setSourcePath(rs.getString("source_path"));
        entry.setFileType(rs.getString("file_type"));
        entry.setLocalPath(rs.getString("local_path"));
        entry.setSize(rs.getLong("size"));
        entry.setLastAccessTime(rs.getLong("last_access_time"));
        entry.setCreateTime(rs.getLong("create_time"));
        return entry;
    }

    /** 多行映射 ResultSetHandler */
    private static final ResultSetHandler<List<CacheEntry>> LIST_HANDLER = rs -> {
        List<CacheEntry> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    };

    /** 单行映射 ResultSetHandler */
    private static final ResultSetHandler<CacheEntry> SINGLE_HANDLER = rs ->
            rs.next() ? mapRow(rs) : null;

    @Override
    public RowProcessor getRowProcessor() {
        return new BasicRowProcessor();
    }

    /**
     * 按缓存键查询缓存条目。
     *
     * @param cacheKey 缓存键
     * @return 缓存条目，不存在返回 null
     */
    public CacheEntry findByKey(String cacheKey) {
        String sql = "SELECT * FROM asmr_cache_entry WHERE cache_key = ?";
        try {
            QueryRunner qr = new QueryRunner();
            return qr.query(getConn(), sql, SINGLE_HANDLER, cacheKey);
        } catch (SQLException e) {
            throw new DataAccessException("按键查询缓存失败: " + cacheKey, e);
        } finally {
            close();
        }
    }

    /**
     * 按远程路径前缀查询缓存条目（用于目录重命名/删除/移动后清理子项缓存）。
     *
     * @param pathPrefix 远程目录完整路径
     * @return 匹配的缓存条目列表
     */
    public List<CacheEntry> findBySourcePathPrefix(String pathPrefix) {
        String sql = "SELECT * FROM asmr_cache_entry WHERE source_path = ? OR source_path LIKE ? ESCAPE '\\'";
        String escapedPrefix = pathPrefix.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        try {
            QueryRunner qr = new QueryRunner();
            return qr.query(getConn(), sql, LIST_HANDLER, pathPrefix, escapedPrefix + "/%");
        } catch (SQLException e) {
            throw new DataAccessException("按路径前缀查询缓存失败: " + pathPrefix, e);
        } finally {
            close();
        }
    }

    /**
     * 新增或替换缓存条目（INSERT OR REPLACE）。
     *
     * @param entry 缓存条目
     * @return 成功返回 true
     */
    public boolean save(CacheEntry entry) {
        String sql = "INSERT OR REPLACE INTO asmr_cache_entry " +
                "(cache_key, source_path, file_type, local_path, size, last_access_time, create_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try {
            QueryRunner qr = new QueryRunner();
            int update = qr.update(getConn(), sql,
                    entry.getCacheKey(),
                    entry.getSourcePath(),
                    entry.getFileType(),
                    entry.getLocalPath(),
                    entry.getSize(),
                    entry.getLastAccessTime(),
                    entry.getCreateTime());
            return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("保存缓存条目失败: " + entry.getCacheKey(), e);
        } finally {
            close();
        }
    }

    /**
     * 更新缓存条目的最后访问时间。
     *
     * @param cacheKey 缓存键
     * @param time     新的访问时间（毫秒时间戳）
     * @return 成功返回 true
     */
    public boolean updateAccessTime(String cacheKey, long time) {
        String sql = "UPDATE asmr_cache_entry SET last_access_time = ? WHERE cache_key = ?";
        try {
            QueryRunner qr = new QueryRunner();
            int update = qr.update(getConn(), sql, time, cacheKey);
            return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("更新缓存访问时间失败: " + cacheKey, e);
        } finally {
            close();
        }
    }

    /**
     * 按缓存键删除单条缓存记录（仅删 DB，不删文件）。
     *
     * @param cacheKey 缓存键
     * @return 成功返回 true
     */
    public boolean deleteByKey(String cacheKey) {
        String sql = "DELETE FROM asmr_cache_entry WHERE cache_key = ?";
        try {
            QueryRunner qr = new QueryRunner();
            int update = qr.update(getConn(), sql, cacheKey);
            return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("删除缓存记录失败: " + cacheKey, e);
        } finally {
            close();
        }
    }

    /**
     * 清空全部缓存记录（仅删 DB，不删文件）。
     *
     * @return 成功返回 true
     */
    public boolean deleteAll() {
        String sql = "DELETE FROM asmr_cache_entry";
        try {
            QueryRunner qr = new QueryRunner();
            qr.update(getConn(), sql);
            return true;
        } catch (SQLException e) {
            throw new DataAccessException("清空缓存记录失败", e);
        } finally {
            close();
        }
    }

    /**
     * 查询缓存总大小（字节）。
     *
     * @return 总大小，无缓存返回 0
     */
    public long getTotalSize() {
        String sql = "SELECT COALESCE(SUM(size), 0) FROM asmr_cache_entry";
        try {
            QueryRunner qr = new QueryRunner();
            return qr.query(getConn(), sql,
                    rs -> rs.next() ? rs.getLong(1) : 0L);
        } catch (SQLException e) {
            throw new DataAccessException("查询缓存总大小失败", e);
        } finally {
            close();
        }
    }

    /**
     * 查询缓存条目总数。
     *
     * @return 条目数，无缓存返回 0
     */
    public int getEntryCount() {
        String sql = "SELECT COUNT(*) FROM asmr_cache_entry";
        try {
            QueryRunner qr = new QueryRunner();
            Integer count = qr.query(getConn(), sql,
                    rs -> rs.next() ? rs.getInt(1) : 0);
            return count == null ? 0 : count;
        } catch (SQLException e) {
            throw new DataAccessException("查询缓存条目数失败", e);
        } finally {
            close();
        }
    }

    /**
     * 按最后访问时间升序取最早的 N 条缓存（用于 LRU 淘汰）。
     *
     * @param limit 最多返回的条目数
     * @return 按 last_access_time 升序排列的缓存条目列表
     */
    public List<CacheEntry> findLruEntries(int limit) {
        String sql = "SELECT * FROM asmr_cache_entry ORDER BY last_access_time ASC LIMIT ?";
        try {
            QueryRunner qr = new QueryRunner();
            return qr.query(getConn(), sql, LIST_HANDLER, limit);
        } catch (SQLException e) {
            throw new DataAccessException("查询 LRU 缓存条目失败", e);
        } finally {
            close();
        }
    }
}
