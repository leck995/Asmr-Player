package cn.tealc.asmr.dao;

import org.apache.commons.dbutils.QueryRunner;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 字幕文本黑名单持久化。
 * <p>
 * 每条黑名单一行记录，与字幕整行文本精确匹配时该字幕不显示。
 * 之前保存在 {@code resource/data/storage-source/blacklist_text} 文件中，
 * 现改为存入数据库，便于与其他设置统一管理与备份。
 *
 * @author Leck
 */
public class BlacklistTextDao {
    private final QueryRunner runner = new QueryRunner();

    /**
     * 读取全部黑名单条目，按主键顺序返回。
     */
    public List<String> getAll() {
        try (Connection connection = JdbcUtils.getConnection()) {
            return runner.query(connection,
                    "SELECT row FROM asmr_blacklist_text ORDER BY id",
                    result -> {
                        List<String> rows = new java.util.ArrayList<>();
                        while (result.next()) {
                            rows.add(result.getString("row"));
                        }
                        return rows;
                    });
        } catch (SQLException e) {
            throw new DataAccessException("读取字幕黑名单失败", e);
        }
    }

    /**
     * 用给定条目整体替换黑名单，空列表表示清空。
     * <p>
     * 在单个事务中先清空再写入，避免中途失败留下不完整的数据。
     *
     * @param rows 黑名单文本，重复项会被忽略
     */
    public void replaceAll(List<String> rows) {
        try (Connection connection = JdbcUtils.getConnection()) {
            connection.setAutoCommit(false);
            try {
                runner.update(connection, "DELETE FROM asmr_blacklist_text");
                if (rows != null && !rows.isEmpty()) {
                    Object[][] params = rows.stream()
                            .filter(row -> row != null && !row.isBlank())
                            .distinct()
                            .map(row -> new Object[]{row})
                            .toArray(Object[][]::new);
                    if (params.length > 0) {
                        runner.batch(connection, "INSERT OR IGNORE INTO asmr_blacklist_text(row) VALUES (?)", params);
                    }
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("保存字幕黑名单失败", e);
        }
    }
}
