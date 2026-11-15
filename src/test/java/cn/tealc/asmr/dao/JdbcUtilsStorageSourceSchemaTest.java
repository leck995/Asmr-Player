package cn.tealc.asmr.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JdbcUtilsStorageSourceSchemaTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsNewSchemaAndUsesSourceScopedUniqueness() throws Exception {
        withDatabase("new.db", url -> {
            JdbcUtils.init();
            JdbcUtils.init();
            try (Connection connection = JdbcUtils.getConnection()) {
                try (Statement statement = connection.createStatement();
                     var result = statement.executeQuery("PRAGMA user_version")) {
                    assertEquals(5, result.getInt(1));
                }
                try (Statement statement = connection.createStatement();
                     var result = statement.executeQuery("PRAGMA table_info(asmr_works)")) {
                    Set<String> columns = new HashSet<>();
                    while (result.next()) {
                        columns.add(result.getString("name"));
                    }
                    assertTrue(columns.containsAll(Set.of("work_id", "related_work_ids", "origin_work_id")));
                    assertFalse(columns.contains("rj_id"));
                    assertFalse(columns.contains("related_rj"));
                    assertFalse(columns.contains("origin_rj"));
                }
                insertWork(connection, "RJ100", "/shared", 1);
                insertWork(connection, "RJ100", "/shared", 2);
                insertWork(connection, null, "/without-rj", 1);
                insertWork(connection, null, "/without-rj-second", 1);
                assertThrows(SQLException.class, () -> insertWork(connection, "RJ100", "/another", 1));
                assertThrows(SQLException.class, () -> insertWork(connection, null, "/without-rj", 1));
                assertThrows(SQLException.class, () -> insertWork(connection, null, "/missing-source", null));
                try (Statement statement = connection.createStatement();
                     var result = statement.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE name='asmr_openlist_account'")) {
                    assertEquals(0, result.getInt(1));
                }
                try (Statement statement = connection.createStatement();
                     var result = statement.executeQuery("PRAGMA table_info(asmr_media_library)")) {
                    boolean hasLastImportedAt = false;
                    boolean hasLastScrapedAt = false;
                    while (result.next()) {
                        hasLastImportedAt |= "last_imported_at".equals(result.getString("name"));
                        hasLastScrapedAt |= "last_scraped_at".equals(result.getString("name"));
                    }
                    assertTrue(hasLastImportedAt);
                    assertTrue(hasLastScrapedAt);
                }
            }
        });
    }

    @Test
    void createsShortcutTableWithRequiredFieldsAndConstraints() throws Exception {
        withDatabase("shortcuts.db", url -> {
            JdbcUtils.init();
            try (Connection connection = JdbcUtils.getConnection(); Statement statement = connection.createStatement()) {
                try (var result = statement.executeQuery("PRAGMA table_info(asmr_anywhere_door)")) {
                    Set<String> columns = new HashSet<>();
                    while (result.next()) columns.add(result.getString("name"));
                    assertEquals(Set.of("id", "type", "target_key", "normalized_key", "alias", "sort_order"), columns);
                }
                statement.execute("INSERT INTO asmr_anywhere_door(type,target_key,normalized_key,sort_order) VALUES ('TAG','目标','目标',0)");
                assertThrows(SQLException.class, () -> statement.execute("INSERT INTO asmr_anywhere_door(type,target_key,normalized_key,sort_order) VALUES ('TAG','目标','目标',1)"));
                statement.execute("INSERT INTO asmr_anywhere_door(type,target_key,normalized_key,sort_order) VALUES ('CATEGORY','目标','目标',1)");
                assertThrows(SQLException.class, () -> statement.execute("INSERT INTO asmr_anywhere_door(type,target_key,normalized_key,sort_order) VALUES ('INVALID','目标','目标',2)"));
                assertThrows(SQLException.class, () -> statement.execute("INSERT INTO asmr_anywhere_door(type,target_key,normalized_key,sort_order) VALUES ('TAG',' ',' ',2)"));
                assertThrows(SQLException.class, () -> statement.execute("INSERT INTO asmr_anywhere_door(type,target_key,normalized_key,sort_order) VALUES ('TAG','其他','其他',-1)"));
            }
        });
    }

    @Test
    void addsShortcutTableWithoutChangingExistingDataInCurrentVersion() throws Exception {
        withDatabase("version-three.db", url -> {
            try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE marker (value TEXT)");
                statement.execute("INSERT INTO marker VALUES ('保留')");
                statement.execute("PRAGMA user_version = 3");
            }
            JdbcUtils.init();
            try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
                try (var result = statement.executeQuery("PRAGMA user_version")) { assertEquals(5, result.getInt(1)); }
                try (var result = statement.executeQuery("SELECT value FROM marker")) { assertEquals("保留", result.getString(1)); }
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE name='asmr_anywhere_door'")) { assertEquals(1, result.getInt(1)); }
            }
        });
    }

    @Test
    void rejectsVersionTwoWithoutChangingIt() throws Exception {
        withDatabase("version-two.db", url -> {
            try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE asmr_works (rj_id TEXT)");
                statement.execute("INSERT INTO asmr_works VALUES ('RJ100')");
                statement.execute("PRAGMA user_version = 2");
            }
            assertThrows(RuntimeException.class, JdbcUtils::init);
            try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
                try (var result = statement.executeQuery("PRAGMA user_version")) {
                    assertEquals(2, result.getInt(1));
                }
                try (var result = statement.executeQuery("SELECT rj_id FROM asmr_works")) {
                    assertEquals("RJ100", result.getString(1));
                }
            }
        });
    }

    @Test
    void rejectsExistingDatabaseWithoutChangingIt() throws Exception {
        withDatabase("old.db", url -> {
            try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE asmr_works (title TEXT)");
                statement.execute("INSERT INTO asmr_works VALUES ('保留')");
            }
            RuntimeException error = assertThrows(RuntimeException.class, JdbcUtils::init);
            assertTrue(error.getCause().getMessage().contains("现有数据库未被修改"));
            try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT title FROM asmr_works")) {
                assertEquals("保留", result.getString(1));
            }
            try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table'")) {
                assertEquals(1, result.getInt(1));
            }
        });
    }

    private void withDatabase(String name, DatabaseAction action) throws Exception {
        Field field = JdbcUtils.class.getDeclaredField("url");
        field.setAccessible(true);
        String originalUrl = (String) field.get(null);
        String url = "jdbc:sqlite:" + temporaryDirectory.resolve(name);
        field.set(null, url);
        try {
            action.run(url);
        } finally {
            field.set(null, originalUrl);
        }
    }

    @Test
    void addsAlbumsToVersionThreeAndPreservesWorksAndPlaylists() throws Exception {
        withDatabase("albums-upgrade.db", url -> {
            JdbcUtils.init();
            try (Connection connection = JdbcUtils.getConnection(); Statement statement = connection.createStatement()) {
                insertWork(connection, "BJ123", "/work", 1);
                statement.execute("INSERT INTO asmr_collect_sheet(id,sheet_name,create_date_time) VALUES (1,'保留歌单','2026-01-01')");
                statement.execute("INSERT INTO asmr_collect_item(item_name,filepath,work_id,sheet_id) VALUES ('音频','/work/1.mp3',1,1)");
                statement.execute("DROP TABLE asmr_work_album_item");
                statement.execute("DROP TABLE asmr_work_album");
                statement.execute("PRAGMA user_version = 3");
            }
            JdbcUtils.init();
            JdbcUtils.init();
            try (Connection connection = JdbcUtils.getConnection(); Statement statement = connection.createStatement()) {
                try (var result = statement.executeQuery("PRAGMA user_version")) { assertEquals(5, result.getInt(1)); }
                try (var result = statement.executeQuery("SELECT work_id FROM asmr_works")) { assertEquals("BJ123", result.getString(1)); }
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM asmr_collect_item")) { assertEquals(1, result.getInt(1)); }
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM asmr_work_album")) { assertEquals(0, result.getInt(1)); }
                statement.execute("INSERT INTO asmr_work_album(name,created_at) VALUES ('集册','2026-01-01T00:00:00')");
                statement.execute("INSERT INTO asmr_work_album_item VALUES (1,1,'2026-01-01T00:00:00')");
                assertThrows(SQLException.class, () -> statement.execute("INSERT INTO asmr_work_album_item VALUES (1,1,'2026-01-01T00:00:00')"));
                assertThrows(SQLException.class, () -> statement.execute("INSERT INTO asmr_work_album_item VALUES (1,999,'2026-01-01T00:00:00')"));
                assertThrows(SQLException.class, () -> statement.execute("INSERT INTO asmr_work_album(name,created_at) VALUES (' ','2026-01-01')"));
            }
        });
    }

    @Test
    void upgradesVersionFourWithMetadataColumnsAndBackfillsTimes() throws Exception {
        withDatabase("metadata-upgrade.db", url -> {
            try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE asmr_collect_sheet (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "sheet_name TEXT NOT NULL, create_date_time TEXT NOT NULL, top INTEGER NOT NULL DEFAULT 1)");
                statement.execute("INSERT INTO asmr_collect_sheet(sheet_name,create_date_time,top) " +
                        "VALUES ('旧歌单','2025-01-02 03:04:05',1)");
                statement.execute("CREATE TABLE asmr_tags (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "name VARCHAR(20) NOT NULL UNIQUE)");
                statement.execute("INSERT INTO asmr_tags(name) VALUES ('旧标签')");
                statement.execute("PRAGMA user_version = 4");
            }
            JdbcUtils.init();
            try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
                try (var result = statement.executeQuery("PRAGMA user_version")) { assertEquals(5, result.getInt(1)); }
                assertTrue(columns(statement, "asmr_collect_sheet").containsAll(Set.of("description", "update_date_time")));
                assertTrue(columns(statement, "asmr_tags").containsAll(Set.of("description", "create_date_time", "update_date_time")));

                try (var result = statement.executeQuery(
                        "SELECT sheet_name, description, create_date_time, update_date_time FROM asmr_collect_sheet")) {
                    assertTrue(result.next());
                    assertEquals("旧歌单", result.getString("sheet_name"));
                    assertEquals("", result.getString("description"));
                    assertEquals("2025-01-02 03:04:05", result.getString("create_date_time"));
                    assertEquals("2025-01-02 03:04:05", result.getString("update_date_time"));
                }
                try (var result = statement.executeQuery(
                        "SELECT name, description, create_date_time, update_date_time FROM asmr_tags")) {
                    assertTrue(result.next());
                    assertEquals("旧标签", result.getString("name"));
                    assertEquals("", result.getString("description"));
                    assertFalse(result.getString("create_date_time").isBlank());
                    assertEquals(result.getString("create_date_time"), result.getString("update_date_time"));
                }
            }
        });
    }

    private static Set<String> columns(Statement statement, String table) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (var result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                columns.add(result.getString("name"));
            }
        }
        return columns;
    }

    private void insertWork(Connection connection, String workId, String path, Integer sourceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO asmr_works (work_id, title, filepath, storage_source_id) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, workId);
            statement.setString(2, path);
            statement.setString(3, path);
            statement.setObject(4, sourceId);
            statement.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface DatabaseAction {
        void run(String url) throws Exception;
    }
}
