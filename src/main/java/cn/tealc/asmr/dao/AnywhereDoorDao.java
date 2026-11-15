package cn.tealc.asmr.dao;

import cn.tealc.asmr.model.AsmrKeyType;
import cn.tealc.asmr.model.AnywhereDoor;
import org.apache.commons.dbutils.QueryRunner;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 快捷入口持久化，所有写入在事务内完成并返回提交后的有序列表。 */
public class AnywhereDoorDao {
    private final QueryRunner runner = new QueryRunner();

    public List<AnywhereDoor> getAll() {
        try (Connection connection = JdbcUtils.getConnection()) {
            return getAll(connection);
        } catch (SQLException e) {
            throw new DataAccessException("读取快捷入口失败", e);
        }
    }

    private List<AnywhereDoor> getAll(Connection connection) throws SQLException {
        return runner.query(connection, """
                SELECT shortcut.*, scope.media_library_id FROM asmr_anywhere_door shortcut
                LEFT JOIN asmr_anywhere_door_library scope ON scope.shortcut_id = shortcut.id
                ORDER BY shortcut.sort_order, shortcut.id
                """, result -> {
            List<AnywhereDoor> entries = new ArrayList<>();
            while (result.next()) {
                AnywhereDoor entry = new AnywhereDoor(result.getString("target_key"),
                        AsmrKeyType.valueOf(result.getString("type")), result.getString("alias"));
                entry.setId(result.getInt("id"));
                entry.setSortOrder(result.getInt("sort_order"));
                int libraryId = result.getInt("media_library_id");
                entry.setMediaLibraryId(result.wasNull() ? null : libraryId);
                entries.add(entry);
            }
            return entries;
        });
    }

    public List<AnywhereDoor> add(AnywhereDoor entry, String normalizedKey) {
        return write(connection -> {
            runner.update(connection, """
                INSERT INTO asmr_anywhere_door(type, target_key, normalized_key, alias, sort_order)
                VALUES (?, ?, ?, ?, (SELECT COALESCE(MAX(sort_order), -1) + 1 FROM asmr_anywhere_door))
                """, entry.getType().name(), entry.getKey(), normalizedKey, entry.getName());
            entry.setId(runner.query(connection, "SELECT last_insert_rowid()", result -> result.getInt(1)));
            saveLibrary(connection, entry);
        });
    }

    public List<AnywhereDoor> update(AnywhereDoor entry, String normalizedKey) {
        return write(connection -> {
            requireUpdated(runner.update(connection, """
                    UPDATE asmr_anywhere_door SET target_key = ?, normalized_key = ?, alias = ?, type = ?
                    WHERE id = ?
                    """, entry.getKey(), normalizedKey, entry.getName(), entry.getType().name(), entry.getId()));
            saveLibrary(connection, entry);
        });
    }

    private void saveLibrary(Connection connection, AnywhereDoor entry) throws SQLException {
        runner.update(connection, "DELETE FROM asmr_anywhere_door_library WHERE shortcut_id = ?", entry.getId());
        if (entry.getMediaLibraryId() != null) {
            runner.update(connection, "INSERT INTO asmr_anywhere_door_library(shortcut_id, media_library_id) VALUES (?, ?)",
                    entry.getId(), entry.getMediaLibraryId());
        }
    }

    public void deleteByLibrary(Connection connection, int libraryId) throws SQLException {
        runner.update(connection, """
                DELETE FROM asmr_anywhere_door WHERE id IN
                (SELECT shortcut_id FROM asmr_anywhere_door_library WHERE media_library_id = ?)
                """, libraryId);
    }

    public List<AnywhereDoor> delete(int id) {
        return write(connection -> requireUpdated(runner.update(connection,
                "DELETE FROM asmr_anywhere_door WHERE id = ?", id)));
    }

    public List<AnywhereDoor> move(int id, int direction) {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("无效的移动方向");
        return write(connection -> {
            List<AnywhereDoor> ordered = getAll(connection);
            int current = -1;
            for (int i = 0; i < ordered.size(); i++) {
                if (ordered.get(i).getId() == id) current = i;
            }
            if (current < 0) throw new IllegalArgumentException("该快捷入口已被移除");
            int target = current + direction;
            if (target < 0 || target >= ordered.size()) return;
            Collections.swap(ordered, current, target);
            for (int i = 0; i < ordered.size(); i++) {
                requireUpdated(runner.update(connection,
                        "UPDATE asmr_anywhere_door SET sort_order = ? WHERE id = ?", i, ordered.get(i).getId()));
            }
        });
    }

    private void requireUpdated(int count) {
        if (count != 1) throw new IllegalArgumentException("该快捷入口已被修改或移除，请重新打开编辑窗口");
    }

    private List<AnywhereDoor> write(DatabaseAction action) {
        try (Connection connection = JdbcUtils.getConnection()) {
            connection.setAutoCommit(false);
            try {
                action.run(connection);
                List<AnywhereDoor> entries = getAll(connection);
                connection.commit();
                return entries;
            } catch (SQLException | RuntimeException e) {
                try { connection.rollback(); } catch (SQLException rollbackError) { e.addSuppressed(rollbackError); }
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("保存快捷入口失败", e);
        }
    }

    @FunctionalInterface
    private interface DatabaseAction {
        void run(Connection connection) throws SQLException;
    }
}
