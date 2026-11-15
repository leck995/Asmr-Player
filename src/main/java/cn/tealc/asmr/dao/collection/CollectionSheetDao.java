package cn.tealc.asmr.dao.collection;

import cn.tealc.asmr.dao.BaseDao;
import cn.tealc.asmr.dao.DataAccessException;
import cn.tealc.asmr.model.AsmrWork;
import cn.tealc.asmr.model.collection.CollectionSheetSummary;
import cn.tealc.asmr.model.collection.SheetData;
import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.BeanProcessor;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.RowProcessor;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏歌单元数据访问，操作 {@code asmr_collect_sheet} 表（名称、描述、置顶标记与时间戳）。
 * <p>
 * 时间戳由 SQL 的 {@code datetime('now','localtime')} 生成为本地时间字符串。
 * 每个方法自取连接、自动提交；列表查询无结果时返回空列表。
 *
 * @author Leck
 */
public class CollectionSheetDao extends BaseDao {
    private static final Logger LOG = LoggerFactory.getLogger(CollectionSheetDao.class);

    /**
     * {@code asmr_collect_sheet} 列名到 {@link SheetData} 属性的映射。
     */
    @Override
    public RowProcessor getRowProcessor() {
        Map<String, String> columnToProperty = new HashMap<>();
        columnToProperty.put("id", "id");
        columnToProperty.put("sheet_name", "sheetName");
        columnToProperty.put("description", "description");
        columnToProperty.put("create_date_time", "createDateTime");
        columnToProperty.put("update_date_time", "updateDateTime");
        columnToProperty.put("top", "top");
        return new BasicRowProcessor(new BeanProcessor(columnToProperty));
    }

    /**
     * 创建描述为空串的歌单。
     * <p>
     * 表结构不限制重名，查重请用 {@link #isSheetNameExist}。
     */
    public boolean createSheet(String sheetName) {
        return createSheet(sheetName, "");
    }

