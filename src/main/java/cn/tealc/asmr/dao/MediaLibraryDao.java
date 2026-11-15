package cn.tealc.asmr.dao;

import cn.tealc.asmr.model.MediaLibrary;
import cn.tealc.asmr.source.StorageType;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 媒体库数据访问层。
 * <p>
 * 操作表 {@code asmr_media_library}。由于 {@code storage_type} 列为 INTEGER，
 * 而 {@link MediaLibrary#getStorageType()} 返回 {@link StorageType} 枚举，
 * 查询时用手写 {@link ResultSetHandler} 手动转换。
 *
 * @author Leck
 */
public class MediaLibraryDao extends BaseDao {
    private static final Logger LOG = LoggerFactory.getLogger(MediaLibraryDao.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 单行映射：ResultSet → MediaLibrary，处理 Integer→StorageType 枚举转换。
     */
    private static MediaLibrary mapRow(ResultSet rs) throws SQLException {
        MediaLibrary ml = new MediaLibrary();
        ml.setId(rs.getInt("id"));
        ml.setName(rs.getString("name"));
        ml.setPath(rs.getString("path"));
        ml.setLevel(rs.getInt("level"));
        ml.setStorageType(StorageType.fromCode(rs.getInt("storage_type")));
        int storageSourceId = rs.getInt("storage_source_id");
        ml.setStorageSourceId(rs.wasNull() ? null : storageSourceId);
        ml.setLastImportedAt(readTime(rs, "last_imported_at"));
        ml.setLastScrapedAt(readTime(rs, "last_scraped_at"));
        return ml;
    }

    private static LocalDateTime readTime(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new SQLException("媒体库时间字段格式无效: " + column, e);
        }
    }

    /**
     * 多行映射 ResultSetHandler。
     */
    private static final ResultSetHandler<List<MediaLibrary>> LIST_HANDLER = rs -> {
        List<MediaLibrary> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    };

    /**
     * 单行映射 ResultSetHandler。
     */
    private static final ResultSetHandler<MediaLibrary> SINGLE_HANDLER = rs ->
            rs.next() ? mapRow(rs) : null;

    @Override
    public org.apache.commons.dbutils.RowProcessor getRowProcessor() {
        // 不再使用 BeanProcessor，返回默认实现即可
        return new org.apache.commons.dbutils.BasicRowProcessor();
    }

    /**
     * 查询全部媒体库。
     */
    public List<MediaLibrary> getAll() {
        String sql = "SELECT * FROM asmr_media_library ORDER BY id";
        try {
            QueryRunner qr = new QueryRunner();
            return qr.query(getConn(), sql, LIST_HANDLER);
        } catch (SQLException e) {
            throw new DataAccessException("查询全部媒体库失败", e);
        } finally {
            close();
        }
    }

    /**
     * 按 ID 查询单个媒体库。
     */
    public MediaLibrary getById(int id) {
        String sql = "SELECT * FROM asmr_media_library WHERE id = ?";
        try {
            QueryRunner qr = new QueryRunner();
            return qr.query(getConn(), sql, SINGLE_HANDLER, id);
        } catch (SQLException e) {
            throw new DataAccessException("按 ID 查询媒体库失败: " + id, e);
        } finally {
            close();
        }
    }

    /**
     * 按存储类型查询媒体库。
     */
    public List<MediaLibrary> getByStorageType(StorageType type) {
        String sql = "SELECT * FROM asmr_media_library WHERE storage_type = ? ORDER BY id";
        try {
            QueryRunner qr = new QueryRunner();
            return qr.query(getConn(), sql, LIST_HANDLER, type.getCode());
        } catch (SQLException e) {
            throw new DataAccessException("按存储类型查询媒体库失败: " + type, e);
        } finally {
            close();
        }
    }

    /**
     * 查询指定路径的媒体库是否存在。
     */
    public MediaLibrary getByPath(String path) {
        String sql = "SELECT * FROM asmr_media_library WHERE path = ?";
        try {
            QueryRunner qr = new QueryRunner();
            return qr.query(getConn(), sql, SINGLE_HANDLER, path);
        } catch (SQLException e) {
            throw new DataAccessException("按路径查询媒体库失败: " + path, e);
        } finally {
            close();
        }
    }

    /**
     * 新增媒体库，新增成功后将自增 ID 回写到对象上。
     */
    public boolean add(MediaLibrary library) {
        String sql = "INSERT INTO asmr_media_library (name, path, level, storage_type, storage_source_id) " +
                "VALUES (?, ?, ?, ?, ?)";
        try {
            QueryRunner qr = new QueryRunner();
            int update = qr.update(getConn(), sql,
                    library.getName(),
                    library.getPath(),
                    library.getLevel(),
                    library.getStorageType() != null ? library.getStorageType().getCode() : StorageType.LOCAL.getCode(),
                    library.getStorageSourceId());
            if (update > 0) {
                Integer generatedId = qr.query(getConn(), "SELECT last_insert_rowid()",
                        rs -> rs.next() ? rs.getInt(1) : null);
                library.setId(generatedId);
                return true;
            }
            return false;
        } catch (SQLException e) {
            throw new DataAccessException("新增媒体库失败", e);
        } finally {
            close();
        }
    }

    /**
     * 按 ID 更新媒体库。
     */
    public boolean update(MediaLibrary library) {
        String sql = "UPDATE asmr_media_library SET name = ?, path = ?, level = ?, " +
                "storage_type = ?, storage_source_id = ? WHERE id = ?";
        try {
            QueryRunner qr = new QueryRunner();
            int update = qr.update(getConn(), sql,
                    library.getName(),
                    library.getPath(),
                    library.getLevel(),
                    library.getStorageType() != null ? library.getStorageType().getCode() : StorageType.LOCAL.getCode(),
                    library.getStorageSourceId(),
                    library.getId());
            return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("更新媒体库失败: " + library.getId(), e);
        } finally {
            close();
        }
    }

    /** 更新媒体库最近完成导入的时间。 */
    public boolean updateLastImportedAt(int id, LocalDateTime time) {
        return updateActivityTime(id, "last_imported_at", time);
    }

    /** 更新媒体库最近完成刮削的时间。 */
    public boolean updateLastScrapedAt(int id, LocalDateTime time) {
        return updateActivityTime(id, "last_scraped_at", time);
    }

    private boolean updateActivityTime(int id, String column, LocalDateTime time) {
        String sql = "UPDATE asmr_media_library SET " + column + " = ? WHERE id = ?";
        try {
            QueryRunner qr = new QueryRunner();
            return qr.update(getConn(), sql, time == null ? null : TIME_FORMATTER.format(time), id) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("更新媒体库活动时间失败: " + id, e);
        } finally {
            close();
        }
    }

    /**
     * 按 ID 删除媒体库。
     */
    public boolean deleteById(int id) {
        String sql = "DELETE FROM asmr_media_library WHERE id = ?";
        try {
            QueryRunner qr = new QueryRunner();
            int update = qr.update(getConn(), sql, id);
            return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("删除媒体库失败: " + id, e);
        } finally {
            close();
        }
    }

    /**
     * 按 ID 删除媒体库（事务场景，使用外部传入的连接，不自动关闭连接）。
     *
     * @param conn 外部事务连接，由调用方负责提交/回滚/关闭
     * @param id   媒体库 ID
     * @return 删除成功返回 true
     * @throws SQLException SQL 执行异常，由调用方在事务中处理
     */
    public boolean deleteById(Connection conn, int id) throws SQLException {
        String sql = "delete from asmr_media_library where id = ?";
        QueryRunner qr = new QueryRunner();
        return qr.update(conn, sql, id) > 0;
    }


}
