package cn.tealc.asmr.dao;

import cn.tealc.asmr.model.WorkBackgroundRecord;
import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.RowProcessor;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 作品背景记录数据访问层。
 * <p>
 * 每部作品至多一行，保存当前使用背景的来源与路径；{@code source_path} 为空表示
 * 已扫描过但没有可用背景。扫描时间 {@code scanned_at} 用于避免空结果反复扫描。
 *
 * @author Leck
 */
public class WorkBackgroundDao extends BaseDao {

    private static final ResultSetHandler<WorkBackgroundRecord> SINGLE_HANDLER = rs ->
            rs.next() ? mapRow(rs) : null;

    private static WorkBackgroundRecord mapRow(ResultSet rs) throws SQLException {
        return new WorkBackgroundRecord(
                rs.getInt("work_record_id"),
                rs.getInt("storage_type"),
                rs.getInt("storage_source_id"),
                rs.getString("source_path"),
                rs.getLong("scanned_at"));
    }

    @Override
    public RowProcessor getRowProcessor() {
        return new BasicRowProcessor();
    }

    /**
     * 按作品记录 ID 查询背景记录。
     *
     * @param workRecordId 作品记录 ID
     * @return 背景记录，不存在返回 null
     */
    public WorkBackgroundRecord findByWorkId(int workRecordId) {
        String sql = "SELECT work_record_id, storage_type, storage_source_id, source_path, scanned_at "
                + "FROM asmr_work_background WHERE work_record_id = ?";
        try {
            QueryRunner qr = new QueryRunner();
            return qr.query(getConn(), sql, SINGLE_HANDLER, workRecordId);
        } catch (SQLException e) {
            throw new DataAccessException("查询作品背景记录失败: " + workRecordId, e);
        } finally {
            close();
        }
    }

    /**
     * 新增或替换作品背景记录。
     *
     * @param record 背景记录
     * @return 写入成功返回 true
     */
    public boolean save(WorkBackgroundRecord record) {
        String sql = "INSERT OR REPLACE INTO asmr_work_background "
                + "(work_record_id, storage_type, storage_source_id, source_path, scanned_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        try {
            QueryRunner qr = new QueryRunner();
            int update = qr.update(getConn(), sql,
                    record.workRecordId(),
                    record.storageType(),
                    record.storageSourceId(),
                    record.sourcePath(),
                    record.scannedAt());
            return update > 0;
        } catch (SQLException e) {
            throw new DataAccessException("保存作品背景记录失败: " + record.workRecordId(), e);
        } finally {
            close();
        }
    }

    /**
     * 删除作品背景记录。
     *
     * @param workRecordId 作品记录 ID
     * @return 删除成功返回 true
     */
    public boolean deleteByWorkId(int workRecordId) {
        String sql = "DELETE FROM asmr_work_background WHERE work_record_id = ?";
        try {
            QueryRunner qr = new QueryRunner();
            return qr.update(getConn(), sql, workRecordId) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("删除作品背景记录失败: " + workRecordId, e);
        } finally {
            close();
        }
    }
}