    /**
     * 创建歌单，创建与更新时间取本地当前时间。
     *
     * @return 是否插入成功
     */
    public boolean createSheet(String sheetName, String description) {
        String sql = "INSERT INTO asmr_collect_sheet (sheet_name, description, create_date_time, update_date_time) " +
                "VALUES (?, ?, datetime('now','localtime'), datetime('now','localtime'))";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.update(getConn(), sql, sheetName, description) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("创建歌单失败: " + sheetName, e);
        } finally {
            close();
        }
    }

    /**
     * 删除歌单，其下条目由外键 ON DELETE CASCADE 自动清理
     * （依赖 {@link cn.tealc.asmr.dao.JdbcUtils#getConnection()} 开启的 foreign_keys 开关）。
     *
     * @return 是否删除了记录，ID 不存在时返回 false
     */
    public boolean deleteSheet(int sheetId) {
        String sql = "DELETE FROM asmr_collect_sheet WHERE id = ?";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.update(getConn(), sql, sheetId) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("删除歌单失败: " + sheetId, e);
        } finally {
            close();
        }
    }

    /**
     * 用 {@code sheetData} 整体覆盖歌单的名称、描述与置顶状态，并刷新更新时间。
     * <p>
     * {@code sheetData.id} 不参与 SQL，目标行由 {@code sheetId} 参数指定。
     *
     * @return 是否更新了记录
     */
    public boolean updateSheet(int sheetId, SheetData sheetData) {
        String sql = "UPDATE asmr_collect_sheet SET sheet_name = ?, description = ?, top = ?, " +
                "update_date_time = datetime('now','localtime') WHERE id = ?";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.update(getConn(), sql, sheetData.getSheetName(), sheetData.getDescription(),
                    sheetData.isTop(), sheetId) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("更新歌单失败: " + sheetId, e);
        } finally {
            close();
        }
    }


    /**
     * 只更新歌单名称与描述，不改动置顶状态。
     *
     * @return 是否更新了记录
     */
    public boolean updateSheetDetails(int sheetId, String newName, String newDescription) {
        String sql = "UPDATE asmr_collect_sheet SET sheet_name = ?, description = ?, " +
                "update_date_time = datetime('now','localtime') WHERE id = ?";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.update(getConn(), sql, newName, newDescription, sheetId) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("更新歌单失败: " + sheetId, e);
        } finally {
            close();
        }
    }

    /**
     * 设置/取消置顶。注意本方法不刷新 {@code update_date_time}，
     * 与其他更新方法不同。
     *
     * @return 是否更新了记录
     */
    public boolean setTop(int sheetId, boolean isTop) {
        String sql = "UPDATE asmr_collect_sheet SET top = ? WHERE id = ?";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.update(getConn(), sql, isTop ? 1 : 0, sheetId) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("设置歌单置顶状态失败: " + sheetId, e);
        } finally {
            close();
        }
    }

    /**
     * 查询单个歌单详情。
     *
     * @return 歌单数据，ID 不存在时返回 null
     */
    public SheetData getSheetById(int sheetId) {
        String sql = "SELECT * FROM asmr_collect_sheet WHERE id = ?";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.query(getConn(), sql, new BeanHandler<>(SheetData.class, getRowProcessor()), sheetId);
        } catch (SQLException e) {
            throw new DataAccessException("获取歌单详情失败: " + sheetId, e);
        } finally {
            close();
        }
    }

    /**
     * 查询全部歌单，置顶优先、其后按创建时间倒序。
     *
     * @return 歌单列表，无数据时返回空列表
     */
    public List<SheetData> getAllSheets() {
        String sql = "SELECT * FROM asmr_collect_sheet ORDER BY top DESC, create_date_time DESC";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.query(getConn(), sql, new BeanListHandler<>(SheetData.class, getRowProcessor()));
        } catch (SQLException e) {
            throw new DataAccessException("获取歌单列表失败", e);
        } finally {
            close();
        }
    }

    /**
     * 获取歌单主页所需的摘要信息：条目总数与最新一条收藏作品的定位字段。
     * <p>
     * 排序同 {@link #getAllSheets()}；空歌单的 {@code latestWork} 为 null。
     */
    public List<CollectionSheetSummary> getSheetSummaries() {
        String sql = "SELECT s.id, s.sheet_name, s.description, s.create_date_time, s.update_date_time, s.top, " +
                "(SELECT COUNT(*) FROM asmr_collect_item ci WHERE ci.sheet_id = s.id) AS item_count, " +
                "w.id AS latest_record_id, w.work_id AS latest_work_id, " +
                "w.filepath AS latest_filepath, w.storage_type AS latest_storage_type, w.storage_source_id AS latest_storage_source_id " +
                "FROM asmr_collect_sheet s " +
                "LEFT JOIN asmr_collect_item latest_item ON latest_item.id = " +
                "(SELECT MAX(ci_latest.id) FROM asmr_collect_item ci_latest WHERE ci_latest.sheet_id = s.id) " +
                "LEFT JOIN asmr_works w ON w.id = latest_item.work_id " +
                "ORDER BY s.top DESC, s.create_date_time DESC";
        try {
            QueryRunner runner = new QueryRunner();
            return runner.query(getConn(), sql, new SheetSummaryListHandler());
        } catch (SQLException e) {
            throw new DataAccessException("获取歌单摘要失败", e);
        } finally {
            close();
        }
    }

    /**
     * 手动行映射器：装配歌单与条目数，最新作品列全为空（空歌单）时不设置 latestWork。
     */
    private static class SheetSummaryListHandler implements org.apache.commons.dbutils.ResultSetHandler<List<CollectionSheetSummary>> {
        @Override
        public List<CollectionSheetSummary> handle(ResultSet rs) throws SQLException {
            List<CollectionSheetSummary> summaries = new ArrayList<>();
            while (rs.next()) {
                SheetData sheet = new SheetData();
                sheet.setId(rs.getInt("id"));
                sheet.setSheetName(rs.getString("sheet_name"));
                sheet.setDescription(rs.getString("description"));
                sheet.setCreateDateTime(rs.getString("create_date_time"));
                sheet.setUpdateDateTime(rs.getString("update_date_time"));
                sheet.setTop(rs.getBoolean("top"));

                CollectionSheetSummary summary = new CollectionSheetSummary();
                summary.setSheet(sheet);
                summary.setItemCount(rs.getInt("item_count"));

                int latestRecordId = rs.getInt("latest_record_id");
                if (!rs.wasNull()) {
                    AsmrWork latestWork = new AsmrWork();
                    latestWork.setId(latestRecordId);
                    latestWork.setWorkId(rs.getString("latest_work_id"));
                    latestWork.setFilepath(rs.getString("latest_filepath"));
                    Object storageType = rs.getObject("latest_storage_type");
                    latestWork.setStorageType(storageType == null ? null : ((Number) storageType).intValue());
                    latestWork.setStorageSourceId(rs.getInt("latest_storage_source_id"));
                    summary.setLatestWork(latestWork);
                }
                summaries.add(summary);
            }
            return summaries;
        }
    }

    /**
     * 检查是否已有同名歌单（表本身不阻止重名，需在创建前调用本方法把关）。
     */
    public boolean isSheetNameExist(String sheetName) {
        String sql = "SELECT COUNT(*) FROM asmr_collect_sheet WHERE sheet_name = ?";
        try {
            QueryRunner runner = new QueryRunner();
            Number count = runner.query(getConn(), sql, new ScalarHandler<>(), sheetName);
            return count != null && count.longValue() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("检查歌单名称是否存在失败: " + sheetName, e);
        } finally {
            close();
        }
    }
}
