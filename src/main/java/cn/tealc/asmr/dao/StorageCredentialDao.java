package cn.tealc.asmr.dao;

import cn.tealc.asmr.model.StorageCredential;
import cn.tealc.asmr.utils.CredentialCipher;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.RowProcessor;
import org.apache.commons.dbutils.handlers.BeanHandler;

import java.sql.SQLException;

/**
 * 存储源凭据数据访问层。
 */
public class StorageCredentialDao extends BaseDao {
    @Override
    public RowProcessor getRowProcessor() {
        return new org.apache.commons.dbutils.BasicRowProcessor();
    }

    public StorageCredential getById(int id) {
        String sql = "SELECT id, auth_type, secret_value FROM asmr_storage_credential WHERE id = ?";
        try {
            StorageCredential credential = new QueryRunner().query(getConn(), sql, rs -> {
                if (!rs.next()) return null;
                StorageCredential item = new StorageCredential();
                item.setId(rs.getInt("id"));
                item.setAuthType(rs.getString("auth_type"));
                item.setSecret(CredentialCipher.decrypt(rs.getString("secret_value")));
                return item;
            }, id);
            return credential;
        } catch (SQLException e) {
            throw new DataAccessException("读取存储源凭据失败", e);
        } finally {
            close();
        }
    }

    public boolean add(StorageCredential credential) {
        String sql = "INSERT INTO asmr_storage_credential (auth_type, secret_value) VALUES (?, ?)";
        try {
            QueryRunner qr = new QueryRunner();
            int updated = qr.update(getConn(), sql, credential.getAuthType(), CredentialCipher.encrypt(credential.getSecret()));
            if (updated <= 0) return false;
            Integer id = qr.query(getConn(), "SELECT last_insert_rowid()", rs -> rs.next() ? rs.getInt(1) : null);
            credential.setId(id);
            return true;
        } catch (SQLException e) {
            throw new DataAccessException("保存存储源凭据失败", e);
        } finally {
            close();
        }
    }

    public boolean update(StorageCredential credential) {
        String sql = "UPDATE asmr_storage_credential SET auth_type = ?, secret_value = ? WHERE id = ?";
        try {
            return new QueryRunner().update(getConn(), sql, credential.getAuthType(),
                    CredentialCipher.encrypt(credential.getSecret()), credential.getId()) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("更新存储源凭据失败", e);
        } finally {
            close();
        }
    }
}
