package cn.tealc.asmr.dao;

import cn.tealc.asmr.model.StorageSource;
import cn.tealc.asmr.model.StorageSourceStatus;
import cn.tealc.asmr.source.StorageType;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.RowProcessor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * 存储源数据访问层。
 */
public class StorageSourceDao extends BaseDao {
    private static StorageSource mapRow(ResultSet rs) throws SQLException {
        StorageSource source = new StorageSource();
        source.setId(rs.getInt("id"));
        source.setName(rs.getString("name"));
        source.setType(StorageType.fromCode(rs.getInt("type")));
        source.setTargetPath(rs.getString("target_path"));
        source.setConnectionConfig(rs.getString("connection_config"));
        int credentialId = rs.getInt("credential_id");
        source.setCredentialId(rs.wasNull() ? null : credentialId);
        String status = rs.getString("status");
        source.setStatus(status == null ? StorageSourceStatus.ACTIVE : StorageSourceStatus.valueOf(status));
        source.setLastError(rs.getString("last_error"));
        return source;
    }

    private static final ResultSetHandler<List<StorageSource>> LIST_HANDLER = rs -> {
        List<StorageSource> result = new ArrayList<>();
        while (rs.next()) result.add(mapRow(rs));
        return result;
    };

    @Override
    public RowProcessor getRowProcessor() {
        return new org.apache.commons.dbutils.BasicRowProcessor();
    }

    public List<StorageSource> getAll() {
        try {
            return new QueryRunner().query(getConn(), "SELECT * FROM asmr_storage_source ORDER BY id", LIST_HANDLER);
        } catch (SQLException e) {
            throw new DataAccessException("查询存储源失败", e);
        } finally {
            close();
        }
    }

    public StorageSource getById(int id) {
        try {
            return new QueryRunner().query(getConn(), "SELECT * FROM asmr_storage_source WHERE id = ?",
                    rs -> rs.next() ? mapRow(rs) : null, id);
        } catch (SQLException e) {
            throw new DataAccessException("读取存储源失败", e);
        } finally {
            close();
        }
    }

    public boolean add(StorageSource source) {
        String sql = "INSERT INTO asmr_storage_source (name, type, target_path, connection_config, credential_id, status, last_error) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try {
            QueryRunner qr = new QueryRunner();
            int updated = qr.update(getConn(), sql, source.getName(), source.getType().getCode(), source.getTargetPath(),
                    source.getConnectionConfig(), source.getCredentialId(), source.getStatus().name(), source.getLastError());
            if (updated <= 0) return false;
            source.setId(qr.query(getConn(), "SELECT last_insert_rowid()", rs -> rs.next() ? rs.getInt(1) : null));
            return true;
        } catch (SQLException e) {
            throw new DataAccessException("新增存储源失败", e);
        } finally {
            close();
        }
    }

    public boolean update(StorageSource source) {
        String sql = "UPDATE asmr_storage_source SET name = ?, type = ?, target_path = ?, connection_config = ?, credential_id = ?, status = ?, last_error = ? WHERE id = ?";
        try {
            return new QueryRunner().update(getConn(), sql, source.getName(), source.getType().getCode(), source.getTargetPath(),
                    source.getConnectionConfig(), source.getCredentialId(), source.getStatus().name(), source.getLastError(), source.getId()) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("更新存储源失败", e);
        } finally {
            close();
        }
    }

    public boolean updateStatus(int id, StorageSourceStatus status, String lastError) {
        try {
            return new QueryRunner().update(getConn(),
                    "UPDATE asmr_storage_source SET status = ?, last_error = ? WHERE id = ?", status.name(), lastError, id) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("更新存储源状态失败", e);
        } finally {
            close();
        }
    }

    public boolean deleteIfUnused(int id) {
        try (Connection connection = JdbcUtils.getConnection()) {
            connection.setAutoCommit(false);
            try {
                QueryRunner runner = new QueryRunner();
                Integer credentialId = runner.query(connection,
                        "SELECT credential_id FROM asmr_storage_source WHERE id = ?",
                        rs -> {
                            if (!rs.next()) return null;
                            Number value = (Number) rs.getObject(1);
                            return value == null ? null : value.intValue();
                        }, id);
                int removed = runner.update(connection, """
                        DELETE FROM asmr_storage_source WHERE id = ?
                        AND NOT EXISTS (SELECT 1 FROM asmr_media_library WHERE storage_source_id = ?)
                        AND NOT EXISTS (SELECT 1 FROM asmr_works WHERE storage_source_id = ?)
                        """, id, id, id);
                if (removed == 0) {
                    connection.rollback();
                    return false;
                }
                if (credentialId != null) {
                    runner.update(connection, """
                            DELETE FROM asmr_storage_credential WHERE id = ?
                            AND NOT EXISTS (SELECT 1 FROM asmr_storage_source WHERE credential_id = ?)
                            """, credentialId, credentialId);
                }
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("移除存储源失败", e);
        }
    }
}
