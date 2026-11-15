package cn.tealc.asmr.dao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class StorageSourceDaoTest {
    @TempDir Path temporaryDirectory;
    private Field urlField;
    private String originalUrl;

    @BeforeEach
    void setUp() throws Exception {
        urlField = JdbcUtils.class.getDeclaredField("url");
        urlField.setAccessible(true);
        originalUrl = (String) urlField.get(null);
        urlField.set(null, "jdbc:sqlite:" + temporaryDirectory.resolve("test.db"));
        JdbcUtils.init();
    }

    @AfterEach
    void restoreUrl() throws Exception {
        urlField.set(null, originalUrl);
    }

    @Test
    void removesUnusedSourceAndItsCredential() throws SQLException {
        execute("INSERT INTO asmr_storage_credential (id, auth_type, secret_value) VALUES (1, 'TOKEN', 'secret')");
        execute("INSERT INTO asmr_storage_source (id, name, type, target_path, credential_id) VALUES (1, '远程', 1, '/', 1)");

        assertTrue(new StorageSourceDao().deleteIfUnused(1));
        assertEquals(0, count("asmr_storage_source"));
        assertEquals(0, count("asmr_storage_credential"));
        assertFalse(new StorageSourceDao().deleteIfUnused(1));
    }

    @Test
    void keepsSharedCredential() throws SQLException {
        execute("INSERT INTO asmr_storage_credential (id, auth_type, secret_value) VALUES (1, 'TOKEN', 'secret')");
        execute("INSERT INTO asmr_storage_source (id, name, type, target_path, credential_id) VALUES (1, '源一', 1, '/', 1), (2, '源二', 1, '/', 1)");

        assertTrue(new StorageSourceDao().deleteIfUnused(1));
        assertEquals(1, count("asmr_storage_source"));
        assertEquals(1, count("asmr_storage_credential"));
    }

    @Test
    void rejectsSourceUsedByLibrary() throws SQLException {
        execute("INSERT INTO asmr_storage_source (id, name, type, target_path) VALUES (1, '本机', 0, '/audio')");
        execute("INSERT INTO asmr_media_library (name, path, storage_source_id) VALUES ('媒体库', '/audio', 1)");

        assertFalse(new StorageSourceDao().deleteIfUnused(1));
        assertEquals(1, count("asmr_storage_source"));
        assertEquals(1, count("asmr_media_library"));
    }

    @Test
    void rejectsSourceUsedByWorkWithoutLibrary() throws SQLException {
        execute("INSERT INTO asmr_storage_source (id, name, type, target_path) VALUES (1, '本机', 0, '/audio')");
        execute("INSERT INTO asmr_works (title, filepath, storage_source_id) VALUES ('作品', '/audio/work', 1)");

        assertFalse(new StorageSourceDao().deleteIfUnused(1));
        assertEquals(1, count("asmr_storage_source"));
        assertEquals(1, count("asmr_works"));
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = JdbcUtils.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int count(String table) throws SQLException {
        try (Connection connection = JdbcUtils.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return result.getInt(1);
        }
    }
}
